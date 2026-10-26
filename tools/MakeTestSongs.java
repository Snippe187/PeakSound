// Legt zwei Test-Songs direkt im Song-Speicher des lokalen Testservers an (ohne Client-Upload):
//   1. "Testbeat"            Stereo, 30 s, Bass-Takt + Hi-Hat + Melodie (prueft Upmix und Equalizer)
//   2. "Surround-Demo 7.1"   8 Kanaele, 60 s: erst jeder Kanal einzeln, dann Musik mit einem Ton, der um den Raum kreist
// Aufruf (Server vorher beenden):
//   java -cp <audio-core-classes>;<protocol-classes>;<libs>\* tools/MakeTestSongs.java <songsDir> <songs.json>
import de.peaksound.audio.Analyzer;
import de.peaksound.audio.FlacWriter;
import de.peaksound.audio.PcmSource;
import de.peaksound.protocol.AudioFormat;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.function.IntUnaryOperator;

public class MakeTestSongs {
    /** Wie ein Kanal zur Zeit t klingt: Funktion (Zeit, Kanalindex) -> Amplitude -1..1. */
    interface Source { double sample(double t, int channel); }

    public static void main(String[] args) throws Exception {
        Path songsDir = Path.of(args[0]);
        Path index = Path.of(args[1]);
        Files.createDirectories(songsDir);
        List<String> entries = new ArrayList<>();
        entries.add(store(songsDir, "Testbeat", "PeakSound Stereo-Test", 2, 44100, 30, stereoBeat(), "OK"));
        entries.add(store(songsDir, "Stereo breit", "PeakSound Stereo-Test", 2, 44100, 30, wideStereo(), "OK"));
        entries.add(store(songsDir, "Surround-Demo 7.1", "PeakSound 3D-Test", 8, 48000, 60, surroundDemo(), "OK"));
        // Zwei gesperrte Mehrkanal-Dateien zum Testen der Freigabe im OP-Menue (6 Kanaele, 5.1)
        entries.add(store(songsDir, "Gesperrt: 5.1-Rundgang", "Warteschlange A", 6, 48000, 24, (t, c) -> channelWalk(t, c == 4 ? BL : c == 5 ? BR : c), "PENDING"));
        entries.add(store(songsDir, "Gesperrt: 5.1-Musik", "Warteschlange B", 6, 48000, 20, (t, c) -> music(t, c == 4 ? BL : c == 5 ? BR : c), "PENDING"));
        Files.writeString(index, "[\n" + String.join(",\n", entries) + "\n]\n");
    }

    // ---- Song 1: Stereo ----

    static Source stereoBeat() {
        Random noise = new Random(1);
        return (t, c) -> {
            double kick = Math.sin(2 * Math.PI * 60 * t) * Math.exp(-(t % 0.5) * 22);                  // Bass, 120 BPM
            double hat = (noise.nextDouble() * 2 - 1) * Math.exp(-((t + 0.25) % 0.5) * 40) * 0.4;      // Hoehen
            double lead = Math.sin(2 * Math.PI * (Math.floor(t) % 2 == 0 ? 523 : 659) * t) * 0.25;      // Mitten
            return (kick * 0.8 + hat + lead) * 0.45;
        };
    }

    /** Stereo mit echtem Unterschied links/rechts (links Melodie, rechts Akkord, beide teilen sich einen Bass). */
    static Source wideStereo() {
        return (t, c) -> {
            double bass = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-(t % 0.5) * 12) * 0.5;
            if (c == 0) {
                double[] melody = {523, 659, 784, 659};
                return bass + Math.sin(2 * Math.PI * melody[(int) (t * 2) % 4] * t) * 0.35 * Math.exp(-(t % 0.5) * 3);
            }
            return bass + (Math.sin(2 * Math.PI * 330 * t) + Math.sin(2 * Math.PI * 392 * t)) * 0.18 * (0.6 + 0.4 * Math.sin(2 * Math.PI * 0.5 * t));
        };
    }

    // ---- Song 2: 7.1 (Windows-Reihenfolge der Kanaele: FL FR C LFE BL BR SL SR) ----

    static final int FL = 0, FR = 1, C = 2, LFE = 3, BL = 4, BR = 5, SL = 6, SR = 7;
    /** Reihenfolge des Rundgangs (Uhrzeigersinn, von vorne links aus gesehen) und Winkel in Grad (0 = vorne Mitte). */
    static final int[] WALK = {FL, C, FR, SR, BR, BL, SL};
    static final double[] ANGLE = new double[8];
    static {
        ANGLE[FL] = -30; ANGLE[C] = 0; ANGLE[FR] = 30; ANGLE[SR] = 90; ANGLE[BR] = 150; ANGLE[BL] = 210; ANGLE[SL] = 270;
    }
    /** Jeder Kanal hat seinen eigenen Ton, damit man sie auseinanderhalten kann. */
    static final double[] TONE = new double[8];
    static {
        TONE[FL] = 392; TONE[C] = 440; TONE[FR] = 494; TONE[SR] = 523; TONE[BR] = 587; TONE[BL] = 659; TONE[SL] = 698; TONE[LFE] = 55;
    }

    static Source surroundDemo() {
        return (t, c) -> {
            if (t < 24) return channelWalk(t, c);
            return music(t - 24, c);
        };
    }

    /** 0-24 s: je 3 s ein Kanal (FL, C, FR, SR, BR, BL, SL, LFE) mit pulsierendem Eigenton. */
    static double channelWalk(double t, int c) {
        int segment = (int) (t / 3);
        int active = segment < 7 ? WALK[segment] : LFE;
        if (c != active) return 0;
        double gate = (t % 0.5) < 0.3 ? 1 : 0;                                    // 2 Pulse pro Sekunde
        double env = gate * Math.min(1, (t % 0.5) / 0.02) * Math.exp(-(t % 0.5) * 3);
        return Math.sin(2 * Math.PI * TONE[c] * t) * env * (c == LFE ? 0.9 : 0.6);
    }

    /** Ab 24 s: Musik. Bass vorne/Subwoofer, Akkorde an den Seiten, Arpeggio hinten, Melodie im Center, Ping kreist. */
    static double music(double t, int c) {
        double beat = t % 0.5;
        double kick = Math.sin(2 * Math.PI * 55 * t) * Math.exp(-beat * 14);
        double v = 0;
        switch (c) {
            case LFE -> v = kick * 0.9;
            case FL, FR -> v = kick * 0.35 + Math.sin(2 * Math.PI * 110 * t) * 0.12 * (Math.floor(t * 2) % 2 == 0 ? 1 : 0.5);
            case SL -> v = pad(t, new double[] {262, 330}) * 0.22;
            case SR -> v = pad(t, new double[] {392, 523}) * 0.22;
            case BL -> v = arpeggio(t, 0) * 0.3;
            case BR -> v = arpeggio(t, 1) * 0.3;
            case C -> {
                double[] melody = {523, 659, 784, 659, 523, 392, 440, 392};
                double f = melody[(int) (t * 2) % melody.length];
                v = Math.sin(2 * Math.PI * f * t) * 0.3 * Math.exp(-(t % 0.5) * 2.5);
            }
            default -> v = 0;
        }
        // Der Ping (hoher Ton) kreist alle 8 s einmal um den Raum: Lautstaerke je Kanal nach Winkelabstand
        double angle = (t % 8.0) / 8.0 * 360.0;
        if (c != LFE) {
            double diff = Math.toRadians(angle - ANGLE[c]);
            double weight = Math.max(0, Math.cos(diff));
            weight = weight * weight;
            double pingGate = (t % 0.5) < 0.12 ? Math.exp(-(t % 0.5) * 25) : 0;
            v += Math.sin(2 * Math.PI * 1318 * t) * pingGate * weight * 0.5;
        }
        return v;
    }

    static double pad(double t, double[] notes) {
        double s = 0;
        for (double f : notes) s += Math.sin(2 * Math.PI * f * t);
        return s / notes.length * (0.7 + 0.3 * Math.sin(2 * Math.PI * 0.25 * t));
    }

    static double arpeggio(double t, int side) {
        double[] notes = {523, 659, 784, 659};
        int step = (int) (t * 4);
        if (step % 2 != side) return 0;
        double local = (t * 4) % 1.0 / 4;
        return Math.sin(2 * Math.PI * notes[step % notes.length] * t) * Math.exp(-local * 14);
    }

    // ---- Speichern ----

    static String store(Path songsDir, String title, String artist, int channels, int rate, int seconds, Source source, String status) throws Exception {
        int frames = rate * seconds;
        short[] pcm = new short[frames * channels];
        for (int i = 0; i < frames; i++) {
            double t = (double) i / rate;
            for (int c = 0; c < channels; c++) {
                double v = Math.max(-1, Math.min(1, source.sample(t, c)));
                pcm[i * channels + c] = (short) (v * 16000);
            }
        }
        PcmSource src = new PcmSource() {
            int pos;
            public int sampleRate() { return rate; }
            public int channels() { return channels; }
            public int read(short[] out, int max) {
                if (pos >= frames) return -1;
                int n = Math.min(max, frames - pos);
                System.arraycopy(pcm, pos * channels, out, 0, n * channels);
                pos += n;
                return n;
            }
            public void close() { }
        };
        byte[] flac = FlacWriter.encode(src);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(flac));
        Files.write(songsDir.resolve(hash + ".flac"), flac);
        try (PcmSource again = PcmSource.open(flac, AudioFormat.FLAC)) {
            Files.write(songsDir.resolve(hash + ".analysis"), Analyzer.analyze(again).toBytes());
        }
        System.out.println(title + ": " + hash + " (" + flac.length / 1024 + " KiB, " + channels + " Kanaele)");
        return """
                  {
                    "hash": "%s",
                    "title": "%s",
                    "artist": "%s",
                    "uploader": "00000000-0000-0000-0000-000000000001",
                    "uploaderName": "Test",
                    "channels": %d,
                    "durationMs": %d,
                    "sizeBytes": %d,
                    "format": "flac",
                    "status": "%s",
                    "uploadedAt": %d,
                    "hasCover": false
                  }""".formatted(hash, title, artist, channels, seconds * 1000, flac.length, status, System.currentTimeMillis());
    }
}
