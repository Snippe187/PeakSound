package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.audio.AudioException;
import de.peaksound.audio.AudioInfo;
import de.peaksound.audio.AudioInspector;
import de.peaksound.audio.Transcoder;
import de.peaksound.protocol.AudioFormat;
import de.peaksound.protocol.ChunkAssembler;
import de.peaksound.protocol.LimitLogic;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.MalformedPacketException;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.Packet.UploadReject.Reason;
import de.peaksound.protocol.PeakItemType;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Nimmt Uploads entgegen und validiert alles serverseitig: Ticket, Platte in der Hand, Rate-Limit,
 * Quota, Speicher, Chunk-Reihenfolge, Format, Laenge. Decodierung und Datei-I/O laufen asynchron.
 */
final class UploadService {
    private static final long TICKET_LIFETIME_MS = 5 * 60_000L;

    private record Ticket(UUID player, long expiresAt) {}

    private static final class Session {
        final int id;
        final UUID player;
        final String fallbackTitle;
        final ChunkAssembler assembler;
        /** Songlimit des Spielers (auf dem Hauptthread beim Start bestimmt; die Verarbeitung laeuft asynchron). */
        final int maxSongs;
        int lastAcked;

        Session(int id, UUID player, String fallbackTitle, ChunkAssembler assembler, int maxSongs) {
            this.maxSongs = maxSongs;
            this.id = id;
            this.player = player;
            this.fallbackTitle = fallbackTitle;
            this.assembler = assembler;
        }
    }

    /** Ergebnis der asynchronen Verarbeitung. */
    private record Outcome(Reason reason, int arg, Song song, boolean added) {
        static Outcome fail(Reason reason, int arg) { return new Outcome(reason, arg, null, false); }
    }

    private final JavaPlugin plugin;
    private final PeakItems items;
    private final SongStore store;
    private final AnalysisStore analysis;
    private final Transcoder transcoder;
    private final AdminLimits limits;
    private final SecureRandom random = new SecureRandom();
    private final Map<Integer, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpload = new ConcurrentHashMap<>();
    private final AtomicInteger processing = new AtomicInteger();
    private int nextUploadId = 1;

    UploadService(JavaPlugin plugin, PeakItems items, SongStore store, AnalysisStore analysis, Transcoder transcoder,
                  AdminLimits limits) {
        this.plugin = plugin;
        this.limits = limits;
        this.items = items;
        this.store = store;
        this.analysis = analysis;
        this.transcoder = transcoder;
    }

    /** Gibt dem Spieler ein Ticket und oeffnet sein Upload-Fenster. Ein Spieler hat hoechstens ein Ticket. */
    void openUpload(Player player) {
        if (!Permissions.check(player, "peaksound.upload", "Du darfst keine Songs hochladen.", "You may not upload songs.")) return;
        tickets.values().removeIf(t -> t.player().equals(player.getUniqueId()) || t.expiresAt() < System.currentTimeMillis());
        int ticket = random.nextInt(Integer.MAX_VALUE - 1) + 1;
        tickets.put(ticket, new Ticket(player.getUniqueId(), System.currentTimeMillis() + TICKET_LIFETIME_MS));
        Net.send(plugin, player, new Packet.OpenUpload(ticket));
    }

    /** Hat der Spieler ein gueltiges Upload-Ticket (also gerade das Upload-Fenster mit einer leeren Platte offen)? */
    boolean hasTicket(Player player) {
        long now = System.currentTimeMillis();
        return tickets.values().stream().anyMatch(t -> t.player().equals(player.getUniqueId()) && t.expiresAt() >= now);
    }

    void onStart(Player player, Packet.UploadStart start) {
        UUID id = player.getUniqueId();
        Ticket ticket = tickets.remove(start.ticket());
        boolean holdingBlank = items.typeOf(player.getInventory().getItemInMainHand())
                .filter(t -> t == PeakItemType.BLANK_RECORD).isPresent();
        if (ticket == null || !ticket.player().equals(id) || ticket.expiresAt() < System.currentTimeMillis() || !holdingBlank) {
            reject(player, 0, Reason.NO_TICKET, 0);
            return;
        }
        if (sessions.containsKey(id) || processing.get() >= Ctx.config().getInt("upload.max-concurrent", 3)) {
            reject(player, 0, Reason.BUSY, 0);
            return;
        }
        long cooldownMs = Ctx.config().getLong("upload.cooldown-seconds", 60) * 1000L;
        long since = System.currentTimeMillis() - lastUpload.getOrDefault(id, 0L);
        if (cooldownMs > 0 && since < cooldownMs) {
            reject(player, 0, Reason.RATE_LIMIT, (int) ((cooldownMs - since + 999) / 1000));
            return;
        }
        int maxSongs = limits.maxSongsFor(player);
        if (!LimitLogic.mayUpload(store.countByUploader(id), maxSongs)) {
            reject(player, 0, Reason.QUOTA_REACHED, maxSongs);
            return;
        }
        if (!LimitLogic.fitsStorage(store.totalBytes(), 1, maxStorageBytes())) {
            reject(player, 0, Reason.STORAGE_FULL, 0);
            return;
        }
        int uploadId = nextUploadId++;
        String fallback = AudioInspector.clean(start.fallbackTitle());
        sessions.put(id, new Session(uploadId, id, fallback, new ChunkAssembler(start.fileSize(), Limits.UPLOAD_CHUNK_BYTES), maxSongs));
        lastUpload.put(id, System.currentTimeMillis());
        Net.send(plugin, player, new Packet.UploadAccept(uploadId, Limits.UPLOAD_CHUNK_BYTES, Limits.UPLOAD_WINDOW));
    }

    void onChunk(Player player, Packet.UploadChunk chunk) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null || s.id != chunk.uploadId()) return; // veraltet oder erfunden: ignorieren
        try {
            s.assembler.accept(chunk.index(), chunk.data());
        } catch (MalformedPacketException e) {
            sessions.remove(player.getUniqueId());
            reject(player, s.id, Reason.CORRUPT, 0);
            return;
        }
        int next = s.assembler.nextIndex();
        if (s.assembler.complete()) {
            Net.send(plugin, player, new Packet.UploadAck(s.id, next));
            sessions.remove(player.getUniqueId());
            startProcessing(player, s);
        } else if (next - s.lastAcked >= Limits.UPLOAD_WINDOW / 2) {
            s.lastAcked = next;
            Net.send(plugin, player, new Packet.UploadAck(s.id, next));
        }
    }

    void onQuit(Player player) {
        sessions.remove(player.getUniqueId());
        tickets.values().removeIf(t -> t.player().equals(player.getUniqueId()));
    }

    private long maxStorageBytes() {
        return limits.maxStorageBytes();
    }

    private void reject(Player player, int uploadId, Reason reason, int arg) {
        Net.send(plugin, player, new Packet.UploadReject(uploadId, reason, arg));
        player.sendMessage(Messages.rejected(player, reason, arg));
    }

    private void startProcessing(Player player, Session s) {
        processing.incrementAndGet();
        UUID uuid = player.getUniqueId();
        String uploaderName = player.getName();
        byte[] data = s.assembler.result();
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            Outcome outcome;
            try {
                outcome = process(data, s.fallbackTitle, uuid, uploaderName, s.maxSongs);
            } catch (RuntimeException | IOException e) {
                Ctx.logger().log(Level.SEVERE, "Upload-Verarbeitung fehlgeschlagen", e);
                outcome = Outcome.fail(Reason.INTERNAL, 0);
            }
            Outcome result = outcome;
            Bukkit.getScheduler().runTask(plugin, () -> {
                processing.decrementAndGet();
                Player online = Bukkit.getPlayer(uuid);
                if (online != null) finish(online, s.id, result);
            });
        });
    }

    /** Laeuft asynchron. */
    /** Hoechste Songlaenge in Sekunden: Config upload.max-song-seconds (Standard 240, erlaubt 30 bis 3600). */
    static int maxSongSeconds(JavaPlugin plugin) {
        return Math.max(30, Math.min(3600, Ctx.config().getInt("upload.max-song-seconds", Limits.MAX_SONG_SECONDS)));
    }

    private Outcome process(byte[] data, String fallbackTitle, UUID uploader, String uploaderName, int maxSongs) throws IOException {
        int maxSeconds = maxSongSeconds(plugin);
        int maxMs = maxSeconds * 1000;
        AudioFormat format = AudioFormat.detect(data).orElse(null);
        if (format == null) return Outcome.fail(Reason.UNSUPPORTED_FORMAT, 0);
        AudioInfo info;
        try {
            info = AudioInspector.inspect(data);
        } catch (AudioException e) {
            return Outcome.fail(Reason.CORRUPT, 0);
        }
        if (info.durationMs() > maxMs + 1000) return Outcome.fail(Reason.TOO_LONG, maxSeconds);
        if (info.bitsPerSample() > 24 && format == AudioFormat.FLAC) return Outcome.fail(Reason.UNSUPPORTED_FORMAT, 0);

        Transcoder.Result stored;
        AudioInfo storedInfo;
        int exactMs;
        try {
            stored = transcoder.transcode(data, format);
            storedInfo = stored.data() == data ? info : AudioInspector.inspect(stored.data());
            // Header-Angaben koennen luegen (z. B. MP3 ohne VBR-Kopf): die echte Laenge durch Decodieren messen
            exactMs = AudioInspector.measureExactMs(stored.data(), stored.format());
        } catch (AudioException e) {
            return Outcome.fail(Reason.CORRUPT, 0);
        }
        if (exactMs > maxMs + 1000) return Outcome.fail(Reason.TOO_LONG, maxSeconds);

        String hash = sha256(stored.data());
        String title = !info.title().isBlank() ? info.title()
                : !fallbackTitle.isBlank() ? fallbackTitle : "Unknown";
        int channels = storedInfo.channels();
        Song.Status status = channels > 2 ? Song.Status.PENDING : Song.Status.OK;
        Song song = new Song(hash, title, info.artist(), uploader, uploaderName, channels, exactMs,
                stored.data().length, stored.format().extension(), status, System.currentTimeMillis(),
                info.cover().length > 0);

        SongStore.AddResult added = store.add(song, stored.data(), info.cover(), info.lyrics(),
                maxSongs, maxStorageBytes());
        return switch (added) {
            case ADDED -> {
                analysis.store(song, stored.data(), stored.format()); // Takt und Frequenzbaender fuer Partikel/Equalizer
                yield new Outcome(null, 0, song, true);
            }
            case ALREADY_PRESENT -> new Outcome(null, 0, store.get(hash).orElse(song), false);
            case QUOTA_REACHED -> Outcome.fail(Reason.QUOTA_REACHED, maxSongs);
            case STORAGE_FULL -> Outcome.fail(Reason.STORAGE_FULL, 0);
        };
    }

    /** Laeuft auf dem Haupt-Thread. */
    private void finish(Player player, int uploadId, Outcome outcome) {
        if (outcome.reason() != null) {
            reject(player, uploadId, outcome.reason(), outcome.arg());
            return;
        }
        Song song = outcome.song();
        boolean pending = song.status() == Song.Status.PENDING;
        Net.send(plugin, player, new Packet.UploadResult(uploadId, pending, song.hash(), song.title(), song.artist(),
                song.durationMs(), song.channels()));
        if (pending) {
            player.sendMessage(Messages.needsApproval(player));
            return;
        }
        if (!writeRecord(player, song)) {
            player.sendMessage(Messages.error(player, "Du hast keine leere Platte mehr. Der Song bleibt gespeichert.",
                    "You no longer have a blank record. The song stays stored."));
            return;
        }
        player.sendMessage(Messages.uploaded(player, song.title()));
    }

    /** Verbraucht eine leere Platte (bevorzugt in der Hand) und gibt die beschriebene Platte. */
    private boolean writeRecord(Player player, Song song) {
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        int slot = items.typeOf(hand).filter(t -> t == PeakItemType.BLANK_RECORD).isPresent() ? inv.getHeldItemSlot() : -1;
        if (slot < 0) {
            for (int i = 0; i < inv.getSize(); i++) {
                if (items.typeOf(inv.getItem(i)).filter(t -> t == PeakItemType.BLANK_RECORD).isPresent()) {
                    slot = i;
                    break;
                }
            }
        }
        if (slot < 0) return false;
        ItemStack blank = inv.getItem(slot);
        blank.setAmount(blank.getAmount() - 1);
        inv.setItem(slot, blank.getAmount() <= 0 ? null : blank);
        ItemStack written = items.createWritten(new PeakItems.Written(song.hash(), song.title(), song.artist(), song.uploaderName()));
        inv.addItem(written).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        return true;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
