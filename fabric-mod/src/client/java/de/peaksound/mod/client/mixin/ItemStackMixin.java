package de.peaksound.mod.client.mixin;

import de.peaksound.mod.client.CoverTooltip;
import de.peaksound.mod.client.RecordInfo;
import java.util.Optional;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Haengt das Cover an den Tooltip beschriebener Platten (Vanilla-Tooltips haben sonst keine Bilder). */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
    private void peaksound$coverTooltip(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
        RecordInfo.songHash((ItemStack) (Object) this)
                .ifPresent(hash -> cir.setReturnValue(Optional.of(new CoverTooltip.Data(hash))));
    }
}
