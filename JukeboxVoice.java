package de.peaksound.mod.client;

import de.peaksound.audio.AudioException;
import de.peaksound.audio.PcmSource;
import de.peaksound.protocol.AudioFormat;
import de.peaksound.protocol.Packet;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Eine spielende Jukebox beim Client: decodiert den Song in einem Hintergrund-Thread zu Mono-Bloecken
 * und fuettert damit eine OpenAL-Quelle an der Position des Blocks. Die Quelle dampft sich selbst nach
 * Entfernung/Reichweite; Richtung und Panning uebernimmt OpenAL anhand von Quelle und Hoerer.
 *
 * <p>Synchronisation: Jeder Block kennt seine Position im Song. Beim Einreihen wird sie mit der erwarteten
 * Position (aus der Serveruhr) verglichen: zu spaete Bloecke werden verworfen, zu fruehe mit Stille
 * ueberbrueckt. So steigen spaet Hinzukommende richtig ein und Aussetzer werden aufgeholt.
 */
final class JukeboxVoice {
    private record Block(long startFrame, short[] mono) {}

    private static final int AL_BUFFERS = 6;
    private static final int MAX_GAP_MS = 3000;
    private static final int TOLERANCE_MS = 120;
    private static final Block END = new Block(-1, new short[0]);

    private final long key;
    private final double x, y, z;
    private final String hash;
    private final long startedAtServerMs;
    private final int durationMs;
    private final int range;
    private final int volume;

    private final BlockingQueue<Block> decoded = new ArrayBlockingQueue<>(8);
    private final Deque<Integer> queuedLengths = new ArrayDeque<>();
    private final Deque<Integer> freeBuffers = new ArrayDeque<>();
    private volatile boolean stopped;
    private volatile int sampleRate;
    private Thread decoder;
    private boolean decoderStarted;
    private int source = -1;
    private final int[] buffers = new int[AL_BUFFERS];
    private Block held;
    private boolean finished;

    JukeboxVoice(Packet.PlaybackStart start) {
        this.key = key(start.x(), start.y(), start.z());
        this.x = start.x() + 0.5;
        this.y = start.y() + 0.5;
        this.z = start.z() + 0.5;
        this.hash = start.hash();
        // Der Server nennt den Offset zum Sendezeitpunkt; daraus ergibt sich der Start des Songs auf der Serveruhr
        this.startedAtServerMs = start.serverTimeMs() - start.offsetMs();
        this.durationMs = start.durationMs();
        this.range = start.range();
        this.volume = start.volume();
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    long key() { return key; }

    String hash() { return hash; }

    long startedAt() { return startedAtServerMs; }

    boolean finished() { return finished; }

    void stop() {
        stopped = true;
        if (decoder != null) decoder.interrupt();
        releaseAl();
        finished = true;
    }

    /** Songdaten sind da: Decoder-Thread starten. */
    void onData(byte[] data) {
        if (stopped || decoderStarted) return;
        decoderStarted = true;
        decoder = Thread.ofPlatform().name("PeakSound-Decoder").daemon(true).start(() -> decode(data));
    }

    private long expectedFrame() {
        long elapsedMs = ServerState.serverNow() - startedAtServerMs;
        return Math.max(0, elapsedMs) * sampleRate / 1000;
    }

    // ---- Decoder-Thread ----

    private void decode(byte[] data) {
        try {
            AudioFormat format = AudioFormat.detect(data).orElseThrow(() -> new AudioException("Format"));
            if (format == AudioFormat.OGG) decodeVorbis(data);
            else decodePcm(data, format);
            decoded.put(END);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            try { decoded.put(END); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    private void decodePcm(byte[] data, AudioFormat format) throws Exception {
        try (PcmSource src = PcmSource.open(data, format)) {
            sampleRate = src.sampleRate();
            int channels = src.channels();
            int blockFrames = Math.max(1024, sampleRate / 4);
            short[] raw = new short[blockFrames * channels];
            long frame = skipTarget();
            long position = 0;
            int n;
            while (!stopped && (n = src.read(raw, blockFrames)) > 0) {
                if (position + n > frame) { // Bloecke vor dem Einstiegspunkt ueberspringen (decodieren, aber nicht abspielen)
                    decoded.put(new Block(position, downmix(raw, n, channels)));
                }
                position += n;
            }
        }
    }

    private void decodeVorbis(byte[] data) throws Exception {
        ByteBuffer file = MemoryUtil.memAlloc(data.length).put(data).flip();
        long handle = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(file, error, null);
            if (handle == 0) throw new AudioException("Ogg Vorbis nicht lesbar: " + error.get(0));
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            int channels = info.channels();
            sampleRate = info.sample_rate();
            int blockFrames = Math.max(1024, sampleRate / 4);
            ShortBuffer pcm = MemoryUtil.memAllocShort(blockFrames * channels);
            try {
                long skip = skipTarget();
                if (skip > 0) STBVorbis.stb_vorbis_seek(handle, (int) skip); // springt zum naechsten Vorbis-Paket
                long position = skip > 0 ? STBVorbis.stb_vorbis_get_sample_offset(handle) : 0;
                int n;
                while (!stopped && (n = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm)) > 0) {
                    short[] raw = new short[n * channels];
                    pcm.get(0, raw);
                    decoded.put(new Block(position, downmix(raw, n, channels)));
                    position += n;
                }
            } finally {
                MemoryUtil.memFree(pcm);
            }
        } finally {
            if (handle != 0) STBVorbis.stb_vorbis_close(handle);
            MemoryUtil.memFree(file);
        }
    }

    /** Erster Frame, der noch gespielt werden muss (kleine Reserve, damit der Aufholmechanismus nur noch feinjustiert). */
    private long skipTarget() {
        return Math.max(0, expectedFrame() - sampleRate / 2);
    }

    /** Mehrkanal zu Mono mischen (Phase 6 verteilt die Kanaele spaeter auf einzelne Lautsprecher). */
    private static short[] downmix(short[] interleaved, int frames, int channels) {
        short[] mono = new short[frames];
        if (channels == 1) {
            System.arraycopy(interleaved, 0, mono, 0, frames);
            return mono;
        }
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) sum += interleaved[i * channels + c];
            mono[i] = (short) (sum / channels);
        }
        return mono;
    }

    // ---- Haupt-Thread (Client-Tick) ----

    void tick() {
        if (finished || stopped) return;
        if (durationMs > 0 && ServerState.serverNow() - startedAtServerMs > durationMs + 500L) {
            stop();
            return;
        }
        if (sampleRate == 0) return; // Decoder ist noch nicht soweit
        if (source < 0 && !createSource()) return;

        recycleBuffers();
        feed();
        updatePositionAndGain();
        int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
        if (state != AL10.AL_PLAYING && !queuedLengths.isEmpty()) AL10.alSourcePlay(source);
    }

    private boolean createSource() {
        if (org.lwjgl.openal.ALC10.alcGetCurrentContext() == 0) return false; // Sound ist aus
        AL10.alGetError();
        source = AL10.alGenSources();
        for (int i = 0; i < AL_BUFFERS; i++) {
            buffers[i] = AL10.alGenBuffers();
            freeBuffers.add(buffers[i]);
        }
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_FALSE);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f); // Dampfung berechnen wir selbst (Reichweite 1-30)
        AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, 1f);
        AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            releaseAl();
            finished = true;
            return false;
        }
        return true;
    }

    private void recycleBuffers() {
        int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int buf = AL10.alSourceUnqueueBuffers(source);
            queuedLengths.pollFirst();
            freeBuffers.add(buf);
        }
    }

    private long queuedFramesAhead() {
        long total = 0;
        for (int len : queuedLengths) total += len;
        if (!queuedLengths.isEmpty() && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
            total -= AL10.alGetSourcei(source, org.lwjgl.openal.AL11.AL_SAMPLE_OFFSET);
        }
        return Math.max(0, total);
    }

    private void feed() {
        long tolerance = (long) sampleRate * TOLERANCE_MS / 1000;
        while (!freeBuffers.isEmpty()) {
            Block block = held != null ? held : decoded.poll();
            held = null;
            if (block == null) return;
            if (block == END) {
                if (queuedLengths.isEmpty()) {
                    stop();
                } else {
                    held = END; // erst abspielen lassen, was noch in der Warteschlange steckt
                }
                return;
            }
            // Wo wuerde dieser Block landen, wenn wir ihn jetzt anhaengen?
            long expectedStart = expectedFrame() + queuedFramesAhead();
            long blockEnd = block.startFrame() + block.mono().length;
            if (blockEnd < expectedStart - tolerance) continue; // zu spaet: verwerfen (aufholen)
            long gap = block.startFrame() - expectedStart;
            if (gap > tolerance) {
                if (gap > (long) sampleRate * MAX_GAP_MS / 1000) { held = block; return; } // noch zu frueh: warten
                queue(new short[(int) gap]); // kurze Stille ueberbrueckt die Luecke
                held = block;
                continue;
            }
            queue(block.mono());
        }
    }

    private void queue(short[] mono) {
        int buf = freeBuffers.poll();
        ShortBuffer data = BufferUtils.createShortBuffer(mono.length);
        data.put(mono).flip();
        AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, data, sampleRate);
        AL10.alSourceQueueBuffers(source, buf);
        queuedLengths.add(mono.length);
    }

    private void updatePositionAndGain() {
        float[] lx = new float[1], ly = new float[1], lz = new float[1];
        AL10.alGetListener3f(AL10.AL_POSITION, lx, ly, lz);
        double dx = x - lx[0], dy = y - ly[0], dz = z - lz[0];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        AL10.alSource3f(source, AL10.AL_POSITION, (float) x, (float) y, (float) z);
        float master = Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.RECORDS);
        AL10.alSourcef(source, AL10.AL_GAIN, (float) (master * volume / 100.0 * attenuation(distance, range)));
    }

    /** 1 nahe an der Quelle, sanft fallend bis 0 an der Reichweite. */
    static double attenuation(double distance, double range) {
        if (distance >= range) return 0;
        double near = Math.min(2.0, range * 0.25);
        if (distance <= near) return 1;
        double t = 1 - (distance - near) / (range - near);
        return t * t;
    }

    private void releaseAl() {
        if (source < 0) return;
        AL10.alSourceStop(source);
        int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) AL10.alSourceUnqueueBuffers(source);
        AL10.alDeleteSources(source);
        for (int b : buffers) if (b != 0) AL10.alDeleteBuffers(b);
        source = -1;
    }

}
