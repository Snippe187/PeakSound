package de.peaksound.audio;

import de.peaksound.protocol.AudioFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Wandelt Uploads in ein platzsparendes, vom Client abspielbares Format um.
 * <ul>
 *   <li>Mit ffmpeg: alles ausser OGG wird zu Ogg Vorbis (Mehrkanal bleibt erhalten; scheitert Vorbis, wird FLAC verwendet).</li>
 *   <li>Ohne ffmpeg: OGG, FLAC und MP3 bleiben wie sie sind, WAV wird verlustfrei zu FLAC.</li>
 * </ul>
 * Titel, Interpret und Cover werden nicht uebernommen; sie liegen getrennt in der Song-Datenbank.
 */
public final class Transcoder {
    public record Result(byte[] data, AudioFormat format) {}

    private static final long FFMPEG_TIMEOUT_SECONDS = 120;

    private final String ffmpeg; // null = nicht verfuegbar

    private Transcoder(String ffmpeg) { this.ffmpeg = ffmpeg; }

    /** @param configuredPath Pfad aus der Config; leer = "ffmpeg" im PATH */
    public static Transcoder create(String configuredPath) {
        String candidate = configuredPath == null || configuredPath.isBlank() ? "ffmpeg" : configuredPath.trim();
        return new Transcoder(works(candidate) ? candidate : null);
    }

    public boolean ffmpegAvailable() { return ffmpeg != null; }

    private static boolean works(String exe) {
        try {
            Process p = new ProcessBuilder(exe, "-version").redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return false;
        }
    }

    public Result transcode(byte[] input, AudioFormat format) throws AudioException {
        if (format == AudioFormat.OGG) return new Result(input, AudioFormat.OGG);
        if (ffmpeg != null) {
            Optional<Result> viaFfmpeg = ffmpegConvert(input, format);
            if (viaFfmpeg.isPresent()) return viaFfmpeg.get();
            // ffmpeg ist da, hat aber versagt: auf den Java-Weg zurueckfallen
        }
        return switch (format) {
            case WAV -> {
                try (PcmSource src = PcmSource.open(input, AudioFormat.WAV)) {
                    yield new Result(FlacWriter.encode(src), AudioFormat.FLAC);
                } catch (IOException e) {
                    throw new AudioException("WAV nicht lesbar", e);
                }
            }
            default -> new Result(input, format); // FLAC und MP3 sind bereits kompakt
        };
    }

    private Optional<Result> ffmpegConvert(byte[] input, AudioFormat format) {
        Path dir = null;
        try {
            dir = Files.createTempDirectory("peaksound-ffmpeg-");
            Path in = dir.resolve("in." + format.extension());
            Files.write(in, input);
            // Zuerst Vorbis (klein), bei Mehrkanal-Problemen FLAC (verlustfrei)
            for (String[] target : List.of(
                    new String[] {"ogg", "-c:a", "libvorbis", "-q:a", "5"},
                    new String[] {"flac", "-c:a", "flac"})) {
                Path out = dir.resolve("out." + target[0]);
                List<String> cmd = new java.util.ArrayList<>(List.of(ffmpeg, "-nostdin", "-v", "error", "-y",
                        "-i", in.toString(), "-vn", "-map_metadata", "-1"));
                for (int i = 1; i < target.length; i++) cmd.add(target[i]);
                cmd.add(out.toString());
                Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (!p.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    continue;
                }
                if (p.exitValue() == 0 && Files.size(out) > 0) {
                    return Optional.of(new Result(Files.readAllBytes(out),
                            target[0].equals("ogg") ? AudioFormat.OGG : AudioFormat.FLAC));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            deleteQuietly(dir);
        }
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) return;
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) { /* Temp-Verzeichnis, unkritisch */ }
    }
}
