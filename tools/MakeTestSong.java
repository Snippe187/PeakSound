// Legt einen Test-Song direkt im Song-Speicher des lokalen Testservers an (ohne Client-Upload).
// Aufruf (Server vorher beenden):  java -cp <audio-core-classes>;<protocol-classes>;<libs>\* tools/MakeTestSong.java <songsDir> <songs.json>
import de.peaksound.audio.Analyzer;
import de.peaksound.audio.FlacWriter;
import de.peaksound.audio.PcmSource;
import de.peaksound.protocol.AudioFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

public class MakeTestSong {
    public static void main(String[] args) throws Exception {
        Path songsDir = Path.of(args[0]);
        Path index = Path.of(args[1]);
        int rate = 44100;
        int seconds = 30;
        int frames = rate * seconds;
        short[] pcm = new short[frames * 2];
        Random noise = new Random(1);
        for (int i = 0; i < frames; i++) {
            double t = (double) i / rate;
            double kick = Math.sin(2 * Math.PI * 60 * t) * Math.exp(-(t % 0.5) * 22);                  // Bass: 120 BPM
            double hat = (noise.nextDouble() * 2 - 1) * Math.exp(-((t + 0.25) % 0.5) * 40) * 0.4;      // Hoehen: Achtel dazwischen
            double lead = Math.sin(2 * Math.PI * (Math.floor(t) % 2 == 0 ? 523 : 659) * t) * 0.25;      // Mitten: Melodie
            short v = (short) ((kick * 0.8 + hat + lead) * 14000);
            pcm[2 * i] = v;
            pcm[2 * i + 1] = v;
        }
        PcmSource source = new PcmSource() {
            int pos;
            public int sampleRate() { return rate; }
            public int channels() { return 2; }
            public int read(short[] out, int max) {
                if (pos >= frames) return -1;
                int n = Math.min(max, frames - pos);
                System.arraycopy(pcm, pos * 2, out, 0, n * 2);
                pos += n;
                return n;
            }
            public void close() { }
        };
        byte[] flac = FlacWriter.encode(source);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(flac));
        Files.createDirectories(songsDir);
        Files.write(songsDir.resolve(hash + ".flac"), flac);
        try (PcmSource again = PcmSource.open(flac, AudioFormat.FLAC)) {
            Files.write(songsDir.resolve(hash + ".analysis"), Analyzer.analyze(again).toBytes());
        }
        String entry = """
                [
                  {
                    "hash": "%s",
                    "title": "Testbeat",
                    "artist": "PeakSound",
                    "uploader": "00000000-0000-0000-0000-000000000001",
                    "uploaderName": "Test",
                    "channels": 2,
                    "durationMs": %d,
                    "sizeBytes": %d,
                    "format": "flac",
                    "status": "OK",
                    "uploadedAt": %d,
                    "hasCover": false
                  }
                ]
                """.formatted(hash, seconds * 1000, flac.length, System.currentTimeMillis());
        Files.writeString(index, entry);
        System.out.println("Song angelegt: " + hash + " (" + flac.length + " Byte)");
    }
}
