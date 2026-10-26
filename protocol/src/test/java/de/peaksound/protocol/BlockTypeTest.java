package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockTypeTest {
    @Test
    void blockStatesAreUniqueAndValid() {
        Set<String> seen = new HashSet<>();
        for (PeakBlockType t : PeakBlockType.values()) {
            assertTrue(t.note() >= 0 && t.note() <= 24, "Ton 0-24: " + t);
            assertTrue(seen.add(t.instrument() + "/" + t.note()), "Zustand doppelt: " + t);
            assertEquals(t, PeakBlockType.byId(t.id()).orElseThrow());
        }
    }

    @Test
    void blockItemsPointToTheirBlock() {
        assertEquals(PeakBlockType.JUKEBOX, PeakItemType.JUKEBOX.block().orElseThrow());
        assertEquals(PeakBlockType.SPEAKER, PeakItemType.SPEAKER.block().orElseThrow());
        assertTrue(PeakItemType.PEAK_TUNER.block().isEmpty());
    }
}
