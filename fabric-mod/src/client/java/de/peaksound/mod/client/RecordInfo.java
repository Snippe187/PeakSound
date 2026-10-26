package de.peaksound.mod.client;

import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Liest die Songdaten einer beschriebenen Platte aus dem Item (so, wie Paper den PersistentDataContainer ablegt). */
public final class RecordInfo {
    private RecordInfo() {}

    /** @return der Song-Hash, wenn das Item eine beschriebene PeakSound-Platte ist */
    public static Optional<String> songHash(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return Optional.empty();
        CompoundTag root = data.copyTag();
        return root.getCompound("PublicBukkitValues").flatMap(values -> {
            String type = values.getString(PeakIds.NAMESPACE + ":" + PeakIds.TYPE_KEY).orElse("");
            if (!type.equals(PeakItemType.WRITTEN_RECORD.id())) return Optional.<String>empty();
            return values.getString(PeakIds.NAMESPACE + ":song").filter(h -> h.length() == 64);
        });
    }
}
