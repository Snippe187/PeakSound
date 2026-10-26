package de.peaksound.plugin;

import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;

/** Registriert alle Crafting-Rezepte. Sie werden Spielern beim Beitritt im Rezeptbuch freigeschaltet. */
public final class PeakRecipes {
    private final PeakSoundPlugin plugin;
    private final PeakItems items;
    private final List<NamespacedKey> keys = new ArrayList<>();

    public final NamespacedKey blankRecordKey = key("blank_record");

    PeakRecipes(PeakSoundPlugin plugin, PeakItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    private static NamespacedKey key(String id) {
        return new NamespacedKey(PeakIds.NAMESPACE, id);
    }

    void register() {
        // 1 Obsidianblock + 1 Diamantschwert = 9 leere Platten (Schwert-Rueckgabe: siehe CraftListener)
        ShapelessRecipe blank = new ShapelessRecipe(blankRecordKey, items.create(PeakItemType.BLANK_RECORD, 9));
        blank.addIngredient(Material.OBSIDIAN);
        blank.addIngredient(Material.DIAMOND_SWORD);
        blank.setCategory(CraftingBookCategory.MISC);
        add(blank);

        // Jukebox in der Mitte, Diamant oben, Redstone links und rechts
        ShapedRecipe jukebox = new ShapedRecipe(key("jukebox"), items.create(PeakItemType.JUKEBOX, 1));
        jukebox.shape(" D ", "RJR");
        jukebox.setIngredient('D', Material.DIAMOND);
        jukebox.setIngredient('R', Material.REDSTONE);
        jukebox.setIngredient('J', Material.JUKEBOX);
        jukebox.setCategory(CraftingBookCategory.REDSTONE);
        add(jukebox);

        // Notenblock in der Mitte, Eisen oben und unten, Redstone links und rechts
        ShapedRecipe speaker = new ShapedRecipe(key("speaker"), items.create(PeakItemType.SPEAKER, 1));
        speaker.shape(" I ", "RNR", " I ");
        speaker.setIngredient('I', Material.IRON_INGOT);
        speaker.setIngredient('R', Material.REDSTONE);
        speaker.setIngredient('N', Material.NOTE_BLOCK);
        speaker.setCategory(CraftingBookCategory.REDSTONE);
        add(speaker);

        // Holzhacke + Redstone-Staub
        ShapelessRecipe tuner = new ShapelessRecipe(key("peak_tuner"), items.create(PeakItemType.PEAK_TUNER, 1));
        tuner.addIngredient(Material.WOODEN_HOE);
        tuner.addIngredient(Material.REDSTONE);
        tuner.setCategory(CraftingBookCategory.EQUIPMENT);
        add(tuner);
    }

    private void add(org.bukkit.inventory.Recipe recipe) {
        NamespacedKey key = ((org.bukkit.Keyed) recipe).getKey();
        Bukkit.removeRecipe(key); // bei /reload nicht doppelt registrieren
        Bukkit.addRecipe(recipe);
        keys.add(key);
    }

    void unregister() {
        keys.forEach(Bukkit::removeRecipe);
        keys.clear();
    }

    List<NamespacedKey> keys() {
        return List.copyOf(keys);
    }

    boolean isOurs(NamespacedKey key) {
        return keys.contains(key);
    }
}
