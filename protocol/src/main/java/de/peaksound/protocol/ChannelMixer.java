package de.peaksound.protocol;

/**
 * Erzeugt aus mehrkanaligem 16-Bit-PCM das Mono-Signal EINES Lautsprechers (gemaess {@link ChannelLayout.Mapping}).
 * Jeder Lautsprecher hat seinen eigenen Mixer, weil der Subwoofer-Tiefpass Zustand behaelt.
 */
public final class ChannelMixer {
    /** Grenzfrequenz des abgeleiteten Subwoofers. */
    public static final double LFE_CUTOFF_HZ = 120;

    private final float[] gains;
    private final boolean lowPass;
    private final Filter filterA;
    private final Filter filterB;
    /** Zusaetzlicher Filter der Bauart (Tieftoener, Hochtoener, Subwoofer), sonst null. */
    private final Filter kindA;
    private final Filter kindB;

    /** Zweipol-Tiefpass (Butterworth-Guete), zweimal hintereinander = vierte Ordnung. */
    private static final class Filter {
        private final double b0, b1, b2, a1, a2;
        private double x1, x2, y1, y2;

        Filter(double fs, double fc) {
            this(fs, fc, false);
        }

        Filter(double fs, double fc, boolean high) {
            double w = 2 * Math.PI * Math.min(fc, fs * 0.45) / fs, cos = Math.cos(w), alpha = Math.sin(w) / (2 * 0.7071);
            double a0 = 1 + alpha;
            b0 = (high ? (1 + cos) / 2 : (1 - cos) / 2) / a0;
            b1 = (high ? -(1 + cos) : (1 - cos)) / a0;
            b2 = b0;
            a1 = -2 * cos / a0;
            a2 = (1 - alpha) / a0;
        }

        double process(double x) {
            double y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return y;
        }
    }

    public ChannelMixer(ChannelLayout.Mapping mapping, int sampleRate) {
        this(mapping, sampleRate, SpeakerKind.FULL_RANGE);
    }

    public ChannelMixer(ChannelLayout.Mapping mapping, int sampleRate, SpeakerKind kind) {
        boolean filtered = kind != SpeakerKind.FULL_RANGE;
        this.kindA = filtered ? new Filter(sampleRate, kind.cutoffHz(), kind.highPass()) : null;
        this.kindB = filtered ? new Filter(sampleRate, kind.cutoffHz(), kind.highPass()) : null;
        this.gains = mapping.gains();
        this.lowPass = mapping.lowPass();
        this.filterA = lowPass ? new Filter(sampleRate, LFE_CUTOFF_HZ) : null;
        this.filterB = lowPass ? new Filter(sampleRate, LFE_CUTOFF_HZ) : null;
    }

    /**
     * @param interleaved Quelle, {@code channels} Kanaele pro Frame (muss der Mapping-Groesse entsprechen)
     * @return {@code frames} Mono-Samples
     */
    public short[] process(short[] interleaved, int frames, int channels) {
        if (channels != gains.length) throw new IllegalArgumentException("Kanalzahl passt nicht zum Mapping");
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            double sum = 0;
            for (int c = 0; c < channels; c++) sum += gains[c] * interleaved[i * channels + c];
            if (lowPass) sum = filterB.process(filterA.process(sum));
            if (kindA != null) sum = kindB.process(kindA.process(sum));
            out[i] = (short) Math.max(-32768, Math.min(32767, Math.round(sum)));
        }
        return out;
    }

    /** Mono-Mischung ueber alle Kanaele (fuer eine Jukebox ohne Lautsprecher). */
    public static short[] downmix(short[] interleaved, int frames, int channels) {
        short[] mono = new short[frames];
        if (channels == 1) {
            System.arraycopy(interleaved, 0, mono, 0, frames);
            return mono;
        }
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += interleaved[i * channels + c];
            mono[i] = (short) (sum / channels);
        }
        return mono;
    }
}
