package de.peaksound.audio;

import java.io.IOException;
import java.io.InputStream;
import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

/** FLAC-Decoder auf Basis von jFLAC (LGPL). Unterstuetzt 16 und 24 Bit (24 Bit wird auf 16 Bit gekuerzt). */
final class FlacPcmSource implements PcmSource {
    private final FLACDecoder decoder;
    private final int channels;
    private final int sampleRate;
    private final int bytesPerSample;
    private short[] pending = new short[0];
    private int pendingPos;
    private boolean eof;

    FlacPcmSource(InputStream in) throws AudioException {
        try {
            decoder = new FLACDecoder(in);
            StreamInfo info = decoder.readStreamInfo();
            if (info == null) throw new AudioException("FLAC ohne StreamInfo");
            decoder.readMetadata(info);
            int bits = info.getBitsPerSample();
            if (bits != 16 && bits != 24) throw new AudioException("FLAC mit " + bits + " Bit wird nicht unterstuetzt");
            bytesPerSample = bits / 8;
            channels = info.getChannels();
            sampleRate = info.getSampleRate();
        } catch (IOException e) {
            throw new AudioException("FLAC nicht lesbar", e);
        }
    }

    @Override public int sampleRate() { return sampleRate; }

    @Override public int channels() { return channels; }

    private boolean fill() throws IOException {
        if (eof) return false;
        Frame frame = decoder.readNextFrame();
        if (frame == null) {
            eof = true;
            return false;
        }
        ByteData pcm = decoder.decodeFrame(frame, null);
        int samples = pcm.getLen() / bytesPerSample;
        if (pending.length < samples) pending = new short[samples];
        byte[] raw = pcm.getData();
        for (int i = 0; i < samples; i++) {
            int o = i * bytesPerSample + bytesPerSample - 2; // oberste 16 Bit (little endian)
            pending[i] = (short) ((raw[o] & 0xFF) | (raw[o + 1] << 8));
        }
        pendingPos = 0;
        pendingLen = samples;
        return true;
    }

    private int pendingLen;

    @Override
    public int read(short[] out, int maxFrames) throws IOException {
        int frames = 0;
        while (frames < maxFrames) {
            if (pendingPos >= pendingLen && !fill()) break;
            int available = (pendingLen - pendingPos) / channels;
            int take = Math.min(available, maxFrames - frames);
            System.arraycopy(pending, pendingPos, out, frames * channels, take * channels);
            pendingPos += take * channels;
            frames += take;
        }
        return frames == 0 ? -1 : frames;
    }

    @Override
    public void close() { /* Daten liegen im Speicher */ }
}
