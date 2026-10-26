package de.peaksound.mod.client.mixin;

import de.peaksound.mod.client.PeakRecipeCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fuellt den Reiter "PeakSound" mit allen Rezeptgruppen, deren Ergebnis ein PeakSound-Item ist. */
@Mixin(ClientRecipeBook.class)
public abstract class ClientRecipeBookMixin {
    @Shadow
    private Map<ExtendedRecipeBookCategory, List<RecipeCollection>> collectionsByTab;

    @Shadow
    private List<RecipeCollection> allCollections;

    @Inject(method = "rebuildCollections", at = @At("TAIL"))
    private void peaksound$addPeakTab(CallbackInfo ci) {
        List<RecipeCollection> mine = new ArrayList<>();
        for (RecipeCollection collection : allCollections) {
            if (!collection.getRecipes().isEmpty() && collection.getRecipes().stream().anyMatch(PeakRecipeCategory::isPeakRecipe)) {
                mine.add(collection);
            }
        }
        Map<ExtendedRecipeBookCategory, List<RecipeCollection>> copy = new HashMap<>(collectionsByTab);
        copy.put(PeakRecipeCategory.PEAKSOUND, List.copyOf(mine));
        collectionsByTab = copy;
    }
}