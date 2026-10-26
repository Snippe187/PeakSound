package de.peaksound.mod.client.mixin;

import de.peaksound.mod.client.ClientSettings;
import de.peaksound.mod.client.IntSlider;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Haengt den PeakSound-Lautstaerkeregler an die Minecraft-Soundeinstellungen. */
@Mixin(SoundOptionsScreen.class)
public abstract class SoundOptionsScreenMixin {
    @Inject(method = "addOptions", at = @At("TAIL"))
    private void peaksound$addVolumeSlider(CallbackInfo ci) {
        OptionsList list = ((OptionsSubScreenAccessor) this).peaksound$list();
        list.addBig(new IntSlider(0, 0, 310, "peaksound.options.volume", 0, 100, ClientSettings.volumePercent(),
                ClientSettings::setVolumePercent));
        list.addBig(net.minecraft.client.gui.components.Button.builder(peaksound$strobeLabel(), b -> {
            ClientSettings.setStrobeAllowed(!ClientSettings.strobeAllowed());
            b.setMessage(peaksound$strobeLabel());
        }).bounds(0, 0, 310, 20).build());
    }

    private static net.minecraft.network.chat.Component peaksound$strobeLabel() {
        return net.minecraft.network.chat.Component.translatable("peaksound.options.strobe",
                net.minecraft.network.chat.Component.translatable(ClientSettings.strobeAllowed() ? "options.on" : "options.off"));
    }
}
