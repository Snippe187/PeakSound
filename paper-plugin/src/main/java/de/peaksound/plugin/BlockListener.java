package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import java.util.Optional;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.NotePlayEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Platzieren, Abbauen und Schuetzen der PeakSound-Bloecke sowie der Peak-Tuner. */
final class BlockListener implements Listener {
    private final PeakItems items;
    private final BlockRegistry registry;

    BlockListener(PeakItems items, BlockRegistry registry) {
        this.items = items;
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Optional<PeakBlockType> type = items.typeOf(event.getItemInHand()).flatMap(PeakItemType::block);
        type.ifPresent(t -> registry.register(event.getBlockPlaced(), t));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Optional<PeakBlockType> type = registry.get(block);
        if (type.isEmpty()) return;
        registry.remove(block);
        // Statt des Vanilla-Drops faellt das PeakSound-Item (nicht im Kreativmodus)
        event.setDropItems(false);
        if (event.getPlayer().getGameMode() != GameMode.CREATIVE) {
            PeakItemType item = type.get() == PeakBlockType.JUKEBOX ? PeakItemType.JUKEBOX : PeakItemType.SPEAKER;
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5, 0.5, 0.5), items.create(item, 1));
        }
    }

    /** PeakSound-Bloecke bleiben bei Explosionen heil (vermeidet Verlust von Playlists und Einstellungen). */
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(b -> registry.get(b).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(b -> registry.get(b).isPresent());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> registry.get(b).isPresent())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> registry.get(b).isPresent())) event.setCancelled(true);
    }

    /** Lautsprecher machen keine Notenblock-Toene. */
    @EventHandler(ignoreCancelled = true)
    public void onNote(NotePlayEvent event) {
        if (registry.get(event.getBlock()).isPresent()) event.setCancelled(true);
    }

    /**
     * Rechtsklick auf PeakSound-Bloecke: Vanilla-Verhalten (Platte einlegen, Tonhoehe aendern)
     * wird unterbunden. Mit dem Peak-Tuner zeigt eine Info an, welcher Block es ist.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Optional<PeakBlockType> type = registry.get(block);
        if (type.isEmpty()) return;

        Player player = event.getPlayer();
        boolean tuner = items.typeOf(player.getInventory().getItemInMainHand())
                .filter(t -> t == PeakItemType.PEAK_TUNER).isPresent();
        // Normales Setzen von Bloecken auf die Flaeche bleibt moeglich (Shift + Block in der Hand)
        if (!tuner && player.isSneaking() && !player.getInventory().getItemInMainHand().getType().isAir()) return;

        event.setUseInteractedBlock(org.bukkit.event.Event.Result.DENY);
        if (tuner) {
            event.setUseItemInHand(org.bukkit.event.Event.Result.DENY);
            player.sendActionBar(Messages.tunerInfo(player, Messages.blockName(player, type.get()),
                    block.getX(), block.getY(), block.getZ()));
        }
    }
}
