package de.peaksound.mod.client.mixin;

import java.util.List;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Zugriff auf die Reiterliste des Rezeptbuchs (Vanilla uebergibt eine unveraenderliche Liste). */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("tabInfos")
    List<RecipeBookComponent.TabInfo> peaksound$getTabInfos();

    @Mutable
    @Accessor("tabInfos")
    void peaksound$setTabInfos(List<RecipeBookComponent.TabInfo> tabs);
}