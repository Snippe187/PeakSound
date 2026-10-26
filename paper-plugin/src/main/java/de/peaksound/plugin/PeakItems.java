package de.peaksound.plugin;

import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import java.util.List;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Erzeugt und erkennt PeakSound-Items. Ein PeakSound-Item ist ein Vanilla-Traegeritem mit
 * Markierung im PersistentDataContainer. Der Name ist uebersetzbar (Mod liefert de/en,
 * ohne Mod greift der englische Fallback).
 */
public final class PeakItems {
    private final NamespacedKey typeKey = new NamespacedKey(PeakIds.NAMESPACE, PeakIds.TYPE_KEY);

    public ItemStack create(PeakItemType type, int amount) {
        Material material = Material.valueOf(type.carrierMaterial());
        ItemStack stack = new ItemStack(material, amount);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, type.id());
        meta.itemName(Component.translatable("item.peaksound." + type.id(), fallbackName(type)));
        // Die Mod waehlt anhand dieses Strings das eigene Modell; Clients ohne Mod ignorieren ihn.
        CustomModelDataComponent modelData = meta.getCustomModelDataComponent();
        modelData.setStrings(List.of(type.id()));
        meta.setCustomModelDataComponent(modelData);
        if (type == PeakItemType.PEAK_TUNER) {
            meta.setMaxStackSize(1);
        }
        stack.setItemMeta(meta);
        return stack;
    }

    /** @return der PeakSound-Typ des Items, leer fuer alle anderen Items */
    public Optional<PeakItemType> typeOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) return Optional.empty();
        String id = stack.getItemMeta().getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return id == null ? Optional.empty() : PeakItemType.byId(id);
    }

    private static String fallbackName(PeakItemType type) {
        return switch (type) {
            case BLANK_RECORD -> "Blank Record";
            case WRITTEN_RECORD -> "Written Record";
            case PEAK_TUNER -> "Peak Tuner";
            case JUKEBOX -> "PeakSound Jukebox";
            case SPEAKER -> "Speaker";
        };
    }
}
