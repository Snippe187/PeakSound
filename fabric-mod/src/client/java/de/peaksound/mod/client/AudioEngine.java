package de.peaksound.mod.client;

import de.peaksound.protocol.Packet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** Verwaltet alle spielenden Jukeboxen des Clients. Alle Methoden laufen auf dem Client-Haupt-Thread. */
public final class AudioEngine {
    private static final Map<Long, JukeboxVoice> voices = new HashMap<>();
    private static int ticks;

    private AudioEngine() {}

    static void start(Packet.PlaybackStart start) {
        long key = JukeboxVoice.key(start.x(), start.y(), start.z());
        JukeboxVoice old = voices.get(key);
        if (old != null) {
            if (old.hash().equals(start.hash()) && old.startedAt() == start.serverTimeMs() - start.offsetMs()) return;
            old.stop();
        }
        JukeboxVoice voice = new JukeboxVoice(start);
        voices.put(key, voice);
        SongLibrary.request(start.hash(), voice::onData);
    }

    static void stop(int x, int y, int z) {
        JukeboxVoice voice = voices.remove(JukeboxVoice.key(x, y, z));
        if (voice != null) voice.stop();
    }

    /** Einmal pro Client-Tick. */
    static void tick() {
        Iterator<JukeboxVoice> it = voices.values().iterator();
        while (it.hasNext()) {
            JukeboxVoice v = it.next();
            v.tick();
            if (v.finished()) it.remove();
        }
        if (++ticks % 20 == 0) SongLibrary.tick();
    }

    static void clear() {
        voices.values().forEach(JukeboxVoice::stop);
        voices.clear();
        SongLibrary.clear();
    }
}
