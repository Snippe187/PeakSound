package de.peaksound.plugin;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
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
        // Datei-Zugriff abseits des Haupt-Threads
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            byte[] data;
            try (FileChannel ch = FileChannel.open(store.audioPath(song), StandardOpenOption.READ)) {
                long offset = (long) request.index() * Limits.DOWNLOAD_CHUNK_BYTES;
                int len = (int) Math.min(Limits.DOWNLOAD_CHUNK_BYTES, song.sizeBytes() - offset);
                ByteBuffer buf = ByteBuffer.allocate(len);
                while (buf.hasRemaining() && ch.read(buf, offset + buf.position()) >= 0) { /* weiterlesen */ }
                data = buf.array();
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
