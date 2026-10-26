package de.peaksound.mod.client;

import de.peaksound.protocol.PeakItemType;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.crafting.ExtendedRecipeBookCategory;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;

/**
 * Eigener Reiter "PeakSound" im Rezeptbuch (Inventar und Werkbank). Der Server kennt die Kategorie nicht; der Client
 * sortiert alle Rezepte, deren Ergebnis ein PeakSound-Item ist (erkennbar am custom_model_data-String), selbst in den Reiter.
 */
public enum PeakRecipeCategory implements ExtendedRecipeBookCategory {
    PEAKSOUND;

    private static final Set<String> IDS = new HashSet<>();

    static {
        for (PeakItemType t : PeakItemType.values()) IDS.add(t.id());
    }

    /** Ist das Ergebnis dieses Rezepts ein PeakSound-Item? */
    public static boolean isPeakRecipe(RecipeDisplayEntry entry) {
        RecipeDisplay display = entry.display();
        SlotDisplay result;
        if (display instanceof ShapedCraftingRecipeDisplay shaped) result = shaped.result();
        else if (display instanceof ShapelessCraftingRecipeDisplay shapeless) result = shapeless.result();
        else return false;
        if (!(result instanceof SlotDisplay.ItemStackSlotDisplay stackDisplay)) return false;
        ItemStackTemplate stack = stackDisplay.stack();
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        return data != null && !data.strings().isEmpty() && IDS.contains(data.strings().get(0));
    }
}