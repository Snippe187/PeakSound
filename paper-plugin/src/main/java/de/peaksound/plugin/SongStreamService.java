package de.peaksound.plugin;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Liefert Songs chunkweise an Clients. Der Client fragt jeden Chunk einzeln an (mit mehreren Anfragen
 * gleichzeitig), dadurch bestimmt er das Tempo und der Server baut keine Warteschlangen auf.
 */
final class SongStreamService {
    /** Maximale Chunk-Anfragen pro Spieler und Sekunde (4 MiB/s bei 64-KiB-Chunks). */
    private static final int MAX_REQUESTS_PER_SECOND = 64;

    private record Bucket(long windowStart, int count) {}

    private final PeakSoundPlugin plugin;
    private final SongStore store;
    private final Map<UUID, Bucket> buckets = new ConcurrentHashMap<>();
    /** Zuletzt benutzte Songs im Arbeitsspeicher (LRU, hoechstens {@link #MAX_CACHE_BYTES}). */
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;
    private final java.util.LinkedHashMap<String, byte[]> cache = new java.util.LinkedHashMap<>(16, 0.75f, true);
    private long cacheBytes;

    SongStreamService(PeakSoundPlugin plugin, SongStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    void onRequest(Player player, Packet.SongRequest request) {
        if (!allow(player.getUniqueId())) return; // zu schnell: still verwerfen, der Client versucht es erneut
        String hash = request.hash();
        Song song = SongStore.validHash(hash) ? store.get(hash).orElse(null) : null;
        // Gesperrte (PENDING) Songs sind nur fuer die Verwaltung gedacht (Phase 7), nie fuer normale Spieler
        if (song == null || (song.status() != Song.Status.OK && !Permissions.admin(player))) {
            Net.send(plugin, player, new Packet.SongUnavailable(hash.length() <= Limits.HASH_LENGTH ? hash : ""));
            return;
        }
        int total = Limits.chunkCount(song.sizeBytes(), Limits.DOWNLOAD_CHUNK_BYTES);
        if (request.index() >= total) return;
        UUID uuid = player.getUniqueId();
        // Datei-Zugriff abseits des Haupt-Threads; haeufig gespielte Songs liegen im Arbeitsspeicher (kein Festplattenzugriff je Chunk)
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            byte[] data;
            try {
                byte[] whole = songBytes(song);
                int offset = request.index() * Limits.DOWNLOAD_CHUNK_BYTES;
                int len = Math.min(Limits.DOWNLOAD_CHUNK_BYTES, whole.length - offset);
                if (len <= 0) return;
                data = java.util.Arrays.copyOfRange(whole, offset, offset + len);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Song konnte nicht gelesen werden: " + hash, e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) Net.send(plugin, p, new Packet.SongUnavailable(hash));
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) Net.send(plugin, p, new Packet.SongChunk(hash, request.index(), total, data));
            });
        });
    }

    /** Die ganze Datei, aus dem LRU-Cache oder von der Platte (dann wird sie zwischengespeichert). */
    private byte[] songBytes(Song song) throws IOException {
        synchronized (cache) {
            byte[] cached = cache.get(song.hash());
            if (cached != null) return cached;
        }
        byte[] data = java.nio.file.Files.readAllBytes(store.audioPath(song));
        synchronized (cache) {
            cache.put(song.hash(), data);
            cacheBytes += data.length;
            var it = cache.entrySet().iterator();
            while (cacheBytes > MAX_CACHE_BYTES && it.hasNext()) { // aelteste zuerst entfernen, den frischen Eintrag behalten
                var e = it.next();
                if (e.getValue() == data) continue;
                cacheBytes -= e.getValue().length;
                it.remove();
            }
        }
        return data;
    }
    /** Cover-Bild fuer den Platten-Tooltip. Leer, wenn der Song keins hat. */
    void onCover(Player player, Packet.CoverRequest request) {
        if (!allow(player.getUniqueId()) || !SongStore.validHash(request.hash())) return;
        Song song = store.get(request.hash()).orElse(null);
        if (song == null || (song.status() != Song.Status.OK && !Permissions.admin(player))) return;
        UUID uuid = player.getUniqueId();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            byte[] image = new byte[0];
            if (song.hasCover()) {
                try {
                    byte[] raw = java.nio.file.Files.readAllBytes(store.coverPath(song.hash()));
                    if (raw.length <= Limits.MAX_COVER_BYTES) image = raw;
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "Cover nicht lesbar: " + song.hash(), e);
                }
            }
            byte[] result = image;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) Net.send(plugin, p, new Packet.CoverData(song.hash(), result));
            });
        });
    }

    /** Songtext fuer die Anzeige. "" , wenn der Song keinen hat. */
    void onLyrics(Player player, Packet.LyricsRequest request) {
        if (!allow(player.getUniqueId()) || !SongStore.validHash(request.hash())) return;
        Song song = store.get(request.hash()).orElse(null);
        if (song == null || (song.status() != Song.Status.OK && !Permissions.admin(player))) return;
        UUID uuid = player.getUniqueId();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            String text = "";
            try {
                java.nio.file.Path file = store.lyricsPath(song.hash());
                if (java.nio.file.Files.exists(file)) text = java.nio.file.Files.readString(file);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Songtext nicht lesbar: " + song.hash(), e);
            }
            String result = text.length() > Limits.MAX_LYRICS_CHARS ? text.substring(0, Limits.MAX_LYRICS_CHARS) : text;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) Net.send(plugin, p, new Packet.LyricsData(song.hash(), result));
            });
        });
    }

    void onQuit(Player player) {
        buckets.remove(player.getUniqueId());
    }

    private boolean allow(UUID id) {
        long now = System.currentTimeMillis();
        Bucket b = buckets.merge(id, new Bucket(now, 1),
                (old, fresh) -> now - old.windowStart() >= 1000 ? fresh : new Bucket(old.windowStart(), old.count() + 1));
        return b.count() <= MAX_REQUESTS_PER_SECOND;
    }
}
