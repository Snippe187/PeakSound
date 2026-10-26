package de.peaksound.audio;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** WAV-Decoder ueber javax.sound (JDK). Wandelt 8/24/32-Bit auf 16 Bit um. */
final class WavPcmSource implements PcmSource {
    private final AudioInputStream stream;
    private final int channels;
    private final int sampleRate;
    private byte[] buffer = new byte[0];

    WavPcmSource(InputStream source) throws AudioException {
        try {
            // javax.sound braucht mark/reset zum Erkennen des Formats
            AudioInputStream raw = AudioSystem.getAudioInputStream(new BufferedInputStream(source, 1 << 16));
            AudioFormat in = raw.getFormat();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, in.getSampleRate(), 16,
                    in.getChannels(), in.getChannels() * 2, in.getSampleRate(), false);
            stream = AudioSystem.isConversionSupported(target, in) ? AudioSystem.getAudioInputStream(target, raw) : null;
            if (stream == null) throw new AudioException("WAV-Format wird nicht unterstuetzt");
            channels = in.getChannels();
            sampleRate = Math.round(in.getSampleRate());
        } catch (UnsupportedAudioFileException | IOException e) {
            throw new AudioException("WAV nicht lesbar", e);
        }
    }

    @Override public int sampleRate() { return sampleRate; }

    @Override public int channels() { return channels; }

    @Override
    public int read(short[] out, int maxFrames) throws IOException {
        int bytesWanted = maxFrames * channels * 2;
        if (buffer.length < bytesWanted) buffer = new byte[bytesWanted];
        int read = stream.readNBytes(buffer, 0, bytesWanted);
        if (read <= 0) return -1;
        int frames = read / (channels * 2);
        for (int i = 0; i < frames * channels; i++) {
            out[i] = (short) ((buffer[2 * i] & 0xFF) | (buffer[2 * i + 1] << 8));
        }
        return frames;
    }

    @Override
    public void close() throws IOException { stream.close(); }
}
