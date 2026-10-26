package de.peaksound.plugin;

import de.peaksound.protocol.PlaylistLogic;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Liest die Playlist einer Jukebox aus der Welt: Ein Hopper direkt an der Jukebox ist der CD-Wechsler,
 * seine beschriebenen Platten bilden die Playlist. Kisten (und andere Behaelter) am Hopper erweitern sie
 * um ihre Slots. Die Items bleiben dabei, wo sie sind: Die Reihenfolge ergibt sich aus den Slots, und
 * "verschieben" tauscht Platten zwischen Slots.
 */
final class PlaylistService {
    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

    /** Ein Eintrag der Playlist und der Slot, in dem die Platte liegt. */
    record Track(String hash, PeakItems.Written written, Song song, Inventory inventory, int slot) {
        boolean playable() { return song != null && song.status() == Song.Status.OK; }
    }

    private final PeakItems items;
    private final SongStore songs;

    PlaylistService(PeakItems items, SongStore songs) {
        this.items = items;
        this.songs = songs;
    }

    /** Alle Playlist-Eintraege in Reihenfolge (Hopper-Slots, dann Behaelter-Slots), hoechstens {@link PlaylistLogic#MAX_ENTRIES}. */
    List<Track> tracks(Block jukebox) {
        List<Inventory> inventories = new ArrayList<>();
        for (BlockFace face : FACES) {
            Block hopper = jukebox.getRelative(face);
            if (hopper.getType() != Material.HOPPER) continue;
            addInventory(inventories, hopper);
            for (BlockFace side : FACES) {
                Block neighbour = hopper.getRelative(side);
                if (neighbour.equals(jukebox) || neighbour.getType() == Material.HOPPER) continue;
                addInventory(inventories, neighbour);
            }
        }
        List<Track> tracks = new ArrayList<>();
        for (Inventory inv : inventories) {
            for (int slot = 0; slot < inv.getSize() && tracks.size() < PlaylistLogic.MAX_ENTRIES; slot++) {
                ItemStack stack = inv.getItem(slot);
                var written = items.writtenOf(stack);
                if (written.isEmpty()) continue;
                tracks.add(new Track(written.get().hash(), written.get(), songs.get(written.get().hash()).orElse(null), inv, slot));
            }
        }
        return tracks;
    }

    private void addInventory(List<Inventory> list, Block block) {
        BlockState state = block.getState(false); // lebendiger Zugriff, keine Kopie
        if (!(state instanceof Container container)) return;
        Inventory inv = container.getInventory();
        // Doppelkisten liefern von beiden Haelften dasselbe Inventar: nur einmal aufnehmen
        for (Inventory known : list) {
            if (known == inv || (known.getLocation() != null && known.getLocation().equals(inv.getLocation()))) return;
        }
        list.add(inv);
    }

    /** Tauscht zwei Platten (auch zwischen verschiedenen Behaeltern). */
    void swap(Track a, Track b) {
        ItemStack first = a.inventory().getItem(a.slot());
        ItemStack second = b.inventory().getItem(b.slot());
        a.inventory().setItem(a.slot(), second);
        b.inventory().setItem(b.slot(), first);
    }
}
