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
    /** Das DJ-Pult-Rezept braucht PeakSound-Jukeboxen als Zutat (alle anderen Rezepte lehnen PeakSound-Items als Zutat ab). */
    public final NamespacedKey djDeskKey = key("dj_desk");

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

        // DJ-Pult: 2 PeakSound-Jukeboxen links und rechts, Redstone oben, Eisenbarren unten, Diamant in der Mitte
        ShapedRecipe dj = new ShapedRecipe(djDeskKey, items.create(PeakItemType.DJ_DESK, 1));
        dj.shape(" R ", "JDJ", " I ");
        dj.setIngredient('R', Material.REDSTONE);
        dj.setIngredient('D', Material.DIAMOND);
        dj.setIngredient('I', Material.IRON_INGOT);
        // Das Rezept nutzt den Traeger-Block der PeakSound-Jukebox (Notenblock); CraftListener prueft, dass es wirklich PeakSound-Jukeboxen sind
        dj.setIngredient('J', Material.NOTE_BLOCK);
        dj.setCategory(CraftingBookCategory.REDSTONE);
        add(dj);

        // Scheinwerfer (ergibt 2): Redstone-Lampe in der Mitte, Glas oben, Eisenbarren links/rechts/unten, Glowstone-Staub unten in den Ecken
        ShapedRecipe spotlight = new ShapedRecipe(key("spotlight"), items.create(PeakItemType.SPOTLIGHT, 2));
        spotlight.shape(" G ", "ILI", "NIN");
        spotlight.setIngredient('G', Material.GLASS);
        spotlight.setIngredient('I', Material.IRON_INGOT);
        spotlight.setIngredient('L', Material.REDSTONE_LAMP);
        spotlight.setIngredient('N', Material.GLOWSTONE_DUST);
        spotlight.setCategory(CraftingBookCategory.REDSTONE);
        add(spotlight);

        // Mikrofon: Eisenbarren oben und unten, Redstone-Staub links und rechts, Stock in der Mitte
        ShapedRecipe microphone = new ShapedRecipe(key("microphone"), items.create(PeakItemType.MICROPHONE, 1));
        microphone.shape(" I ", "RSR", " I ");
        microphone.setIngredient('I', Material.IRON_INGOT);
        microphone.setIngredient('R', Material.REDSTONE);
        microphone.setIngredient('S', Material.STICK);
        microphone.setCategory(CraftingBookCategory.REDSTONE);
        add(microphone);

        // Song-Bibliothek: Diamant oben, Obsidian links/rechts, normale Jukebox in der Mitte, drei Eisenbarren unten
        ShapedRecipe library = new ShapedRecipe(key("song_library"), items.create(PeakItemType.SONG_LIBRARY, 1));
        library.shape(" D ", "OJO", "III");
        library.setIngredient('D', Material.DIAMOND);
        library.setIngredient('O', Material.OBSIDIAN);
        library.setIngredient('J', Material.JUKEBOX);
        library.setIngredient('I', Material.IRON_INGOT);
        library.setCategory(CraftingBookCategory.REDSTONE);
        add(library);

        // Musik-Fernbedienung: Redstone oben, Eisen links/rechts, Comparator in der Mitte, Hebel unten
        ShapedRecipe remote = new ShapedRecipe(key("music_remote"), items.create(PeakItemType.MUSIC_REMOTE, 1));
        remote.shape(" R ", "ICI", " L ");
        remote.setIngredient('R', Material.REDSTONE);
        remote.setIngredient('I', Material.IRON_INGOT);
        remote.setIngredient('C', Material.COMPARATOR);
        remote.setIngredient('L', Material.LEVER);
        remote.setCategory(CraftingBookCategory.REDSTONE);
        add(remote);

        // Radio-Empfaenger: Blitzableiter (Antenne) oben, Notenblock zwischen Eisen, Redstone unten
        ShapedRecipe receiver = new ShapedRecipe(key("radio_receiver"), items.create(PeakItemType.RADIO_RECEIVER, 1));
        receiver.shape(" L ", "INI", "IRI");
        receiver.setIngredient('L', Material.LIGHTNING_ROD);
        receiver.setIngredient('I', Material.IRON_INGOT);
        receiver.setIngredient('N', Material.NOTE_BLOCK);
        receiver.setIngredient('R', Material.REDSTONE);
        receiver.setCategory(CraftingBookCategory.REDSTONE);
        add(receiver);

        // Radio-Sender: wie der Empfaenger, aber mit Gold an den Seiten und Amethystsplittern als Kristall unten
        ShapedRecipe transmitter = new ShapedRecipe(key("radio_transmitter"), items.create(PeakItemType.RADIO_TRANSMITTER, 1));
        transmitter.shape(" L ", "GNG", "ARA");
        transmitter.setIngredient('L', Material.LIGHTNING_ROD);
        transmitter.setIngredient('G', Material.GOLD_INGOT);
        transmitter.setIngredient('N', Material.NOTE_BLOCK);
        transmitter.setIngredient('A', Material.AMETHYST_SHARD);
        transmitter.setIngredient('R', Material.REDSTONE);
        transmitter.setCategory(CraftingBookCategory.REDSTONE);
        add(transmitter);

        // Display: Glasscheiben oben, Eisen links/rechts um den Bilderrahmen, Redstone unten (zeigt das Cover des laufenden Songs)
        ShapedRecipe displayRecipe = new ShapedRecipe(key("display"), items.create(PeakItemType.DISPLAY, 1));
        displayRecipe.shape("GGG", "IFI", " R ");
        displayRecipe.setIngredient('G', Material.GLASS_PANE);
        displayRecipe.setIngredient('I', Material.IRON_INGOT);
        displayRecipe.setIngredient('F', Material.ITEM_FRAME);
        displayRecipe.setIngredient('R', Material.REDSTONE);
        displayRecipe.setCategory(CraftingBookCategory.REDSTONE);
        add(displayRecipe);

        // Taschenradio: Blitzableiter (Antenne) oben, Notenblock zwischen Eisen, Redstone unten
        ShapedRecipe pocket = new ShapedRecipe(key("pocket_radio"), items.create(PeakItemType.POCKET_RADIO, 1));
        pocket.shape(" L ", "INI", " R ");
        pocket.setIngredient('L', Material.LIGHTNING_ROD);
        pocket.setIngredient('I', Material.IRON_INGOT);
        pocket.setIngredient('N', Material.NOTE_BLOCK);
        pocket.setIngredient('R', Material.REDSTONE);
        pocket.setCategory(CraftingBookCategory.EQUIPMENT);
        add(pocket);

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
