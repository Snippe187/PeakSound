package de.peaksound.audio;

import de.peaksound.protocol.AudioFormat;
import de.peaksound.protocol.Limits;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

/** Liest Format, Laenge, Kanaele und Tags (Titel, Interpret, Cover) einer Audiodatei. */
public final class AudioInspector {
    static {
        // jaudiotagger loggt sehr gespraechig; Fehler melden wir ueber AudioException
        Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
    }

    private AudioInspector() {}

    /** Maximale Cover-Groesse, die uebernommen wird. Groessere Bilder werden ignoriert. */
    public static final int MAX_COVER_BYTES = 512 * 1024;

    /** Untersucht Rohdaten. Das Format wird an den ersten Bytes erkannt, nie an einem Dateinamen. */
    public static AudioInfo inspect(byte[] data) throws AudioException {
        AudioFormat format = AudioFormat.detect(data).orElseThrow(() -> new AudioException("Unbekanntes Format"));
        Path tmp = null;
        try {
            // jaudiotagger braucht eine Datei mit passender Endung
            tmp = Files.createTempFile("peaksound-inspect-", "." + format.extension());
            Files.write(tmp, data);
            return inspect(tmp, format);
        } catch (IOException e) {
            throw new AudioException("Datei nicht lesbar", e);
        } finally {
            if (tmp != null) {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { /* Temp-Datei, unkritisch */ }
            }
        }
    }

    /** Untersucht eine Datei auf der Platte (Client: vor dem Upload). */
    public static AudioInfo inspect(Path file) throws AudioException {
        byte[] head;
        try (var in = Files.newInputStream(file)) {
            head = in.readNBytes(16);
        } catch (IOException e) {
            throw new AudioException("Datei nicht lesbar", e);
        }
        AudioFormat format = AudioFormat.detect(head).orElseThrow(() -> new AudioException("Unbekanntes Format"));
        return inspect(file, format);
    }

    /**
     * Exakte Laenge in Millisekunden durch Zaehlen der decodierten Frames. Header-Angaben koennen bei
     * MP3 ohne VBR-Kopf falsch sein. OGG wird nicht decodiert (dort ist die Header-Laenge exakt).
     */
    public static int measureExactMs(byte[] data, AudioFormat format) throws AudioException {
        if (format == AudioFormat.OGG) return inspect(data).durationMs();
        try (PcmSource source = PcmSource.open(data, format)) {
            short[] buf = new short[4096 * Math.max(1, source.channels())];
            long frames = 0;
            int n;
            while ((n = source.read(buf, 4096)) > 0) frames += n;
            return (int) Math.min(Integer.MAX_VALUE, frames * 1000L / Math.max(1, source.sampleRate()));
        } catch (IOException e) {
            throw new AudioException("Audio nicht decodierbar", e);
        }
    }

    private static AudioInfo inspect(Path file, AudioFormat format) throws AudioException {
        try {
            AudioFile audio = AudioFileIO.readAs(file.toFile(), format.extension());
            AudioHeader header = audio.getAudioHeader();
            int channels = parseInt(header.getChannels(), 2);
            int durationMs = (int) Math.min(Integer.MAX_VALUE, Math.round(header.getPreciseTrackLength() * 1000.0));
            String title = "";
            String artist = "";
            byte[] cover = new byte[0];
            Tag tag = audio.getTag();
            if (tag != null) {
                title = clean(tag.getFirst(FieldKey.TITLE));
                artist = clean(tag.getFirst(FieldKey.ARTIST));
                Artwork art = tag.getFirstArtwork();
                if (art != null && art.getBinaryData() != null && art.getBinaryData().length <= MAX_COVER_BYTES) {
                    cover = art.getBinaryData();
                }
            }
            return new AudioInfo(format, durationMs, channels, header.getSampleRateAsNumber(),
                    header.getBitsPerSample(), title, artist, cover);
        } catch (Exception e) {
            throw new AudioException("Audiodatei nicht lesbar: " + e.getMessage(), e);
        }
    }

    private static int parseInt(String s, int fallback) {
        if (s == null) return fallback;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    /** Steuerzeichen entfernen und Laenge begrenzen: Der Text landet in Itemnamen und im Chat. */
    public static String clean(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\p{Cntrl}", " ").trim();
        return t.length() > Limits.MAX_TEXT ? t.substring(0, Limits.MAX_TEXT) : t;
    }
}
