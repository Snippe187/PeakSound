package de.peaksound.mod.client.mixin;

import de.peaksound.mod.client.ClientSettings;
import de.peaksound.mod.client.IntSlider;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Haengt den PeakSound-Lautstaerkeregler an die Minecraft-Soundeinstellungen. */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {
    @Shadow
    protected OptionsList list;

    @Inject(method = "addOptions", at = @At("TAIL"))
    private void peaksound$addVolumeSlider(CallbackInfo ci) {
        list.addBig(new IntSlider(0, 0, 310, "peaksound.options.volume", 0, 100, ClientSettings.volumePercent(),
                ClientSettings::setVolumePercent));
    }
}
