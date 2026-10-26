package de.peaksound.mod.client;

import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.PeakItemType;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

/** Eigener Creative-Tab "PeakSound" mit allen Items und Bloecken. */
public final class PeakCreativeTab {
    private PeakCreativeTab() {}

    static void register() {
        Identifier id = Identifier.fromNamespaceAndPath(PeakIds.NAMESPACE, "main");
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB, id);
        CreativeModeTab tab = FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup." + PeakIds.NAMESPACE))
                .icon(() -> PeakItemStacks.create(PeakItemType.JUKEBOX))
                .displayItems((params, output) -> {
                    for (PeakItemType type : PeakItemType.values()) {
                        // Beschriebene Platten entstehen nur durch Upload, nicht per Creative-Tab
                        if (type != PeakItemType.WRITTEN_RECORD) output.accept(PeakItemStacks.create(type));
                    }
                })
                .build();
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
    }
}
