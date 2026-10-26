package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SwordCraftTest {
    @Test
    void newSwordKeepsHalfDurability() {
        // 1561 Haltbarkeit -> 780 uebrig -> Schaden 781
        assertEquals(781, SwordCraft.damageAfterCraft(1561, 0).getAsInt());
    }

    @Test
    void halvesRemainingNotMax() {
        // 100 von 1561 uebrig (Schaden 1461) -> 50 uebrig -> Schaden 1511
        assertEquals(1511, SwordCraft.damageAfterCraft(1561, 1461).getAsInt());
    }

    @Test
    void swordBreaksWhenHalfIsZero() {
        assertTrue(SwordCraft.damageAfterCraft(1561, 1560).isEmpty()); // 1 uebrig -> 0
        assertTrue(SwordCraft.damageAfterCraft(1561, 1561).isEmpty());
    }

    @Test
    void shiftClickStopsWhenSwordBreaks() {
        // 1561 -> 780 -> 390 -> 195 -> 97 -> 48 -> 24 -> 12 -> 6 -> 3 -> 1 -> 0 (bricht)
        assertEquals(11, SwordCraft.maxCrafts(1561, 0, 64));
    }

    @Test
    void shiftClickStopsWhenObsidianRunsOut() {
        assertEquals(3, SwordCraft.maxCrafts(1561, 0, 3));
        assertEquals(0, SwordCraft.maxCrafts(1561, 0, 0));
    }

    @Test
    void rejectsInvalidMax() {
        assertThrows(IllegalArgumentException.class, () -> SwordCraft.damageAfterCraft(0, 0));
    }
}
