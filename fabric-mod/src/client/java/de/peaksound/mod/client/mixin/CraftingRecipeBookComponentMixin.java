package de.peaksound.mod.client.mixin;

import de.peaksound.mod.client.PeakItemStacks;
import de.peaksound.mod.client.PeakRecipeCategory;
import de.peaksound.protocol.PeakItemType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.gui.screens.recipebook.CraftingRecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Haengt den Reiter "PeakSound" (Icon: Jukebox) an das Rezeptbuch von Inventar und Werkbank. */
@Mixin(CraftingRecipeBookComponent.class)
public abstract class CraftingRecipeBookComponentMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void peaksound$addTab(AbstractCraftingMenu menu, CallbackInfo ci) {
        RecipeBookComponentAccessor accessor = (RecipeBookComponentAccessor) (Object) this;
        List<RecipeBookComponent.TabInfo> tabs = new ArrayList<>(accessor.peaksound$getTabInfos());
        tabs.add(new RecipeBookComponent.TabInfo(PeakItemStacks.create(PeakItemType.JUKEBOX), Optional.empty(), PeakRecipeCategory.PEAKSOUND));
        accessor.peaksound$setTabInfos(tabs);
    }
}