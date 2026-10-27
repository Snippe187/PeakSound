package de.peaksound.mod.client;

import de.peaksound.audio.AudioException;
import de.peaksound.audio.PcmSource;
import de.peaksound.protocol.AudioFormat;
import de.peaksound.protocol.ChannelLayout;
import de.peaksound.protocol.ChannelMixer;
import de.peaksound.protocol.DjMath;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.SpeakerChannel;
import de.peaksound.protocol.SpeakerKind;
import de.peaksound.protocol.SyncMath;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

/**
 * Ein laufender Song mit allen seinen Schallquellen beim Client: eine Jukebox (Mono-Mix) oder mehrere
 * Lautsprecher (je ein Kanal). Der Song wird einmal in einem Hintergrund-Thread decodiert; jede Schallquelle
 * mischt daraus ihr Signal und speist eine eigene OpenAL-Quelle an ihrer Position, damit Richtung und Dampfung
 * stimmen (echtes 3D, bis 7.1.4).
 *
 * <p>Synchronisation: Jeder Block kennt seine Position im Song. Beim Einreihen wird sie mit der erwarteten
 * Position (aus der Serveruhr) verglichen: zu spaete Bloecke werden verworfen, zu fruehe mit Stille
 * ueberbrueckt. So steigen spaet Hinzukommende richtig ein, und alle Lautsprecher bleiben phasengleich.
 */
final class PlaybackSession {
    /** Ein Stueck des Songs: Position des ersten Frames und alle Quellkanaele interleaved. */
    private record Block(long startFrame, short[] interleaved, int frames) {}

    /** Eine Schallquelle (Jukebox oder Lautsprecher) mit eigener OpenAL-Quelle. */
    private final class Emitter {
        final long key;
        final double x, y, z;
        final int channel; // 0 = Mono-Mix, 1-12 = Lautsprecher-Kanal
        final SpeakerKind kind;
        /** Taschenradio: folgt dem Hoerer, ohne Entfernungsdaempfung. */
        final boolean relative;
        volatile int range;
        volatile int volume;
        /** Ueberblendung (DJ-Pult): Lautstaerke rampt linear von fadeFrom nach volume ueber fadeMs. */
        volatile double fadeFrom;
        volatile long fadeStartMs;
        volatile int fadeMs;
        int source = -1;
        final int[] buffers = new int[AL_BUFFERS];
        final Deque<Integer> free = new ArrayDeque<>();
        final Deque<Integer> queuedLengths = new ArrayDeque<>();
        ChannelMixer mixer;
        int pendingSilence;

        Emitter(Packet.PlaybackStart start) {
            this.key = key(start.x(), start.y(), start.z());
            this.relative = start.x() == Limits.POCKET_COORD;
            this.x = start.x() + 0.5;
            this.y = start.y() + 0.5;
            this.z = start.z() + 0.5;
            this.channel = start.speakerChannel();
            this.kind = SpeakerKind.byOrdinal(start.speakerKind());
            this.range = start.range();
            this.volume = start.volume();
        }
    }

    private static final int AL_BUFFERS = 6;
    private static final int MAX_GAP_MS = 3000;
    private static final int TOLERANCE_MS = 120;
    private static final Block END = new Block(-1, new short[0], 0);

    private final String hash;
    private final long startedAtServerMs;
    private final int durationMs;
    private final Map<Long, Emitter> emitters = new LinkedHashMap<>();
    private final BlockingQueue<Block> decoded = new ArrayBlockingQueue<>(8);
    private volatile boolean stopped;
    private volatile int sampleRate;
    private volatile int sourceChannels;
    private volatile boolean vorbisOrder;
    private Thread decoder;
    private boolean decoderStarted;
    private Block held;
    private boolean finished;

    PlaybackSession(Packet.PlaybackStart start) {
        this.hash = start.hash();
        this.startedAtServerMs = SyncMath.startedAt(start.serverTimeMs(), start.offsetMs());
        this.durationMs = start.durationMs();
    }

    static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static String id(String hash, long startedAtServerMs) { return hash + "@" + startedAtServerMs; }

    String id() { return id(hash, startedAtServerMs); }

    boolean finished() { return finished; }

    /** Diagnose: eine Zeile zu dieser Sitzung. */
    String debug() {
        return hash.substring(0, Math.min(8, hash.length())) + " Quellen=" + emitters.size() + " Rate=" + sampleRate + " Kanaele=" + sourceChannels
                + " Decoder=" + decoderStarted + " beendet=" + finished + " Position=" + (ServerState.serverNow() - startedAtServerMs) / 1000 + "/" + durationMs / 1000 + " s";
    }

    boolean hasEmitters() { return !emitters.isEmpty(); }

    // ---- Schallquellen verwalten (Haupt-Thread) ----

    /** Fuegt eine Schallquelle hinzu oder ersetzt die an derselben Position (z. B. neuer Kanal). */
    void putEmitter(Packet.PlaybackStart start) {
        Emitter old = emitters.remove(key(start.x(), start.y(), start.z()));
        if (old != null) release(old);
        Emitter e = new Emitter(start);
        emitters.put(e.key, e);
    }

    void removeEmitter(long key) {
        Emitter e = emitters.remove(key);
        if (e != null) release(e);
    }

    void updateEmitter(long key, int range, int volume) {
        Emitter e = emitters.get(key);
        if (e != null) {
            e.range = range;
            e.volume = volume;
            e.fadeMs = 0;
        }
    }

    /** Ueberblendet die Lautstaerke einer Schallquelle in {@code durationMs} auf {@code toVolume} (alle Clients starten gemeinsam). */
    void fadeEmitter(long key, int toVolume, int durationMs) {
        Emitter e = emitters.get(key);
        if (e == null) return;
        e.fadeFrom = currentVolume(e);
        e.fadeStartMs = System.currentTimeMillis();
        e.fadeMs = Math.max(0, durationMs);
        e.volume = toVolume;
    }

    private static double currentVolume(Emitter e) {
        int ms = e.fadeMs;
        if (ms <= 0) return e.volume;
        long elapsed = System.currentTimeMillis() - e.fadeStartMs;
        return DjMath.rampPercent(e.fadeFrom, e.volume, elapsed, ms);
    }

    void stop() {
        stopped = true;
        if (decoder != null) decoder.interrupt();
        emitters.values().forEach(this::release);
        emitters.clear();
        finished = true;
    }

    /**
     * Der Song-Strom steht bereit (noch nicht unbedingt komplett geladen): Decoder-Thread starten. Der Decoder blockiert
     * nur, wenn er schneller ist als der Download, die Wiedergabe beginnt also nach den ersten Chunks.
     */
    void onStream(InputStream in) {
        if (stopped || decoderStarted) {
            try { in.close(); } catch (IOException ignored) { /* Strom wird nicht gebraucht */ }
            return;
        }
        decoderStarted = true;
        decoder = Thread.ofPlatform().name("PeakSound-Decoder").daemon(true).start(() -> decode(in));
    }

    private long expectedFrame() {
        return SyncMath.frameAt(SyncMath.positionMs(ServerState.serverNow(), startedAtServerMs), sampleRate);
    }

    // ---- Decoder-Thread ----

    private void decode(InputStream raw) {
        try (BufferedInputStream in = new BufferedInputStream(raw, 1 << 16)) {
            // Format an den ersten Bytes erkennen (wartet auf die ersten Chunks)
            in.mark(32);
            byte[] head = in.readNBytes(16);
            in.reset();
            AudioFormat format = AudioFormat.detect(head).orElseThrow(() -> new AudioException("Format"));
            vorbisOrder = format == AudioFormat.OGG;
            decodeStream(in, format);
            decoded.put(END);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            try { decoded.put(END); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
        }
    }

    private void decodeStream(InputStream in, AudioFormat format) throws Exception {
        try (PcmSource src = PcmSource.open(in, format)) {
            int channels = src.channels();
            int rate = src.sampleRate();
            int blockFrames = Math.max(1024, rate / 4);
            short[] raw = new short[blockFrames * channels];
            sourceChannels = channels;
            sampleRate = rate;
            long skip = skipTarget();
            long position = 0;
            int n;
            while (!stopped && (n = src.read(raw, blockFrames)) > 0) {
                if (position + n > skip) { // Bloecke vor dem Einstiegspunkt ueberspringen (decodieren, aber nicht abspielen)
                    decoded.put(new Block(position, java.util.Arrays.copyOf(raw, n * channels), n));
                }
                position += n;
            }
        }
    }
    /** Erster Frame, der noch gespielt werden muss (kleine Reserve, damit der Aufholmechanismus nur noch feinjustiert). */
    private long skipTarget() {
        return Math.max(0, expectedFrame() - sampleRate / 2);
    }

    // ---- Haupt-Thread (Client-Tick) ----

    void tick() {
        if (finished || stopped) return;
        if (durationMs > 0 && ServerState.serverNow() - startedAtServerMs > durationMs + 500L) {
            stop();
            return;
        }
        if (sampleRate == 0 || emitters.isEmpty()) return; // Decoder ist noch nicht soweit
        if (ALC10.alcGetCurrentContext() == 0) return;      // Sound ist aus

        for (Emitter e : emitters.values()) {
            if (e.source < 0 && !createSource(e)) return;
            recycle(e);
        }
        feed();
        for (Emitter e : emitters.values()) {
            updatePositionAndGain(e);
            if (AL10.alGetSourcei(e.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && !e.queuedLengths.isEmpty()) {
                AL10.alSourcePlay(e.source);
            }
        }
    }

    private boolean createSource(Emitter e) {
        AL10.alGetError();
        e.source = AL10.alGenSources();
        for (int i = 0; i < AL_BUFFERS; i++) {
            e.buffers[i] = AL10.alGenBuffers();
            e.free.add(e.buffers[i]);
        }
        AL10.alSourcei(e.source, AL10.AL_SOURCE_RELATIVE, e.relative ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcef(e.source, AL10.AL_ROLLOFF_FACTOR, 0f); // Dampfung berechnen wir selbst (Reichweite 1-30)
        AL10.alSourcef(e.source, AL10.AL_REFERENCE_DISTANCE, 1f);
        AL10.alSource3f(e.source, AL10.AL_POSITION, e.relative ? 0f : (float) e.x, e.relative ? 0f : (float) e.y, e.relative ? 0f : (float) e.z);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            release(e);
            return false;
        }
        e.mixer = e.channel == 0 ? null : new ChannelMixer(
                ChannelLayout.mapping(sourceChannels, vorbisOrder, SpeakerChannel.values()[e.channel - 1]), sampleRate, e.kind);
        // Kommt eine Quelle mitten im Song dazu: Stille in Hoehe des schon eingereihten Vorlaufs, damit sie phasengleich bleibt
        e.pendingSilence = (int) referenceQueuedAhead(e);
        return true;
    }

    private void recycle(Emitter e) {
        int processed = AL10.alGetSourcei(e.source, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int buf = AL10.alSourceUnqueueBuffers(e.source);
            e.queuedLengths.pollFirst();
            e.free.add(buf);
        }
    }

    private static long queuedAhead(Emitter e) {
        long total = 0;
        for (int len : e.queuedLengths) total += len;
        if (!e.queuedLengths.isEmpty() && AL10.alGetSourcei(e.source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
            total -= AL10.alGetSourcei(e.source, AL11.AL_SAMPLE_OFFSET);
        }
        return Math.max(0, total);
    }

    /** Wie weit die anderen Quellen schon voraus sind (0, wenn es noch keine gibt). */
    private long referenceQueuedAhead(Emitter except) {
        for (Emitter o : emitters.values()) {
            if (o != except && o.source >= 0 && !o.queuedLengths.isEmpty()) return queuedAhead(o);
        }
        return 0;
    }

    private Emitter reference() {
        for (Emitter e : emitters.values()) if (e.source >= 0) return e;
        return null;
    }

    private boolean allHaveFreeBuffer() {
        for (Emitter e : emitters.values()) if (e.free.isEmpty()) return false;
        return true;
    }

    private void feed() {
        Emitter ref = reference();
        if (ref == null) return;
        // Neue Quellen zuerst mit Stille auffuellen (siehe createSource)
        for (Emitter e : emitters.values()) {
            if (e.pendingSilence > 0 && !e.free.isEmpty()) {
                queueMono(e, new short[e.pendingSilence]);
                e.pendingSilence = 0;
            }
        }
        long tolerance = (long) sampleRate * TOLERANCE_MS / 1000;
        while (allHaveFreeBuffer()) {
            Block block = held != null ? held : decoded.poll();
            held = null;
            if (block == null) return;
            if (block == END) {
                boolean drained = true;
                for (Emitter e : emitters.values()) drained &= e.queuedLengths.isEmpty();
                if (drained) stop();
                else held = END; // erst abspielen lassen, was noch in der Warteschlange steckt
                return;
            }
            // Wo wuerde dieser Block landen, wenn wir ihn jetzt anhaengen?
            long expectedStart = expectedFrame() + queuedAhead(ref);
            long blockEnd = block.startFrame() + block.frames();
            if (blockEnd < expectedStart - tolerance) continue; // zu spaet: verwerfen (aufholen)
            long gap = block.startFrame() - expectedStart;
            if (gap > tolerance) {
                if (gap > (long) sampleRate * MAX_GAP_MS / 1000) { held = block; return; } // noch zu frueh: warten
                for (Emitter e : emitters.values()) queueMono(e, new short[(int) gap]); // Stille ueberbrueckt die Luecke
                held = block;
                continue;
            }
            for (Emitter e : emitters.values()) {
                queueMono(e, e.mixer == null
                        ? ChannelMixer.downmix(block.interleaved(), block.frames(), sourceChannels)
                        : e.mixer.process(block.interleaved(), block.frames(), sourceChannels));
            }
        }
    }

    private void queueMono(Emitter e, short[] mono) {
        int buf = e.free.poll();
        ShortBuffer data = BufferUtils.createShortBuffer(mono.length);
        data.put(mono).flip();
        AL10.alBufferData(buf, AL10.AL_FORMAT_MONO16, data, sampleRate);
        AL10.alSourceQueueBuffers(e.source, buf);
        e.queuedLengths.add(mono.length);
    }

    private void updatePositionAndGain(Emitter e) {
        if (e.relative) { // Taschenradio: direkt am Ohr, ohne Entfernung
            float m = Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.RECORDS);
            AL10.alSource3f(e.source, AL10.AL_POSITION, 0f, 0f, 0f);
            AL10.alSourcef(e.source, AL10.AL_GAIN, (float) (m * ClientSettings.volumeFactor() * currentVolume(e) / 100.0));
            return;
        }
        float[] lx = new float[1], ly = new float[1], lz = new float[1];
        AL10.alGetListener3f(AL10.AL_POSITION, lx, ly, lz);
        double dx = e.x - lx[0], dy = e.y - ly[0], dz = e.z - lz[0];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        AL10.alSource3f(e.source, AL10.AL_POSITION, (float) e.x, (float) e.y, (float) e.z);
        float master = Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.RECORDS);
        AL10.alSourcef(e.source, AL10.AL_GAIN, (float) (master * ClientSettings.volumeFactor() * currentVolume(e) / 100.0 * attenuation(distance, e.range)));
    }

    /** 1 nahe an der Quelle, sanft fallend bis 0 an der Reichweite. */
    static double attenuation(double distance, double range) {
        if (distance >= range) return 0;
        double near = Math.min(2.0, range * 0.25);
        if (distance <= near) return 1;
        double t = 1 - (distance - near) / (range - near);
        return t * t;
    }

    private void release(Emitter e) {
        if (e.source < 0) return;
        AL10.alSourceStop(e.source);
        int queued = AL10.alGetSourcei(e.source, AL10.AL_BUFFERS_QUEUED);
        for (int i = 0; i < queued; i++) AL10.alSourceUnqueueBuffers(e.source);
        AL10.alDeleteSources(e.source);
        for (int b : e.buffers) if (b != 0) AL10.alDeleteBuffers(b);
        e.source = -1;
    }
}
