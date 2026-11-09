package de.peaksound.mod.client.mixin;

import de.peaksound.mod.client.ClientSettings;
import de.peaksound.mod.client.PeakAdvancementsOpener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Tauscht die Standard-Fortschrittsansicht (Taste L) gegen die PeakVerse-Ansicht, solange sie in den Einstellungen an ist. */
@Mixin(Minecraft.class)
public abstract class MinecraftAdvancementsMixin {
    @ModifyVariable(method = "setScreenAndShow", at = @At("HEAD"), argsOnly = true)
    private Screen peaksound$advancements(Screen screen) {
        return ClientSettings.peakAdvancements() ? PeakAdvancementsOpener.replace(screen) : screen;
    }
}
