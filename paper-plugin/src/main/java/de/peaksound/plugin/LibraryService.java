package de.peaksound.plugin;

import de.peaksound.protocol.LibraryLogic;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Song-Bibliothek-Block: Der Spieler durchsucht alle auf dem Server hochgeladenen Songs und schreibt einen davon auf eine
 * leere Platte aus seinem Inventar. Der Server prueft jede Anfrage selbst (Block, Entfernung, Song vorhanden und freigegeben,
 * leere Platte im Inventar); der Client schlaegt nur vor.
 */
final class LibraryService {
    private final PeakSoundPlugin plugin;
    private final SongStore songs;
    private final PeakItems items;
    private final EditAccess access;

    LibraryService(PeakSoundPlugin plugin, SongStore songs, PeakItems items, EditAccess access) {
        this.plugin = plugin;
        this.songs = songs;
        this.items = items;
        this.access = access;
    }

    boolean enabled() {
        return plugin.getConfig().getBoolean("library.enabled", true);
    }

    void open(Player player, Block block) {
        if (!enabled()) {
            player.sendMessage(Messages.error(player, "Die Song-Bibliothek ist auf diesem Server ausgeschaltet.",
                    "The song library is turned off on this server."));
            return;
        }
        Net.send(plugin, player, new Packet.OpenLibraryMenu(block.getX(), block.getY(), block.getZ()));
    }

    private UploadService uploads;

    void setUploads(UploadService uploads) { this.uploads = uploads; }

    /** Zugriff ohne Block: Anfrage mit Marker-Koordinaten, erlaubt nur mit gueltigem Upload-Ticket (Shift-Klick mit leerer Platte). */
    private boolean remoteAccess(Player player, int x, int y, int z) {
        return x == Limits.LIBRARY_REMOTE && y == Limits.LIBRARY_REMOTE && z == Limits.LIBRARY_REMOTE
                && uploads != null && uploads.hasTicket(player) && access.rateOk(player);
    }

    void onQuery(Player player, Packet.LibraryQuery query) {
        if (!enabled()) return;
        if (remoteAccess(player, query.x(), query.y(), query.z())) {
            send(player, query.x(), query.y(), query.z(), query.query(), query.page());
            return;
        }
        Block block = access.validated(player, query.x(), query.y(), query.z(), PeakBlockType.SONG_LIBRARY);
        if (block == null) return;
        send(player, block.getX(), block.getY(), block.getZ(), query.query(), query.page());
    }

    private void send(Player player, int bx, int by, int bz, String query, int page) {
        List<Song> hits = new ArrayList<>();
        for (Song s : songs.all()) {
            if (s.status() == Song.Status.OK && LibraryLogic.matches(query, s.title(), s.artist(), s.uploaderName())) hits.add(s);
        }
        hits.sort(Comparator.comparing((Song s) -> s.title().toLowerCase(java.util.Locale.ROOT)).thenComparing(Song::uploadedAt));
        int pages = LibraryLogic.pages(hits.size(), Limits.LIBRARY_PAGE_SIZE);
        int clamped = LibraryLogic.clampPage(page, hits.size(), Limits.LIBRARY_PAGE_SIZE);
        List<Packet.LibrarySong> entries = new ArrayList<>();
        for (Song s : LibraryLogic.slice(hits, clamped, Limits.LIBRARY_PAGE_SIZE)) {
            entries.add(new Packet.LibrarySong(s.hash(), cut(s.title()), cut(s.artist()), cut(s.uploaderName()), s.durationMs()));
        }
        Net.send(plugin, player, new Packet.LibraryResults(bx, by, bz, clamped, pages, hits.size(),
                countBlank(player), entries));
    }

    void onWrite(Player player, Packet.LibraryWrite write) {
        if (!enabled() || !SongStore.validHash(write.hash())) return;
        if (!remoteAccess(player, write.x(), write.y(), write.z())
                && access.validated(player, write.x(), write.y(), write.z(), PeakBlockType.SONG_LIBRARY) == null) return;
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
        player.sendMessage(Messages.uploaded(player, song.title()));
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