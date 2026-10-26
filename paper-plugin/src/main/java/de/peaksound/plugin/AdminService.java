package de.peaksound.plugin;

import de.peaksound.protocol.LimitLogic;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Song-Verwaltung fuer Operatoren: alle Songs ansehen, loeschen, gesperrte Mehrkanal-Dateien freigeben oder
 * ablehnen, Upload-Limits einstellen. Jede Anfrage wird serverseitig erneut auf Operator-Rechte geprueft;
 * ein manipulierter Client kann hier nichts bewirken. Jede Aktion landet im Server-Log.
 */
final class AdminService {
    /** Mindestabstand zwischen zwei Anfragen eines Spielers. */
    private static final long MIN_INTERVAL_MS = 100;

    private final PeakSoundPlugin plugin;
    private final SongStore store;
    private final AdminLimits limits;
    private final PlaybackManager playback;
    private final AnalysisStore analysis;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    AdminService(PeakSoundPlugin plugin, SongStore store, AdminLimits limits, PlaybackManager playback, AnalysisStore analysis) {
        this.plugin = plugin;
        this.store = store;
        this.limits = limits;
        this.playback = playback;
        this.analysis = analysis;
    }

    private boolean allowed(Player player) {
        if (!Permissions.admin(player)) {
            plugin.getLogger().fine(player.getName() + " hat versucht, die Song-Verwaltung ohne Rechte zu nutzen");
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastRequest.put(player.getUniqueId(), now);
        return last == null || now - last >= MIN_INTERVAL_MS;
    }

    void onListRequest(Player player) {
        if (allowed(player)) send(player);
    }

    void onAction(Player player, Packet.AdminAction action) {
        if (!allowed(player)) return;
        switch (action.action()) {
            case DELETE_SONG -> removeSong(player, action.hash(), false);
            case REJECT_SONG -> removeSong(player, action.hash(), true);
            case APPROVE_SONG -> approve(player, action.hash());
            case SET_PLAYER_LIMIT -> {
                UUID target = new UUID(action.uuidHigh(), action.uuidLow());
                limits.setPlayerLimit(target, action.value());
                log(player, "Songlimit von " + target + " = " + (action.value() == LimitLogic.USE_DEFAULT ? "Standard" : action.value()));
            }
            case SET_DEFAULT_LIMIT -> {
                if (action.value() >= 0) {
                    limits.setDefaultMaxSongs(action.value());
                    log(player, "Standard-Songlimit = " + limits.defaultMaxSongs());
                }
            }
            case SET_STORAGE_MB -> {
                if (action.value() >= 0) {
                    limits.setStorageMb(action.value());
                    log(player, "Speicher-Limit = " + action.value() + " MB");
                }
            }
        }
        if (action.action() != Packet.AdminAction.Action.DELETE_SONG && action.action() != Packet.AdminAction.Action.REJECT_SONG) {
            send(player); // Loeschen antwortet erst, wenn die Dateien weg sind
        }
    }

    private void log(Player admin, String what) {
        plugin.getLogger().info("[Admin] " + admin.getName() + ": " + what);
    }

    private void approve(Player admin, String hash) {
        if (!SongStore.validHash(hash)) return;
        Song song = store.get(hash).orElse(null);
        if (song == null || song.status() != Song.Status.PENDING) return;
        store.setStatus(hash, Song.Status.OK);
        log(admin, "Freigabe von \"" + song.title() + "\" (" + hash.substring(0, 8) + ") von " + song.uploaderName());
        Player uploader = Bukkit.getPlayer(song.uploader());
        if (uploader != null) {
            uploader.sendMessage(Messages.info(uploader,
                    "Deine Datei \"" + song.title() + "\" wurde freigegeben. Lade sie erneut hoch, um eine Platte zu bekommen.",
                    "Your file \"" + song.title() + "\" was approved. Upload it again to get a record."));
        }
    }

    /** Loescht den Song (Dateien asynchron), stoppt laufende Wiedergabe und benachrichtigt den Uploader. */
    private void removeSong(Player admin, String hash, boolean rejected) {
        if (!SongStore.validHash(hash)) return;
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            var removed = store.remove(hash);
            Bukkit.getScheduler().runTask(plugin, () -> {
                removed.ifPresent(song -> {
                    analysis.forget(hash);
                    playback.onSongRemoved(hash);
                    log(admin, (rejected ? "Ablehnung" : "Loeschung") + " von \"" + song.title() + "\" (" + hash.substring(0, 8) + ") von " + song.uploaderName());
                    Player uploader = Bukkit.getPlayer(song.uploader());
                    if (uploader != null) {
                        uploader.sendMessage(rejected
                                ? Messages.error(uploader, "Deine Datei \"" + song.title() + "\" wurde vom Server-Owner abgelehnt.",
                                        "Your file \"" + song.title() + "\" was rejected by the server owner.")
                                : Messages.error(uploader, "Dein Song \"" + song.title() + "\" wurde von einem Admin geloescht.",
                                        "Your song \"" + song.title() + "\" was deleted by an admin."));
                    }
                });
                if (admin.isOnline()) send(admin);
            });
        });
    }

    /** Schickt dem Operator die aktuelle Verwaltungs-Ansicht. */
    void send(Player player) {
        List<Song> all = store.all();
        all.sort(Comparator.comparingLong(Song::uploadedAt).reversed());
        List<Packet.AdminSong> songs = new ArrayList<>();
        for (Song s : all) {
            if (songs.size() >= Packet.AdminSongs.MAX_ADMIN_SONGS) break;
            songs.add(new Packet.AdminSong(s.hash(), cut(s.title()), cut(s.artist()), cut(s.uploaderName()), s.channels(),
                    s.durationMs(), s.sizeBytes(), s.status() == Song.Status.PENDING, s.uploadedAt()));
        }

        // Spieler: alle, die Songs haben oder ein eigenes Limit, dazu die gerade Online-Spieler
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, Integer> counts = new HashMap<>();
        for (Song s : all) {
            names.putIfAbsent(s.uploader(), s.uploaderName());
            counts.merge(s.uploader(), 1, Integer::sum);
        }
        limits.overrides().keySet().forEach(k -> {
            try {
                UUID id = UUID.fromString(k);
                names.putIfAbsent(id, Bukkit.getOfflinePlayer(id).getName() != null ? Bukkit.getOfflinePlayer(id).getName() : k);
            } catch (IllegalArgumentException ignored) { /* kaputter Eintrag */ }
        });
        for (Player online : Bukkit.getOnlinePlayers()) names.putIfAbsent(online.getUniqueId(), online.getName());
        List<Packet.AdminPlayer> players = new ArrayList<>();
        names.entrySet().stream().sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER)).limit(Packet.AdminSongs.MAX_ADMIN_PLAYERS)
                .forEach(e -> players.add(new Packet.AdminPlayer(cut(e.getValue()), e.getKey().getMostSignificantBits(),
                        e.getKey().getLeastSignificantBits(), counts.getOrDefault(e.getKey(), 0), limits.overrideFor(e.getKey()))));

        Net.send(plugin, player, new Packet.AdminSongs(songs, all.size(), store.totalBytes(), limits.maxStorageBytes(),
                limits.defaultMaxSongs(), players));
    }

    private static String cut(String s) {
        return s.length() > Limits.MAX_TEXT ? s.substring(0, Limits.MAX_TEXT) : s;
    }

    void onQuit(Player player) {
        lastRequest.remove(player.getUniqueId());
    }
}
