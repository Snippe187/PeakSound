package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.protocol.LibraryLogic;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Song-Bibliothek: Der Spieler durchsucht alle auf dem Server hochgeladenen Songs (sortiert, gefiltert, mit Favoriten) und
 * schreibt einen davon auf eine leere Platte aus seinem Inventar. Eigene Songs kann er umbenennen oder loeschen. Der Server
 * prueft jede Anfrage selbst (Block oder Upload-Ticket, Entfernung, Song vorhanden und freigegeben, Besitzer oder Operator,
 * leere Platte im Inventar); der Client schlaegt nur vor.
 */
final class LibraryService {
    private final JavaPlugin plugin;
    private final SongStore songs;
    private final PeakItems items;
    private final EditAccess access;
    private final FavoriteStore favorites;
    private final PlaybackManager playback;
    private final AnalysisStore analysis;
    private UploadService uploads;
    private QueueService queue;

    void setQueue(QueueService queue) { this.queue = queue; }

    LibraryService(JavaPlugin plugin, SongStore songs, PeakItems items, EditAccess access, FavoriteStore favorites,
                   PlaybackManager playback, AnalysisStore analysis) {
        this.plugin = plugin;
        this.songs = songs;
        this.items = items;
        this.access = access;
        this.favorites = favorites;
        this.playback = playback;
        this.analysis = analysis;
    }

    void setUploads(UploadService uploads) { this.uploads = uploads; }

    boolean enabled() {
        return Ctx.config().getBoolean("library.enabled", true);
    }

    void open(Player player, Block block) {
        if (!enabled()) {
            player.sendMessage(Messages.error(player, "Die Song-Bibliothek ist auf diesem Server ausgeschaltet.",
                    "The song library is turned off on this server."));
            return;
        }
        if (!Permissions.check(player, "peaksound.library", "Du darfst die Song-Bibliothek nicht benutzen.", "You may not use the song library.")) return;
        Net.send(plugin, player, new Packet.OpenLibraryMenu(block.getX(), block.getY(), block.getZ()));
    }

    /** Zugriff ohne Block: Anfrage mit Marker-Koordinaten, erlaubt nur mit gueltigem Upload-Ticket (Shift-Klick mit leerer Platte). */
    private boolean remoteAccess(Player player, int x, int y, int z) {
        return x == Limits.LIBRARY_REMOTE && y == Limits.LIBRARY_REMOTE && z == Limits.LIBRARY_REMOTE
                && uploads != null && uploads.hasTicket(player) && access.rateOk(player);
    }

    /** Darf der Spieler diese Anfrage stellen (Song-Library-Block in Reichweite oder Upload-Ticket)? */
    private boolean allowed(Player player, int x, int y, int z) {
        if (!enabled() || !player.hasPermission("peaksound.library")) return false;
        return remoteAccess(player, x, y, z) || access.validated(player, x, y, z, PeakBlockType.SONG_LIBRARY) != null;
    }

    void onQuery(Player player, Packet.LibraryQuery query) {
        if (!allowed(player, query.x(), query.y(), query.z())) return;
        send(player, query.x(), query.y(), query.z(), query.query(), query.page(), query.sort(), query.filter());
    }

    private record Hit(Song song, boolean favorite, boolean mine) {}

    private void send(Player player, int bx, int by, int bz, String query, int page, LibraryLogic.Sort sort, LibraryLogic.Filter filter) {
        List<Hit> hits = new ArrayList<>();
        for (Song s : songs.all()) {
            if (s.status() != Song.Status.OK || !LibraryLogic.matches(query, s.title(), s.artist(), s.uploaderName())) continue;
            boolean fav = favorites.isFavorite(player.getUniqueId(), s.hash());
            boolean mine = s.uploader().equals(player.getUniqueId());
            if (filter == LibraryLogic.Filter.FAVORITES && !fav) continue;
            if (filter == LibraryLogic.Filter.MINE && !mine) continue;
            hits.add(new Hit(s, fav, mine));
        }
        var order = LibraryLogic.comparator(sort);
        hits.sort((a, b) -> order.compare(item(a.song()), item(b.song())));
        int pages = LibraryLogic.pages(hits.size(), Limits.LIBRARY_PAGE_SIZE);
        int clamped = LibraryLogic.clampPage(page, hits.size(), Limits.LIBRARY_PAGE_SIZE);
        List<Packet.LibrarySong> entries = new ArrayList<>();
        for (Hit h : LibraryLogic.slice(hits, clamped, Limits.LIBRARY_PAGE_SIZE)) {
            Song s = h.song();
            entries.add(new Packet.LibrarySong(s.hash(), cut(s.title()), cut(s.artist()), cut(s.uploaderName()), s.durationMs(),
                    h.favorite(), canManage(player, h.mine()), s.hasCover(), s.plays()));
        }
        Net.send(plugin, player, new Packet.LibraryResults(bx, by, bz, clamped, pages, hits.size(), countBlank(player), entries));
    }

    /** Darf der Spieler diesen Song umbenennen/loeschen? Eigene mit peaksound.library.manage.own, fremde mit .manage.any bzw. Operator. */
    private static boolean canManage(Player player, boolean own) {
        return LibraryLogic.mayManage(own && player.hasPermission("peaksound.library.manage.own"), Permissions.manageAny(player));
    }

    private static LibraryLogic.Item item(Song s) {
        return new LibraryLogic.Item(s.title(), s.artist(), s.durationMs(), s.uploadedAt(), s.plays());
    }

    void onWrite(Player player, Packet.LibraryWrite write) {
        if (!SongStore.validHash(write.hash()) || !allowed(player, write.x(), write.y(), write.z())) return;
        Song song = songs.get(write.hash()).filter(s -> s.status() == Song.Status.OK).orElse(null);
        if (song == null) {
            player.sendMessage(Messages.error(player, "Dieser Song ist nicht mehr verfuegbar.", "This song is no longer available."));
            return;
        }
        PlayerInventory inv = player.getInventory();
        int slot = findBlank(inv);
        if (slot < 0) {
            player.sendMessage(Messages.error(player, "Du brauchst eine leere Platte im Inventar.", "You need a blank record in your inventory."));
            return;
        }
        ItemStack blank = inv.getItem(slot);
        blank.setAmount(blank.getAmount() - 1);
        inv.setItem(slot, blank.getAmount() <= 0 ? null : blank);
        ItemStack written = items.createWritten(new PeakItems.Written(song.hash(), song.title(), song.artist(), song.uploaderName()));
        inv.addItem(written).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        songs.countPlay(song.hash());
        player.sendMessage(Messages.uploaded(player, song.title()));
    }

    /** Favorit umschalten, eigenen Song umbenennen oder loeschen (Operatoren duerfen auch fremde). */
    void onManage(Player player, Packet.LibraryManage manage) {
        if (!SongStore.validHash(manage.hash()) || !allowed(player, manage.x(), manage.y(), manage.z())) return;
        Song song = songs.get(manage.hash()).orElse(null);
        if (song == null) return;
        switch (manage.kind()) {
            case FAVORITE -> favorites.toggle(player.getUniqueId(), song.hash());
            case QUEUE -> {
                if (manage.x() == Limits.LIBRARY_REMOTE || queue == null || song.status() != Song.Status.OK) return;
                if (!Permissions.check(player, "peaksound.queue", "Du darfst keine Songs wuenschen.", "You may not request songs.")) return;
                Block block = player.getWorld().getBlockAt(manage.x(), manage.y(), manage.z());
                queue.request(player, block, song);
            }
            case RENAME -> {
                String title = de.peaksound.audio.AudioInspector.clean(manage.text());
                if (title.isEmpty() || !canManage(player, song.uploader().equals(player.getUniqueId()))) return;
                songs.rename(song.hash(), title);
                Ctx.logger().info("[Bibliothek] " + player.getName() + " benennt \"" + song.title() + "\" um in \"" + title + "\"");
            }
            case DELETE -> {
                if (!canManage(player, song.uploader().equals(player.getUniqueId()))) return;
                removeSong(player, song);
                return; // Antwort folgt, wenn die Dateien weg sind
            }
        }
    }

    private void removeSong(Player player, Song song) {
        Bukkit.getAsyncScheduler().runNow(plugin, task -> {
            var removed = songs.remove(song.hash());
            Bukkit.getScheduler().runTask(plugin, () -> {
                removed.ifPresent(s -> {
                    analysis.forget(s.hash());
                    favorites.forget(s.hash());
                    playback.onSongRemoved(s.hash());
                    Ctx.logger().info("[Bibliothek] " + player.getName() + " loescht \"" + s.title() + "\" (" + s.hash().substring(0, 8) + ")");
                    player.sendMessage(Messages.info(player, "Song \"" + s.title() + "\" geloescht. Platten damit bleiben stumm.",
                            "Song \"" + s.title() + "\" deleted. Records with it will stay silent."));
                });
            });
        });
    }

    /** Slot der ersten leeren Platte (Haupthand zuerst), oder -1. */
    private int findBlank(PlayerInventory inv) {
        int held = inv.getHeldItemSlot();
        if (isBlank(inv.getItem(held))) return held;
        for (int i = 0; i < inv.getSize(); i++) {
            if (isBlank(inv.getItem(i))) return i;
        }
        return -1;
    }

    private int countBlank(Player player) {
        int n = 0;
        for (ItemStack s : player.getInventory().getContents()) if (isBlank(s)) n += s.getAmount();
        return n;
    }

    private boolean isBlank(ItemStack s) {
        return s != null && items.typeOf(s).filter(t -> t == PeakItemType.BLANK_RECORD).isPresent();
    }

    private static String cut(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }
}
