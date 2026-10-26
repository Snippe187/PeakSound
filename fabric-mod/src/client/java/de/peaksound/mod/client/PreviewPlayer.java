package de.peaksound.mod.client;

import de.peaksound.audio.AudioException;
import de.peaksound.audio.PcmSource;
import de.peaksound.protocol.AudioFormat;
import de.peaksound.protocol.ChannelMixer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * Vorhoeren in der Song-Verwaltung: spielt einen Song (auch eine gesperrte Mehrkanal-Datei) als Mono-Mix direkt
 * beim Hoerer, ohne Raumklang. Der Song wird vollstaendig decodiert (hoechstens 4,5 Minuten), das ist fuer
 * eine Vorschau schnell genug und braucht keinen Streaming-Code.
 */
final class PreviewPlayer {
    private static final int MAX_SECONDS = 270;

    private record Mono(short[] data, int rate) {}

    private static String playingHash;
    private static String loadingHash;
    private static int source = -1;
    private static int buffer = -1;

    private PreviewPlayer() {}

    static boolean isActive(String hash) {
        return hash.equals(playingHash) || hash.equals(loadingHash);
    }

    /** Startet die Vorschau (laedt den Song bei Bedarf). Eine laufende Vorschau wird ersetzt. */
    static void play(String hash) {
        stop();
        loadingHash = hash;
        SongLibrary.request(hash, bytes -> Thread.ofPlatform().daemon(true).name("PeakSound-Preview").start(() -> {
            try {
                Mono mono = decode(bytes);
                Minecraft.getInstance().execute(() -> {
                    if (hash.equals(loadingHash)) start(hash, mono);
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> { if (hash.equals(loadingHash)) loadingHash = null; });
            }
        }));
    }

    static void stop() {
        loadingHash = null;
        playingHash = null;
        if (source >= 0) {
            AL10.alSourceStop(source);
            AL10.alDeleteSources(source);
            source = -1;
        }
        if (buffer >= 0) {
            AL10.alDeleteBuffers(buffer);
            buffer = -1;
        }
    }

    /** Einmal pro Client-Tick: beendet die Vorschau, wenn der Song zu Ende ist, und aktualisiert die Lautstaerke. */
    static void tick() {
        if (source < 0) return;
        if (AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
            stop();
            return;
        }
        AL10.alSourcef(source, AL10.AL_GAIN, Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.RECORDS));
    }

    private static void start(String hash, Mono mono) {
        if (ALC10.alcGetCurrentContext() == 0) { // Sound ist aus
            loadingHash = null;
            return;
        }
        AL10.alGetError();
        buffer = AL10.alGenBuffers();
        ShortBuffer data = BufferUtils.createShortBuffer(mono.data().length);
        data.put(mono.data()).flip();
        AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, mono.rate());
        source = AL10.alGenSources();
        AL10.alSourcei(source, AL10.AL_BUFFER, buffer);
        AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE); // direkt am Ohr, ohne Entfernung
        AL10.alSource3f(source, AL10.AL_POSITION, 0f, 0f, 0f);
        AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0f);
        AL10.alSourcef(source, AL10.AL_GAIN, Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.RECORDS));
        AL10.alSourcePlay(source);
        loadingHash = null;
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            stop();
            return;
        }
        playingHash = hash;
    }

    // ---- Decodieren (Hintergrund-Thread) ----

    private static Mono decode(byte[] data) throws Exception {
        AudioFormat format = AudioFormat.detect(data).orElseThrow(() -> new AudioException("Format"));
        return format == AudioFormat.OGG ? decodeVorbis(data) : decodePcm(data, format);
    }

    private static Mono decodePcm(byte[] data, AudioFormat format) throws Exception {
        try (PcmSource src = PcmSource.open(data, format)) {
            int channels = src.channels();
            int rate = src.sampleRate();
            int maxFrames = rate * MAX_SECONDS;
            short[] out = new short[Math.min(maxFrames, rate * 60)];
            int total = 0;
            short[] raw = new short[4096 * channels];
            int n;
            while (total < maxFrames && (n = src.read(raw, 4096)) > 0) {
                short[] mono = ChannelMixer.downmix(raw, n, channels);
                if (total + n > out.length) out = java.util.Arrays.copyOf(out, Math.min(maxFrames, Math.max(out.length * 2, total + n)));
                int take = Math.min(n, out.length - total);
                System.arraycopy(mono, 0, out, total, take);
                total += take;
            }
            return new Mono(java.util.Arrays.copyOf(out, total), rate);
        }
    }

    private static Mono decodeVorbis(byte[] data) throws Exception {
        ByteBuffer file = MemoryUtil.memAlloc(data.length).put(data).flip();
        long handle = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer error = stack.mallocInt(1);
            handle = STBVorbis.stb_vorbis_open_memory(file, error, null);
            if (handle == 0) throw new AudioException("Ogg Vorbis nicht lesbar");
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            STBVorbis.stb_vorbis_get_info(handle, info);
            int channels = info.channels();
            int rate = info.sample_rate();
            int maxFrames = rate * MAX_SECONDS;
            short[] out = new short[Math.min(maxFrames, rate * 60)];
            int total = 0;
            ShortBuffer pcm = MemoryUtil.memAllocShort(4096 * channels);
            try {
                int n;
                while (total < maxFrames && (n = STBVorbis.stb_vorbis_get_samples_short_interleaved(handle, channels, pcm)) > 0) {
                    short[] raw = new short[n * channels];
                    pcm.get(0, raw);
                    short[] mono = ChannelMixer.downmix(raw, n, channels);
                    if (total + n > out.length) out = java.util.Arrays.copyOf(out, Math.min(maxFrames, Math.max(out.length * 2, total + n)));
                    int take = Math.min(n, out.length - total);
                    System.arraycopy(mono, 0, out, total, take);
                    total += take;
                }
            } finally {
                MemoryUtil.memFree(pcm);
            }
            return new Mono(java.util.Arrays.copyOf(out, total), rate);
        } finally {
            if (handle != 0) STBVorbis.stb_vorbis_close(handle);
            MemoryUtil.memFree(file);
        }
    }
}
