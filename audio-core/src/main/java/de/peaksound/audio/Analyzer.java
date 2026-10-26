package de.peaksound.audio;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Berechnet die {@link Analysis} aus PCM: drei Frequenzbaender (Pegel 0-15, 20 Werte pro Sekunde) und
 * Taktschlaege (Onset-Erkennung auf dem Bassband). Streamend und mit konstantem Speicher, damit lange Songs
 * und Mehrkanal den Server nicht belasten; laeuft immer asynchron.
 */
public final class Analyzer {
    public static final int HOP_MS = 50;
    private static final double BASS_CUTOFF_HZ = 200;
    private static final double HIGH_CUTOFF_HZ = 2500;
    /** Kuerzester erlaubter Abstand zweier Taktschlaege (ca. 210 BPM). */
    private static final int MIN_BEAT_GAP_MS = 280;

    private Analyzer() {}

    /** Zweipol-Filter (RBJ-Cookbook, Butterworth-Guete). */
    private static final class Biquad {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        private Biquad(double b0, double b1, double b2, double a0, double a1, double a2) {
            this.b0 = b0 / a0; this.b1 = b1 / a0; this.b2 = b2 / a0; this.a1 = a1 / a0; this.a2 = a2 / a0;
        }

        static Biquad lowPass(double fs, double fc) {
            double w = 2 * Math.PI * Math.min(fc, fs * 0.45) / fs, cos = Math.cos(w), alpha = Math.sin(w) / (2 * 0.7071);
            return new Biquad((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad highPass(double fs, double fc) {
            double w = 2 * Math.PI * Math.min(fc, fs * 0.45) / fs, cos = Math.cos(w), alpha = Math.sin(w) / (2 * 0.7071);
            return new Biquad((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }

    /** Zwei Biquads hintereinander: Filter vierter Ordnung (24 dB pro Oktave) trennt die Baender sauber. */
    private record Cascade(Biquad first, Biquad second) {
        static Cascade lowPass(double fs, double fc) { return new Cascade(Biquad.lowPass(fs, fc), Biquad.lowPass(fs, fc)); }

        static Cascade highPass(double fs, double fc) { return new Cascade(Biquad.highPass(fs, fc), Biquad.highPass(fs, fc)); }

        double process(double x) { return second.process(first.process(x)); }
    }

    /** Ein Band, das weniger als dieser Anteil des lautesten Bands ist, gilt als leer (Filter-Restsignal). */
    private static final double NOISE_FLOOR = 0.05;

    public static Analysis analyze(PcmSource source) throws AudioException {
        int rate = source.sampleRate();
        int channels = source.channels();
        int windowFrames = Math.max(1, rate * HOP_MS / 1000);
        Cascade bassLp = Cascade.lowPass(rate, BASS_CUTOFF_HZ);
        Cascade midHp = Cascade.highPass(rate, BASS_CUTOFF_HZ);
        Cascade midLp = Cascade.lowPass(rate, HIGH_CUTOFF_HZ);
        Cascade highHp = Cascade.highPass(rate, HIGH_CUTOFF_HZ);

        List<double[]> rms = new ArrayList<>(); // je Fenster: {bass, mid, high}
        double[] energy = new double[3];
        int inWindow = 0;
        short[] buf = new short[4096 * channels];
        try {
            int n;
            while ((n = source.read(buf, 4096)) > 0) {
                for (int i = 0; i < n; i++) {
                    double mono = 0;
                    for (int c = 0; c < channels; c++) mono += buf[i * channels + c];
                    mono = mono / channels / 32768.0;
                    double b = bassLp.process(mono);
                    double m = midLp.process(midHp.process(mono));
                    double h = highHp.process(mono);
                    energy[0] += b * b;
                    energy[1] += m * m;
                    energy[2] += h * h;
                    if (++inWindow == windowFrames) {
                        rms.add(new double[] {Math.sqrt(energy[0] / inWindow), Math.sqrt(energy[1] / inWindow),
                                Math.sqrt(energy[2] / inWindow)});
                        energy[0] = energy[1] = energy[2] = 0;
                        inWindow = 0;
                    }
                }
            }
        } catch (IOException e) {
            throw new AudioException("Analyse: Audio nicht decodierbar", e);
        }
        if (inWindow > 0) {
            rms.add(new double[] {Math.sqrt(energy[0] / inWindow), Math.sqrt(energy[1] / inWindow), Math.sqrt(energy[2] / inWindow)});
        }

        int n = rms.size();
        byte[][] levels = new byte[3][n];
        double[] refs = new double[3];
        double loudest = 0;
        for (int band = 0; band < 3; band++) {
            refs[band] = percentile(rms, band, 0.98);
            loudest = Math.max(loudest, refs[band]);
        }
        for (int band = 0; band < 3; band++) {
            // Jedes Band wird auf sich selbst normiert; Restsignal eines leeren Bands bleibt aber leer
            double ref = refs[band] < loudest * NOISE_FLOOR ? Double.MAX_VALUE : refs[band];
            for (int i = 0; i < n; i++) {
                double v = ref < 1e-5 ? 0 : Math.min(1.0, rms.get(i)[band] / ref);
                levels[band][i] = (byte) Math.round(15 * Math.pow(v, 0.7));
            }
        }
        return new Analysis(HOP_MS, levels[0], levels[1], levels[2], detectBeats(rms));
    }

    private static double percentile(List<double[]> rms, int band, double p) {
        if (rms.isEmpty()) return 0;
        double[] values = new double[rms.size()];
        for (int i = 0; i < values.length; i++) values[i] = rms.get(i)[band];
        java.util.Arrays.sort(values);
        return values[(int) Math.min(values.length - 1, Math.floor(values.length * p))];
    }

    /** Onset-Erkennung: Anstieg der Bass-Energie ueber dem gleitenden Mittel der letzten Sekunde. */
    private static int[] detectBeats(List<double[]> rms) {
        int n = rms.size();
        int history = 1000 / HOP_MS;
        int minGap = MIN_BEAT_GAP_MS / HOP_MS;
        double[] flux = new double[n];
        for (int i = 1; i < n; i++) flux[i] = Math.max(0, rms.get(i)[0] - rms.get(i - 1)[0]);
        List<Integer> beats = new ArrayList<>();
        int last = -minGap;
        for (int i = 1; i < n - 1; i++) {
            double sum = 0;
            int from = Math.max(0, i - history);
            for (int j = from; j < i; j++) sum += flux[j];
            double mean = i > from ? sum / (i - from) : 0;
            boolean peak = flux[i] >= flux[i - 1] && flux[i] > flux[i + 1];
            if (peak && flux[i] > mean * 1.8 + 0.004 && i - last >= minGap) {
                beats.add(i * HOP_MS);
                last = i;
            }
        }
        return beats.stream().mapToInt(Integer::intValue).toArray();
    }
}
