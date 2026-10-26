package de.peaksound.audio;

import static org.junit.jupiter.api.Assertions.*;

import de.peaksound.protocol.AudioFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.DoubleUnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class AnalyzerTest {
    private static final int RATE = 44100;

    /** Mono-Signal aus einer Funktion der Zeit. */
    private static PcmSource signal(double seconds, DoubleUnaryOperator f) {
        int frames = (int) (seconds * RATE);
        short[] data = new short[frames];
        for (int i = 0; i < frames; i++) data[i] = (short) (f.applyAsDouble((double) i / RATE) * 20000);
        return new PcmSource() {
            int pos;
            @Override public int sampleRate() { return RATE; }
            @Override public int channels() { return 1; }
            @Override public int read(short[] out, int maxFrames) {
                if (pos >= data.length) return -1;
                int n = Math.min(maxFrames, data.length - pos);
                System.arraycopy(data, pos, out, 0, n);
                pos += n;
                return n;
            }
            @Override public void close() { }
        };
    }

    @Test
    void bandsSeparateLowAndHighTones() throws Exception {
        Analysis low = Analyzer.analyze(signal(3, t -> Math.sin(2 * Math.PI * 80 * t)));
        Analysis high = Analyzer.analyze(signal(3, t -> Math.sin(2 * Math.PI * 6000 * t)));
        int mid = 30; // eine Sekunde in den Song
        assertTrue(low.bass()[mid] >= 14, "Bass bei 80 Hz: " + low.bass()[mid]);
        assertTrue(low.high()[mid] <= 1, "Hoehen bei 80 Hz: " + low.high()[mid]);
        assertTrue(high.high()[mid] >= 14, "Hoehen bei 6 kHz: " + high.high()[mid]);
        assertTrue(high.bass()[mid] <= 1, "Bass bei 6 kHz: " + high.bass()[mid]);
    }

    @Test
    void midBandReactsToVoiceFrequencies() throws Exception {
        Analysis a = Analyzer.analyze(signal(3, t -> Math.sin(2 * Math.PI * 1000 * t)));
        assertTrue(a.mid()[30] >= 14);
        assertTrue(a.bass()[30] <= 2);
        assertTrue(a.high()[30] <= 3);
    }

    @Test
    void detectsBeatsOfA120BpmKick() throws Exception {
        // Alle 500 ms ein kurzer 60-Hz-Schlag mit schnellem Abklingen
        Analysis a = Analyzer.analyze(signal(10, t -> {
            double phase = t % 0.5;
            return Math.sin(2 * Math.PI * 60 * t) * Math.exp(-phase * 25);
        }));
        assertTrue(a.beatsMs().length >= 17 && a.beatsMs().length <= 21, "Schlaege: " + a.beatsMs().length);
        for (int beat : a.beatsMs()) {
            int offset = beat % 500;
            assertTrue(offset <= 100 || offset >= 400, "Schlag bei " + beat + " ms liegt neben dem Takt");
        }
    }

    @Test
    void silenceGivesZeroLevelsAndNoBeats() throws Exception {
        Analysis a = Analyzer.analyze(signal(2, t -> 0));
        for (byte b : a.bass()) assertEquals(0, b);
        assertEquals(0, a.beatsMs().length);
    }

    @Test
    void levelLookupAndBeatSearch() {
        Analysis a = new Analysis(50, new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, new byte[] {7, 8, 9},
                new int[] {100, 600, 1100});
        assertEquals(2, a.level(Analysis.Band.BASS, 50));
        assertEquals(6, a.level(Analysis.Band.MID, 120));
        assertEquals(0, a.level(Analysis.Band.HIGH, 10_000));
        assertEquals(0, a.level(Analysis.Band.HIGH, -5));
        assertEquals(100, a.nextBeatAfter(-1));
        assertEquals(600, a.nextBeatAfter(100));
        assertEquals(-1, a.nextBeatAfter(1100));
    }

    @Test
    void serializationRoundTripAndCorruptionIsRejected() throws Exception {
        Analysis a = new Analysis(50, new byte[] {1, 2, 3}, new byte[] {4, 5, 6}, new byte[] {7, 8, 9}, new int[] {100, 600});
        Analysis b = Analysis.fromBytes(a.toBytes());
        assertArrayEquals(a.bass(), b.bass());
        assertArrayEquals(a.beatsMs(), b.beatsMs());
        assertThrows(AudioException.class, () -> Analysis.fromBytes(new byte[] {1, 2, 3}));
        byte[] bytes = a.toBytes();
        assertThrows(AudioException.class, () -> Analysis.fromBytes(java.util.Arrays.copyOf(bytes, bytes.length - 3)));
    }

    /** Echtes Ogg Vorbis testen, wenn auf dem PC eines vorliegt (Minecraft-Sounds im Asset-Cache). */
    @Test
    void decodesRealOggIfAvailable() throws Exception {
        Path objects = Path.of(System.getProperty("user.home"), "AppData", "Roaming", ".minecraft", "assets", "objects");
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(objects), "kein Minecraft-Asset-Cache");
        byte[] ogg = null;
        try (Stream<Path> files = Files.walk(objects, 2)) {
            for (Path p : (Iterable<Path>) files.filter(Files::isRegularFile).filter(f -> {
                try { return Files.size(f) > 20_000 && Files.size(f) < 3_000_000; } catch (IOException e) { return false; }
            })::iterator) {
                byte[] bytes = Files.readAllBytes(p);
                if (AudioFormat.detect(bytes).orElse(null) == AudioFormat.OGG) { ogg = bytes; break; }
            }
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(ogg != null, "keine Ogg-Datei gefunden");
        try (PcmSource src = PcmSource.open(ogg, AudioFormat.OGG)) {
            short[] buf = new short[4096 * src.channels()];
            long frames = 0;
            int n;
            boolean nonSilent = false;
            while ((n = src.read(buf, 4096)) > 0) {
                frames += n;
                for (int i = 0; i < n * src.channels() && !nonSilent; i++) nonSilent = Math.abs(buf[i]) > 200;
            }
            assertTrue(frames > src.sampleRate() / 4, "zu wenig Frames: " + frames);
            assertTrue(nonSilent, "Ogg liefert nur Stille");
        }
        Analysis a = Analyzer.analyze(PcmSource.open(ogg, AudioFormat.OGG));
        assertTrue(a.bass().length > 0);
    }
}
