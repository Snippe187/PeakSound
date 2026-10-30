package de.peaksound.mod.client;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import java.util.HashSet;
import java.util.Set;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.SingleVariant;
import net.minecraft.client.renderer.block.dispatch.Variant;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Setzt die PeakSound-Modelle per Code ein, statt Vanilla-Dateien (Notenblock-Blockstate, Item-Definitionen) zu ueberschreiben.
 * Ein Server-Resource-Pack, das diese Vanilla-Dateien ersetzt, kann dadurch unsere Optik nicht mehr verdraengen:
 * die Modelle liegen nur unter {@code peaksound:} und werden nach dem Laden aller Packs fuer genau unsere Zustaende bzw. Items eingehaengt.
 */
public final class PeakModels {
    /** Maximale Lautsprecher-Nummer (Zustand instrument=custom_head, powered=true, Ton = Nummer - 1). */
    private static final int MAX_SPEAKERS = 25;

    /** Vanilla-Items, die als Traeger dienen (siehe PeakItemType.carrierMaterial). */
    private static final Set<Identifier> CARRIERS = Set.of(
            Identifier.withDefaultNamespace("note_block"), Identifier.withDefaultNamespace("player_head"),
            Identifier.withDefaultNamespace("trial_key"), Identifier.withDefaultNamespace("disc_fragment_5"));
    private static final Set<String> ITEM_IDS = new HashSet<>();

    static {
        for (PeakItemType type : PeakItemType.values()) ITEM_IDS.add(type.id());
    }

    private PeakModels() {}

    public static void register() {
        ModelLoadingPlugin.register(plugin -> {
            plugin.modifyBlockModelOnLoad().register((model, context) -> {
                Identifier ours = blockModel(context.state());
                return ours == null ? model : new SingleVariant.Unbaked(new Variant(ours)).asRoot();
            });
            plugin.modifyItemModelAfterBake().register((model, context) ->
                    CARRIERS.contains(context.itemId()) ? new CarrierItemModel(model) : model);
        });
    }

    /** Eigenes Blockmodell fuer einen Notenblock-Zustand, sonst {@code null} (dann bleibt es beim Modell des Packs bzw. von Vanilla). */
    static Identifier blockModel(BlockState state) {
        if (!state.is(Blocks.NOTE_BLOCK) || !state.getValue(NoteBlock.POWERED)) return null;
        String instrument = state.getValue(NoteBlock.INSTRUMENT).getSerializedName();
        int note = state.getValue(NoteBlock.NOTE);
        for (PeakBlockType type : PeakBlockType.values()) {
            if (type.note() == note && type.instrument().equals(instrument)) return Identifier.fromNamespaceAndPath("peaksound", "block/" + type.id());
        }
        // nummerierte Lautsprecher: custom_head + powered, Ton = Nummer - 1
        if (instrument.equals("custom_head") && note < MAX_SPEAKERS) return Identifier.fromNamespaceAndPath("peaksound", "block/speaker_" + (note + 1));
        return null;
    }

    /** Item-Definition der Mod fuer einen Stack mit PeakSound-Markierung (custom_model_data-String), sonst {@code null}. */
    static Identifier itemModel(ItemStack stack) {
        CustomModelData data = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        if (data == null || data.strings().isEmpty()) return null;
        String id = data.strings().get(0);
        return ITEM_IDS.contains(id) ? Identifier.fromNamespaceAndPath("peaksound", id) : null;
    }

    /** Umhuellt das (ggf. vom Pack gelieferte) Modell eines Traeger-Items und leitet PeakSound-Items auf unser Modell um. */
    private record CarrierItemModel(ItemModel original) implements ItemModel {
        @Override
        public void update(ItemStackRenderState state, ItemStack stack, ItemModelResolver resolver, ItemDisplayContext context,
                           ClientLevel level, ItemOwner owner, int seed) {
            Identifier ours = itemModel(stack);
            ItemModel target = ours == null ? original : Minecraft.getInstance().getModelManager().getItemModel(ours);
            target.update(state, stack, resolver, context, level, owner, seed);
        }
    }
}
