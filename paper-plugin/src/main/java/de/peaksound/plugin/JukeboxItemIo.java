package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PortFunction;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;

/**
 * Hopper und Kisten an einer Jukebox: Eine Seite mit "Eingang" nimmt beschriebene Platten aus dem Behaelter dort ins
 * Jukebox-Inventar, eine Seite mit "Ausgang" legt Platten in den Behaelter. Es bewegt sich alle 8 Ticks (wie ein Hopper)
 * hoechstens eine Platte je Seite.
 */
final class JukeboxItemIo {
    /** Seiten wie im Seitenmenue: Norden, Osten, Sueden, Westen, oben, unten. */
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};

    private final BlockRegistry registry;
    private final JukeboxSettingsStore settings;
    private final PlaylistService playlist;

    JukeboxItemIo(BlockRegistry registry, JukeboxSettingsStore settings, PlaylistService playlist) {
        this.registry = registry;
        this.settings = settings;
        this.playlist = playlist;
    }

    void tick() {
        for (BlockRegistry.Key key : registry.keysOf(PeakBlockType.JUKEBOX)) {
            List<PortFunction> ports = settings.get(key).ports();
            boolean any = false;
            for (PortFunction f : ports) any |= f.item();
            if (!any) continue;
            World world = Bukkit.getWorld(key.world());
            if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) continue;
            Block jukebox = world.getBlockAt(key.x(), key.y(), key.z());
            for (int i = 0; i < FACES.length; i++) {
                PortFunction f = ports.get(i);
                if (!f.item()) continue;
                Block neighbour = jukebox.getRelative(FACES[i]);
                BlockState state = neighbour.getState(false); // lebendiger Zugriff, keine Kopie
                if (!(state instanceof Container container)) continue;
                if (f == PortFunction.ITEM_IN) playlist.pull(jukebox, container.getInventory());
                else playlist.push(jukebox, container.getInventory());
            }
        }
    }
}