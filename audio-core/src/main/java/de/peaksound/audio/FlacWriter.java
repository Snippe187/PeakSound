package de.peaksound.audio;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import net.sourceforge.javaflacencoder.FLACEncoder;
import net.sourceforge.javaflacencoder.FLACFileOutputStream;
import net.sourceforge.javaflacencoder.StreamConfiguration;

/** Schreibt PCM verlustfrei als FLAC (Java-Encoder, LGPL). Streamend, auch fuer Mehrkanal bis 8 Kanaele. */
public final class FlacWriter {
    private static final int BLOCK_FRAMES = 4096;

    private FlacWriter() {}

    public static byte[] encode(PcmSource source) throws AudioException {
        int channels = source.channels();
        if (channels < 1 || channels > StreamConfiguration.MAX_CHANNEL_COUNT) {
            throw new AudioException("Kanalzahl nicht unterstuetzt: " + channels);
        }
        // Eine seekbare Temp-Datei, damit die FLAC-Kopfdaten (Laenge, MD5) am Ende korrekt geschrieben werden
        File tmp = null;
        try {
            tmp = File.createTempFile("peaksound-flac-", ".flac");
            StreamConfiguration config = new StreamConfiguration(channels, BLOCK_FRAMES, BLOCK_FRAMES, source.sampleRate(), 16);
            FLACEncoder encoder = new FLACEncoder();
            FLACFileOutputStream out = new FLACFileOutputStream(tmp);
            encoder.setStreamConfiguration(config);
            encoder.setOutputStream(out);
            encoder.openFLACStream();

            short[] pcm = new short[BLOCK_FRAMES * channels];
            int[] samples = new int[BLOCK_FRAMES * channels];
            int frames;
            while ((frames = source.read(pcm, BLOCK_FRAMES)) > 0) {
                for (int i = 0; i < frames * channels; i++) samples[i] = pcm[i];
                encoder.addSamples(samples, frames);
                encoder.encodeSamples(encoder.fullBlockSamplesAvailableToEncode(), false);
            }
            encoder.encodeSamples(encoder.samplesAvailableToEncode(), true);
            out.close();
            return Files.readAllBytes(tmp.toPath());
        } catch (IOException e) {
            throw new AudioException("FLAC konnte nicht geschrieben werden", e);
        } finally {
            if (tmp != null) tmp.delete();
        }
    }
}
