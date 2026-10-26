package de.peaksound.plugin;

import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Erzeugt und erkennt PeakSound-Items. Ein PeakSound-Item ist ein Vanilla-Traegeritem mit
 * Markierung im PersistentDataContainer. Der Name ist uebersetzbar (Mod liefert de/en,
 * ohne Mod greift der englische Fallback).
 */
public final class PeakItems {
    private final NamespacedKey typeKey = new NamespacedKey(PeakIds.NAMESPACE, PeakIds.TYPE_KEY);
    private final NamespacedKey songKey = new NamespacedKey(PeakIds.NAMESPACE, "song");
    private final NamespacedKey titleKey = new NamespacedKey(PeakIds.NAMESPACE, "title");
    private final NamespacedKey artistKey = new NamespacedKey(PeakIds.NAMESPACE, "artist");
    private final NamespacedKey uploaderKey = new NamespacedKey(PeakIds.NAMESPACE, "uploader");

    /** Angaben einer beschriebenen Platte. */
    public record Written(String hash, String title, String artist, String uploader) {}

    public ItemStack create(PeakItemType type, int amount) {
        Material material = Material.valueOf(type.carrierMaterial());
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        meta.itemName(Component.translatable("item.peaksound." + type.id(), fallbackName(type)));
        applyModelData(meta, type);
        if (type == PeakItemType.PEAK_TUNER) {
            meta.setMaxStackSize(1);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** Beschriebene Platte: Itemname = Songtitel; Interpret und Uploader als Lore (auch ohne Mod sichtbar). */
    public ItemStack createWritten(Written data) {
        ItemStack stack = new ItemStack(Material.valueOf(PeakItemType.WRITTEN_RECORD.carrierMaterial()));
        ItemMeta meta = stack.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(typeKey, PersistentDataType.STRING, PeakItemType.WRITTEN_RECORD.id());
        pdc.set(songKey, PersistentDataType.STRING, data.hash());
        pdc.set(titleKey, PersistentDataType.STRING, data.title());
        pdc.set(artistKey, PersistentDataType.STRING, data.artist());
        pdc.set(uploaderKey, PersistentDataType.STRING, data.uploader());
        meta.itemName(Component.text(data.title()));
        meta.lore(List.of(
                Component.text(data.artist().isBlank() ? "-" : data.artist(), NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text(data.uploader(), NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.setMaxStackSize(1);
        applyModelData(meta, PeakItemType.WRITTEN_RECORD);
        stack.setItemMeta(meta);
        return stack;
    }

    private void applyModelData(ItemMeta meta, PeakItemType type) {
        // Die Mod waehlt anhand dieses Strings das eigene Modell; Clients ohne Mod ignorieren ihn.
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of(type.id()));
        meta.setCustomModelDataComponent(modelData);
    }

    /** @return der PeakSound-Typ des Items, leer fuer alle anderen Items */
    public Optional<PeakItemType> typeOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return Optional.empty();
        String id = stack.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : PeakItemType.byId(id);
    }

    /** @return die Songdaten einer beschriebenen Platte, leer fuer alle anderen Items */
    public Optional<Written> writtenOf(ItemStack stack) {
        if (typeOf(stack).filter(t -> t == PeakItemType.WRITTEN_RECORD).isEmpty()) return Optional.empty();
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String hash = pdc.get(songKey, PersistentDataType.STRING);
        if (!SongStore.validHash(hash)) return Optional.empty();
        return Optional.of(new Written(hash,
                pdc.getOrDefault(titleKey, PersistentDataType.STRING, ""),
                pdc.getOrDefault(artistKey, PersistentDataType.STRING, ""),
                pdc.getOrDefault(uploaderKey, PersistentDataType.STRING, "")));
    }

    private static String fallbackName(PeakItemType type) {
        return switch (type) {
            case BLANK_RECORD -> "Blank Record";
            case WRITTEN_RECORD -> "Written Record";
            case PEAK_TUNER -> "Peak Tuner";
            case JUKEBOX -> "PeakSound Jukebox";
            case SPEAKER -> "Speaker";
            case DJ_DESK -> "DJ Desk";
            case SPOTLIGHT -> "Spotlight";
            case MICROPHONE -> "Microphone";
            case SONG_LIBRARY -> "Song Library";
            case MUSIC_REMOTE -> "Music Remote";
        };
    }
}
