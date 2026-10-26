package de.peaksound.audio;

import com.jcraft.jogg.Packet;
import com.jcraft.jogg.Page;
import com.jcraft.jogg.StreamState;
import com.jcraft.jogg.SyncState;
import com.jcraft.jorbis.Block;
import com.jcraft.jorbis.Comment;
import com.jcraft.jorbis.DspState;
import com.jcraft.jorbis.Info;
import java.io.IOException;
import java.io.InputStream;

/**
 * Ogg-Vorbis-Decoder auf Basis von JOrbis (LGPL), nur fuer die serverseitige Analyse.
 * Der Client spielt Vorbis ueber LWJGLs STBVorbis ab.
 */
final class OggPcmSource implements PcmSource {
    private static final int BUFFER_SIZE = 8192;

    private final InputStream in;
    private final SyncState oy = new SyncState();
    private final StreamState os = new StreamState();
    private final Page og = new Page();
    private final Packet op = new Packet();
    private final Info vi = new Info();
    private final Comment vc = new Comment();
    private final DspState vd = new DspState();
    private final Block vb = new Block(vd);
    private final float[][][] pcm = new float[1][][];
    private final int[] index;
    private final int channels;
    private final int sampleRate;
    private short[] pending = new short[0];
    private int pendingPos;
    private boolean eof;

    OggPcmSource(InputStream source) throws AudioException {
        in = source;
        try {
            oy.init();
            readMore();
            if (oy.pageout(og) != 1) throw new AudioException("Kein Ogg-Stream");
            os.init(og.serialno());
            vi.init();
            vc.init();
            if (os.pagein(og) < 0 || os.packetout(op) != 1 || vi.synthesis_headerin(vc, op) < 0) {
                throw new AudioException("Kein Vorbis-Stream");
            }
            int headers = 0;
            while (headers < 2) {
                int page = oy.pageout(og);
                if (page == 0) {
                    if (!readMore()) throw new AudioException("Ogg-Header unvollstaendig");
                    continue;
                }
                if (page < 0) continue;
                os.pagein(og);
                while (headers < 2) {
                    int packet = os.packetout(op);
                    if (packet == 0) break;
                    if (packet < 0 || vi.synthesis_headerin(vc, op) < 0) throw new AudioException("Vorbis-Header defekt");
                    headers++;
                }
            }
            vd.synthesis_init(vi);
            vb.init(vd);
            channels = vi.channels;
            sampleRate = vi.rate;
            index = new int[channels];
            if (channels < 1 || sampleRate < 1) throw new AudioException("Vorbis-Format ungueltig");
        } catch (IOException e) {
            throw new AudioException("Ogg nicht lesbar", e);
        }
    }

    private boolean readMore() throws IOException {
        int offset = oy.buffer(BUFFER_SIZE);
        int n = in.read(oy.data, offset, BUFFER_SIZE);
        if (n <= 0) return false;
        oy.wrote(n);
        return true;
    }

    @Override public int sampleRate() { return sampleRate; }

    @Override public int channels() { return channels; }

    /** Decodiert bis Samples vorliegen. @return false am Ende des Streams */
    private boolean fill() throws IOException {
        if (eof) return false;
        while (true) {
            int samples = vd.synthesis_pcmout(pcm, index);
            if (samples > 0) {
                float[][] chans = pcm[0];
                if (pending.length < samples * channels) pending = new short[samples * channels];
                for (int c = 0; c < channels; c++) {
                    int base = index[c];
                    for (int i = 0; i < samples; i++) {
                        float v = chans[c][base + i] * 32767f;
                        pending[i * channels + c] = (short) Math.max(-32768, Math.min(32767, Math.round(v)));
                    }
                }
                pendingLen = samples * channels;
                pendingPos = 0;
                vd.synthesis_read(samples);
                return true;
            }
            int packet = os.packetout(op);
            if (packet == 0) {
                int page = oy.pageout(og);
                if (page == 0) {
                    if (!readMore()) {
                        eof = true;
                        return false;
                    }
                } else if (page == 1) {
                    os.pagein(og);
                }
            } else if (packet == 1 && vb.synthesis(op) == 0) {
                vd.synthesis_blockin(vb);
            }
        }
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
    public void close() throws IOException { in.close(); }
}
