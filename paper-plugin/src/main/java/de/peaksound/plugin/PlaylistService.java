package de.peaksound.plugin;

import de.peaksound.protocol.PlaylistLogic;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die Playlist einer Jukebox ist ihr eigenes Inventar ({@link JukeboxStorage}, 15 Slots): Die beschriebenen Platten in
 * Slot-Reihenfolge bilden die Abspielreihenfolge, leere Slots werden uebersprungen. "Verschieben" tauscht Platten zwischen
 * Slots. Hopper und Kisten wirken nur noch ueber die Seiten-Einstellung der Jukebox (siehe {@link JukeboxItemIo}).
 */
final class PlaylistService {
    /** Ein Eintrag der Playlist und der Slot, in dem die Platte liegt. */
    record Track(String hash, PeakItems.Written written, Song song, Inventory inventory, int slot) {
        boolean playable() { return song != null && song.status() == Song.Status.OK; }
    }

    private final PeakItems items;
    private final SongStore songs;
    private final JukeboxStorage storage;

    PlaylistService(PeakItems items, SongStore songs, JukeboxStorage storage) {
        this.items = items;
        this.songs = songs;
        this.storage = storage;
    }

    /** Alle Playlist-Eintraege in Slot-Reihenfolge. */
    List<Track> tracks(Block jukebox) {
        Inventory inv = storage.inventory(BlockRegistry.Key.of(jukebox));
        List<Track> tracks = new ArrayList<>();
        for (int slot = 0; slot < PlaylistLogic.INVENTORY_SLOTS; slot++) {
            var written = items.writtenOf(inv.getItem(slot));
            if (written.isEmpty()) continue;
            tracks.add(new Track(written.get().hash(), written.get(), songs.get(written.get().hash()).orElse(null), inv, slot));
        }
        return tracks;
    }

    /** Tauscht zwei Platten (Slots desselben Inventars). */
    void swap(Track a, Track b) {
        ItemStack first = a.inventory().getItem(a.slot());
        ItemStack second = b.inventory().getItem(b.slot());
        a.inventory().setItem(a.slot(), second);
        b.inventory().setItem(b.slot(), first);
        storage.markDirty();
    }

    /** Legt eine Platte (eine Kopie von {@code stack}, Anzahl 1) in den ersten freien Slot. @return false: keine beschriebene Platte oder voll */
    boolean put(Block jukebox, ItemStack stack) {
        if (stack == null || items.writtenOf(stack).isEmpty()) return false;
        BlockRegistry.Key key = BlockRegistry.Key.of(jukebox);
        int slot = storage.freeSlot(key);
        if (slot < 0) return false;
        ItemStack single = stack.clone();
        single.setAmount(1);
        storage.inventory(key).setItem(slot, single);
        storage.markDirty();
        return true;
    }

    /** Nimmt die Platte aus dem Slot heraus (leer: null). */
    ItemStack take(Block jukebox, int slot) {
        if (slot < 0 || slot >= PlaylistLogic.INVENTORY_SLOTS) return null;
        Inventory inv = storage.inventory(BlockRegistry.Key.of(jukebox));
        ItemStack s = inv.getItem(slot);
        if (s == null || items.writtenOf(s).isEmpty()) return null;
        inv.setItem(slot, null);
        storage.markDirty();
        return s;
    }

    /** Nimmt die erste beschriebene Platte aus {@code source} (Hopper oder Behaelter) ins Jukebox-Inventar. @return ob etwas bewegt wurde */
    boolean pull(Block jukebox, Inventory source) {
        for (int i = 0; i < source.getSize(); i++) {
            ItemStack s = source.getItem(i);
            if (s == null || items.writtenOf(s).isEmpty()) continue;
            if (!put(jukebox, s)) return false; // voll
            s.setAmount(s.getAmount() - 1);
            source.setItem(i, s.getAmount() <= 0 ? null : s);
            return true;
        }
        return false;
    }

    /** Legt die erste Platte aus dem Jukebox-Inventar in {@code target}. @return ob etwas bewegt wurde */
    boolean push(Block jukebox, Inventory target) {
        Inventory inv = storage.inventory(BlockRegistry.Key.of(jukebox));
        for (int slot = 0; slot < PlaylistLogic.INVENTORY_SLOTS; slot++) {
            ItemStack s = inv.getItem(slot);
            if (s == null || items.writtenOf(s).isEmpty()) continue;
            if (!target.addItem(s.clone()).isEmpty()) return false; // Ziel voll
            inv.setItem(slot, null);
            storage.markDirty();
            return true;
        }
        return false;
    }
}