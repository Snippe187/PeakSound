package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import java.util.Locale;
import org.bukkit.Instrument;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.NoteBlock;

/** Versetzt einen Block in den PeakSound-Zustand und meldet ihn an. */
final class PeakBlocks {
    private PeakBlocks() {}

    /**
     * Macht den Block zu einem PeakSound-Block: Notenblock im festen Zustand (Instrument, Ton, powered), den die Mod
     * als eigenes Modell zeigt, und Eintrag in der {@link BlockRegistry}.
     */
    static void install(Block block, PeakBlockType type, BlockRegistry registry) {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(Instrument.valueOf(type.instrument().toUpperCase(Locale.ROOT)));
        data.setNote(new Note(type.note()));
        data.setPowered(true);
        block.setBlockData(data, false);
        registry.register(block, type);
    }
}
