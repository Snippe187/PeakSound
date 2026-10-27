package de.peaksound.mod.client;

import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;

/**
 * Baut PeakSound-Items clientseitig (Creative-Tab) genauso, wie das Plugin sie erzeugt:
 * Vanilla-Traegeritem + Markierung unter {@code PublicBukkitValues} (so speichert Paper den PersistentDataContainer).
 */
public final class PeakItemStacks {
    private PeakItemStacks() {}

    /** Ist das ein PeakSound-Item dieser Art? (erkannt am custom_model_data-String, den auch das Plugin setzt) */
    public static boolean is(ItemStack stack, PeakItemType type) {
        CustomModelData data = stack == null ? null : stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data != null && !data.strings().isEmpty() && type.id().equals(data.strings().get(0));
    }

    public static ItemStack create(PeakItemType type) {
        Item carrier = BuiltInRegistries.ITEM.getValue(
                Identifier.withDefaultNamespace(type.carrierMaterial().toLowerCase(Locale.ROOT)));
        ItemStack stack = new ItemStack(carrier);

        CompoundTag bukkit = new CompoundTag();
        bukkit.putString(PeakIds.NAMESPACE + ":" + PeakIds.TYPE_KEY, type.id());
        CompoundTag root = new CompoundTag();
        root.put("PublicBukkitValues", bukkit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));

        // Gleicher custom_model_data-String wie im Plugin, damit das eigene Modell greift
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(type.id()), List.of()));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item." + PeakIds.NAMESPACE + "." + type.id()));
        if (type == PeakItemType.PEAK_TUNER) stack.set(DataComponents.MAX_STACK_SIZE, 1);
        return stack;
    }
}
