package de.peaksound.audio;

import de.peaksound.protocol.AudioFormat;
import java.io.Closeable;
import java.io.IOException;

/** Strom von 16-Bit-PCM-Frames (interleaved). Spielt Songs streamend ab, ohne alles in den Speicher zu laden. */
public interface PcmSource extends Closeable {
    int sampleRate();

    int channels();

    /**
     * Liest bis zu {@code maxFrames} Frames in {@code out} (Laenge mindestens maxFrames * channels).
     *
     * @return gelesene Frames, oder -1 am Ende
     */
    int read(short[] out, int maxFrames) throws IOException;

    /**
     * Oeffnet einen Decoder fuer MP3, FLAC, WAV oder OGG (Ogg Vorbis ueber JOrbis).
     */
    static PcmSource open(byte[] data, AudioFormat format) throws AudioException {
        return open(new java.io.ByteArrayInputStream(data), format);
    }

    /**
     * Wie {@link #open(byte[], AudioFormat)}, aber von einem Strom: Der Decoder liest nur, was er braucht, und blockiert,
     * bis weitere Daten ankommen. So kann die Wiedergabe beginnen, waehrend der Rest noch geladen wird.
     */
    static PcmSource open(java.io.InputStream in, AudioFormat format) throws AudioException {
        return switch (format) {
            case MP3 -> new Mp3PcmSource(in);
            case FLAC -> new FlacPcmSource(in);
            case WAV -> new WavPcmSource(in);
            case OGG -> new OggPcmSource(in);
        };
    }
}
