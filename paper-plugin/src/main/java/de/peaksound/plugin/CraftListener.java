package de.peaksound.plugin;

import de.peaksound.protocol.PeakItemType;
import de.peaksound.protocol.SwordCraft;
import java.util.OptionalInt;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;

/**
 * Crafting-Logik: verhindert, dass PeakSound-Items als Zutat missbraucht werden, und setzt
 * das Schwert-Rezept (Schwert kommt mit halber Haltbarkeit zurueck) selbst um, auch bei Shift-Klick.
 */
final class CraftListener implements Listener {
    private final PeakSoundPlugin plugin;
    private final PeakItems items;
    private final PeakRecipes recipes;

    CraftListener(PeakSoundPlugin plugin, PeakItems items, PeakRecipes recipes) {
        this.plugin = plugin;
        this.items = items;
        this.recipes = recipes;
    }

    private boolean isOurs(Recipe recipe) {
        return recipe instanceof Keyed k && recipes.isOurs(k.getKey());
    }

    /** PeakSound-Jukeboxen/-Notenbloecke zaehlen nicht als normale Zutat (keine Vervielfaeltigung). */
    @EventHandler
    public void onPrepare(PrepareItemCraftEvent event) {
        if (!isOurs(event.getRecipe())) return;
        for (ItemStack ingredient : event.getInventory().getMatrix()) {
            if (items.typeOf(ingredient).isPresent()) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onCraft(CraftItemEvent event) {
        if (!(event.getRecipe() instanceof Keyed k) || !k.getKey().equals(recipes.blankRecordKey)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        CraftingInventory inv = event.getInventory();
        ItemStack[] matrix = inv.getMatrix();
        int swordSlot = -1;
        int obsidianSlot = -1;
        for (int i = 0; i < matrix.length; i++) {
            ItemStack s = matrix[i];
            if (s == null) continue;
            if (s.getType() == Material.DIAMOND_SWORD) swordSlot = i;
            else if (s.getType() == Material.OBSIDIAN) obsidianSlot = i;
        }
        if (swordSlot < 0 || obsidianSlot < 0) return;

        ItemStack sword = matrix[swordSlot];
        Damageable meta = (Damageable) sword.getItemMeta();
        int max = meta.hasMaxDamage() ? meta.getMaxDamage() : sword.getType().getMaxDurability();
        int damage = meta.getDamage();
        int obsidian = matrix[obsidianSlot].getAmount();

        // Normaler Klick = ein Craft, Shift-Klick = so oft wie moeglich
        int wanted = event.isShiftClick() ? obsidian : 1;
        int crafts = 0;
        boolean broken = false;
        while (crafts < wanted && !broken) {
            crafts++;
            OptionalInt next = SwordCraft.damageAfterCraft(max, damage);
            if (next.isPresent()) damage = next.getAsInt();
            else broken = true;
        }

        if (broken) {
            matrix[swordSlot] = null;
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
        } else {
            meta.setDamage(damage);
            sword.setItemMeta(meta);
        }
        ItemStack remainingObsidian = matrix[obsidianSlot];
        if (remainingObsidian.getAmount() - crafts <= 0) matrix[obsidianSlot] = null;
        else remainingObsidian.setAmount(remainingObsidian.getAmount() - crafts);
        inv.setMatrix(matrix);

        giveRecords(player, crafts * 9);
        // Ergebnis-Slot neu berechnen lassen und Client synchronisieren
        plugin.getServer().getScheduler().runTask(plugin, player::updateInventory);
    }

    private void giveRecords(Player player, int total) {
        int left = total;
        while (left > 0) {
            int amount = Math.min(left, 64);
            ItemStack stack = items.create(PeakItemType.BLANK_RECORD, amount);
            player.getInventory().addItem(stack).values()
                    .forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
            left -= amount;
        }
    }
}
