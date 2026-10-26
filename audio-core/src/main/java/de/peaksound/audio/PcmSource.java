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
     * Oeffnet einen Decoder fuer MP3, FLAC, WAV oder OGG. Der Client spielt OGG selbst ueber STBVorbis ab;
     * der OGG-Decoder hier dient der serverseitigen Analyse.
     */
    static PcmSource open(byte[] data, AudioFormat format) throws AudioException {
        return switch (format) {
            case MP3 -> new Mp3PcmSource(data);
            case FLAC -> new FlacPcmSource(data);
            case WAV -> new WavPcmSource(data);
            case OGG -> new OggPcmSource(data);
        };
    }
}
