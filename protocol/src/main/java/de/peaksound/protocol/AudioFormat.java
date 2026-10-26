package de.peaksound.protocol;

import java.util.Optional;

/** Unterstuetzte Audioformate, erkannt an den ersten Bytes (die Dateiendung wird nie vertraut). */
public enum AudioFormat {
    MP3("mp3"), FLAC("flac"), WAV("wav"), OGG("ogg");

    private final String extension;

    AudioFormat(String extension) { this.extension = extension; }

    public String extension() { return extension; }

    public static Optional<AudioFormat> detect(byte[] data) {
        if (data.length < 12) return Optional.empty();
        if (data[0] == 'f' && data[1] == 'L' && data[2] == 'a' && data[3] == 'C') return Optional.of(FLAC);
        if (data[0] == 'O' && data[1] == 'g' && data[2] == 'g' && data[3] == 'S') return Optional.of(OGG);
        if (data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'A' && data[10] == 'V' && data[11] == 'E') return Optional.of(WAV);
        if (data[0] == 'I' && data[1] == 'D' && data[2] == '3') return Optional.of(MP3);
        // MPEG-Frame-Sync: 11 gesetzte Bits
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xE0) == 0xE0) return Optional.of(MP3);
        return Optional.empty();
    }
}
