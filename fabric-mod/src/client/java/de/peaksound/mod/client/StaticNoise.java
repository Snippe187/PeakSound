package de.peaksound.mod.client;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.nio.ShortBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;

/**
 * Rauschen an Radio-Empfaengern, die auf keinen Sender eingestellt sind. Ein einziger kurzer Rauschpuffer wird von
 * einer OpenAL-Quelle je Empfaenger in Schleife gespielt (kein Song, kein Download). Staerke und Reichweite kommen
 * vom Server ({@link Packet.RadioStatic}), die Abschwaechung nach Entfernung berechnet der Client wie bei Lautsprechern.
 */
final class StaticNoise {
    private static final int SAMPLE_RATE = 22050;
    private static final double NOISE_GAIN = 0.35;

    private static final class Entry {
        final double x, y, z;
        final boolean relative;
        int range, volume, level;
        int source = -1;

        Entry(Packet.RadioStatic p) {
            relative = p.x() == Limits.POCKET_COORD;
            x = p.x() + 0.5;
            y = p.y() + 0.5;
            z = p.z() + 0.5;
            update(p);
        }

        void update(Packet.RadioStatic p) {
            range = p.range();
            volume = p.volume();
            level = p.level();
        }
    }

    private static final Map<Long, Entry> entries = new HashMap<>();
    private static int buffer;

    private StaticNoise() {}

    static void onPacket(Packet.RadioStatic p) {
        long key = PlaybackSession.key(p.x(), p.y(), p.z());
        if (p.level() == 0) {
            Entry e = entries.remove(key);
            if (e != null) release(e);
            return;
        }
        Entry e = entries.get(key);
        if (e == null) entries.put(key, new Entry(p));
        else e.update(p);
    }

    /** Einmal pro Client-Tick. */
    static void tick() {
        if (entries.isEmpty()) return;
        if (ALC10.alcGetCurrentContext() == 0) return;
        float[] lx = new float[1], ly = new float[1], lz = new float[1];
        AL10.alGetListener3f(AL10.AL_POSITION, lx, ly, lz);
        float master = Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.RECORDS);
        for (Entry e : entries.values()) {
            if (e.source < 0 && !create(e)) continue;
            double dx = e.x - lx[0], dy = e.y - ly[0], dz = e.z - lz[0];
            double distance = e.relative ? 0 : Math.sqrt(dx * dx + dy * dy + dz * dz);
            double gain = master * ClientSettings.volumeFactor() * (e.volume / 100.0) * (e.level / 100.0) * NOISE_GAIN
                    * PlaybackSession.attenuation(distance, e.range);
            AL10.alSourcef(e.source, AL10.AL_GAIN, (float) gain);
            if (AL10.alGetSourcei(e.source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(e.source);
        }
    }

    private static boolean create(Entry e) {
        AL10.alGetError();
        if (buffer == 0) {
            buffer = AL10.alGenBuffers();
            Random random = new Random(1234);
            short[] pcm = new short[SAMPLE_RATE]; // 1 Sekunde, leicht tiefpassgefiltert: weiches "Sssch" statt hartem Knistern
            double previous = 0;
            for (int i = 0; i < pcm.length; i++) {
                double white = random.nextDouble() * 2 - 1;
                previous = previous * 0.55 + white * 0.45;
                pcm[i] = (short) (previous * 20000);
            }
            ShortBuffer data = BufferUtils.createShortBuffer(pcm.length);
            data.put(pcm).flip();
            AL10.alBufferData(buffer, AL10.AL_FORMAT_MONO16, data, SAMPLE_RATE);
        }
        e.source = AL10.alGenSources();
        AL10.alSourcei(e.source, AL10.AL_BUFFER, buffer);
        AL10.alSourcei(e.source, AL10.AL_LOOPING, AL10.AL_TRUE);
        AL10.alSourcei(e.source, AL10.AL_SOURCE_RELATIVE, e.relative ? AL10.AL_TRUE : AL10.AL_FALSE);
        AL10.alSourcef(e.source, AL10.AL_ROLLOFF_FACTOR, 0f);
        AL10.alSourcef(e.source, AL10.AL_REFERENCE_DISTANCE, 1f);
        AL10.alSource3f(e.source, AL10.AL_POSITION, e.relative ? 0f : (float) e.x, e.relative ? 0f : (float) e.y, e.relative ? 0f : (float) e.z);
        if (AL10.alGetError() != AL10.AL_NO_ERROR) {
            release(e);
            return false;
        }
        return true;
    }

    private static void release(Entry e) {
        if (e.source < 0) return;
        AL10.alSourceStop(e.source);
        AL10.alDeleteSources(e.source);
        e.source = -1;
    }

    /** Alles beenden (Verbindungswechsel, Spiel beenden). */
    static void clear() {
        Iterator<Entry> it = entries.values().iterator();
        while (it.hasNext()) {
            release(it.next());
            it.remove();
        }
        if (buffer != 0 && ALC10.alcGetCurrentContext() != 0) AL10.alDeleteBuffers(buffer);
        buffer = 0;
    }
}
