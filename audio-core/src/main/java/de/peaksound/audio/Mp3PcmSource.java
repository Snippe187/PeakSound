package de.peaksound.audio;

import java.io.IOException;
import java.io.InputStream;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.DecoderException;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

/** MP3-Decoder auf Basis von JLayer (LGPL). */
final class Mp3PcmSource implements PcmSource {
    private final Bitstream bitstream;
    private final Decoder decoder = new Decoder();
    private int sampleRate;
    private int channels;
    private short[] pending = new short[0];
    private int pendingPos;
    private boolean eof;

    Mp3PcmSource(InputStream in) throws AudioException {
        bitstream = new Bitstream(in);
        // Ersten Frame lesen, damit Samplerate und Kanalzahl bekannt sind
        try {
            if (!fill()) throw new AudioException("MP3 enthaelt keine Frames");
        } catch (IOException e) {
            throw new AudioException("MP3 nicht lesbar", e);
        }
    }

    private boolean fill() throws IOException {
        if (eof) return false;
        try {
            Header header = bitstream.readFrame();
            if (header == null) {
                eof = true;
                return false;
            }
            SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
            sampleRate = out.getSampleFrequency();
            channels = out.getChannelCount();
            pending = java.util.Arrays.copyOf(out.getBuffer(), out.getBufferLength());
            pendingPos = 0;
            bitstream.closeFrame();
            return true;
        } catch (BitstreamException | DecoderException e) {
            throw new IOException("MP3-Fehler: " + e.getMessage(), e);
        }
    }

    @Override public int sampleRate() { return sampleRate; }

    @Override public int channels() { return channels; }

    @Override
    public int read(short[] out, int maxFrames) throws IOException {
        int frames = 0;
        while (frames < maxFrames) {
            if (pendingPos >= pending.length && !fill()) break;
            int available = (pending.length - pendingPos) / channels;
            int take = Math.min(available, maxFrames - frames);
            System.arraycopy(pending, pendingPos, out, frames * channels, take * channels);
            pendingPos += take * channels;
            frames += take;
        }
        return frames == 0 ? -1 : frames;
    }

    @Override
    public void close() {
        try { bitstream.close(); } catch (BitstreamException ignored) { /* nichts zu retten */ }
    }
}
