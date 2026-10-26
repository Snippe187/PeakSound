package de.peaksound.plugin;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Haelt den Netz-Cache aktuell: Jede Blockaenderung in der Naehe eines Netzes verwirft nur dessen Eintrag
 * (neu berechnet wird erst, wenn das Netz wieder gebraucht wird). Schuetzt ausserdem die Equalizer-Comparators
 * davor, von Vanilla auf 0 zurueckgesetzt zu werden.
 */
final class NetworkListener implements Listener {
    private final NetworkRegistry networks;
    private final EqualizerService equalizer;

    NetworkListener(NetworkRegistry networks, EqualizerService equalizer) {
        this.networks = networks;
        this.equalizer = equalizer;
    }

    private void changed(Block block) {
        networks.invalidateAt(block.getWorld(), block.getX(), block.getY(), block.getZ());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) { changed(event.getBlockPlaced()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) { changed(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { changed(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) { changed(event.getToBlock()); } // Wasser/Lava spuelt Staub weg

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent event) { changed(event.getBlock()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) { event.blockList().forEach(this::changed); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) { event.blockList().forEach(this::changed); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        World world = event.getBlock().getWorld();
        event.getBlocks().forEach(b -> {
            changed(b);
            Block target = b.getRelative(event.getDirection());
            networks.invalidateAt(world, target.getX(), target.getY(), target.getZ());
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        World world = event.getBlock().getWorld();
        event.getBlocks().forEach(b -> {
            changed(b);
            Block target = b.getRelative(event.getDirection());
            networks.invalidateAt(world, target.getX(), target.getY(), target.getZ());
        });
    }

    /** Equalizer-Comparators werden nur vom EqualizerService gesetzt, nie von Vanilla neu berechnet. */
    @EventHandler(ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (equalizer.isPort(event.getBlock())) event.setCancelled(true);
    }
}
