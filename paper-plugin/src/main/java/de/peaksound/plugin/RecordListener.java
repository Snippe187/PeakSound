package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Bedienung der PeakSound-Jukebox mit Platten:
 * <ul>
 *   <li>Shift + leere Platte: Upload-Fenster oeffnen</li>
 *   <li>beschriebene Platte: einlegen und abspielen</li>
 *   <li>Shift + beschriebene Platte (mit leerer Platte im Inventar): kopieren</li>
 *   <li>leere Hand: Platte entnehmen</li>
 * </ul>
 */
final class RecordListener implements Listener {
    private final PeakSoundPlugin plugin;
    private final PeakItems items;
    private final BlockRegistry registry;
    private final SongStore store;
    private final UploadService uploads;
    private final PlaybackManager playback;
    private final NetworkHandler network;

    RecordListener(PeakSoundPlugin plugin, PeakItems items, BlockRegistry registry, SongStore store,
                   UploadService uploads, PlaybackManager playback, NetworkHandler network) {
        this.plugin = plugin;
        this.items = items;
        this.registry = registry;
        this.store = store;
        this.uploads = uploads;
        this.playback = playback;
        this.network = network;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null || registry.get(block).filter(t -> t == PeakBlockType.JUKEBOX).isEmpty()) return;

        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        PeakItemType type = items.typeOf(hand).orElse(null);
        if (type == PeakItemType.PEAK_TUNER) return; // Menue-Zugriff gehoert dem BlockListener

        if (type == PeakItemType.BLANK_RECORD && player.isSneaking()) {
            event.setCancelled(true);
            if (!network.hasMod(player)) player.sendMessage(Messages.needMod(player));
            else uploads.openUpload(player);
        } else if (type == PeakItemType.WRITTEN_RECORD) {
            event.setCancelled(true);
            if (player.isSneaking()) copyRecord(player, hand);
            else insertRecord(player, block, hand);
        } else if (hand.getType().isAir() && !player.isSneaking()) {
            event.setCancelled(true);
            playback.eject(block).ifPresent(record -> give(player, record));
        }
    }

    private void insertRecord(Player player, Block block, ItemStack hand) {
        if (playback.hasRecord(block)) {
            player.sendMessage(Messages.error(player, "In dieser Jukebox liegt schon eine Platte.",
                    "There is already a record in this jukebox."));
            return;
        }
        PeakItems.Written written = items.writtenOf(hand).orElse(null);
        Song song = written == null ? null : store.get(written.hash()).orElse(null);
        if (song == null || song.status() != Song.Status.OK) {
            player.sendMessage(Messages.error(player, "Dieser Song ist auf dem Server nicht (mehr) verfuegbar.",
                    "This song is no longer available on the server."));
            return;
        }
        ItemStack record = hand.clone();
        hand.setAmount(hand.getAmount() - 1);
        player.getInventory().setItemInMainHand(hand.getAmount() <= 0 ? null : hand);
        playback.insert(block, record, song);
    }

    private void copyRecord(Player player, ItemStack written) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (items.typeOf(stack).filter(t -> t == PeakItemType.BLANK_RECORD).isPresent()) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
                ItemStack copy = written.clone();
                copy.setAmount(1);
                give(player, copy);
                player.sendMessage(Messages.info(player, "Platte kopiert.", "Record copied."));
                return;
            }
        }
        player.sendMessage(Messages.error(player, "Zum Kopieren brauchst du eine leere Platte im Inventar.",
                "You need a blank record in your inventory to copy."));
    }

    private void give(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
    }
}
