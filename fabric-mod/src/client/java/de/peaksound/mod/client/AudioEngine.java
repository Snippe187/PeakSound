package de.peaksound.mod.client;

import de.peaksound.protocol.Packet;
import de.peaksound.protocol.SyncMath;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Verwaltet alle laufenden Songs des Clients. Schallquellen (Jukeboxen, Lautsprecher), die denselben Song zur
 * selben Startzeit spielen, teilen sich eine {@link PlaybackSession} (ein Decoder, viele OpenAL-Quellen).
 * Alle Methoden laufen auf dem Client-Haupt-Thread.
 */
public final class AudioEngine {
    private static final Map<String, PlaybackSession> sessions = new HashMap<>();
    private static final Map<Long, PlaybackSession> byKey = new HashMap<>();
    private static int ticks;

    private AudioEngine() {}

    static void start(Packet.PlaybackStart start) {
        long key = PlaybackSession.key(start.x(), start.y(), start.z());
        String id = PlaybackSession.id(start.hash(), SyncMath.startedAt(start.serverTimeMs(), start.offsetMs()));
        PlaybackSession session = sessions.get(id);
        if (session == null) {
            session = new PlaybackSession(start);
            sessions.put(id, session);
            SongLibrary.request(start.hash(), session::onData);
        }
        PlaybackSession previous = byKey.get(key);
        if (previous != null && previous != session) detach(previous, key); // diese Quelle spielt jetzt einen anderen Song
        session.putEmitter(start);
        byKey.put(key, session);
    }

    static void update(Packet.PlaybackUpdate update) {
        long key = PlaybackSession.key(update.x(), update.y(), update.z());
        PlaybackSession session = byKey.get(key);
        if (session != null) session.updateEmitter(key, update.range(), update.volume());
    }

    static void stop(int x, int y, int z) {
        long key = PlaybackSession.key(x, y, z);
        PlaybackSession session = byKey.get(key);
        if (session != null) detach(session, key);
    }

    /** Nimmt eine Quelle aus der Sitzung; hat die Sitzung keine mehr, endet sie. */
    private static void detach(PlaybackSession session, long key) {
        session.removeEmitter(key);
        byKey.remove(key, session);
        if (!session.hasEmitters()) {
            session.stop();
            sessions.remove(session.id());
        }
    }

    /** Einmal pro Client-Tick. */
    static void tick() {
        Iterator<PlaybackSession> it = sessions.values().iterator();
        while (it.hasNext()) {
            PlaybackSession s = it.next();
            s.tick();
            if (s.finished()) {
                it.remove();
                byKey.values().removeIf(v -> v == s);
            }
        }
        if (++ticks % 20 == 0) SongLibrary.tick();
    }

    static void clear() {
        sessions.values().forEach(PlaybackSession::stop);
        sessions.clear();
        byKey.clear();
        SongLibrary.clear();
    }
}
