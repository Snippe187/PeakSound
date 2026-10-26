package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.SpeakerNumbering;
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
        setState(block, type.instrument(), type.note());
        registry.register(block, type);
    }

    private static void setState(Block block, String instrument, int note) {
        NoteBlock data = (NoteBlock) Material.NOTE_BLOCK.createBlockData();
        data.setInstrument(Instrument.valueOf(instrument.toUpperCase(Locale.ROOT)));
        data.setNote(new Note(note));
        data.setPowered(true);
        block.setBlockData(data, false);
    }

    /** Die Nummer, die ein Lautsprecher-Block gerade anzeigt (0 = keine). */
    static int speakerNumber(Block block) {
        if (block.getType() != Material.NOTE_BLOCK) return 0;
        NoteBlock data = (NoteBlock) block.getBlockData();
        return SpeakerNumbering.numberOf(data.getInstrument().name().toLowerCase(Locale.ROOT), data.getNote().getId(), data.isPowered());
    }

    /** Zeigt die Nummer auf dem Lautsprecher (die Mod druckt sie auf die Textur). 0 = Grundzustand ohne Nummer. */
    static void setSpeakerNumber(Block block, int number) {
        if (number > 0) setState(block, SpeakerNumbering.INSTRUMENT, SpeakerNumbering.noteFor(number));
        else setState(block, PeakBlockType.SPEAKER.instrument(), PeakBlockType.SPEAKER.note());
    }
}
