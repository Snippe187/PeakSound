package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChainMathTest {
    @Test
    void specExample() {
        // 16 Bloecke / 50 %, Reichweite auf 24 -> Lautstaerke 75 %
        assertEquals(new ChainMath.Pair(24, 75), ChainMath.adjustRange(16, 50, 24));
    }

    @Test
    void rangeDownScalesVolumeDown() {
        assertEquals(new ChainMath.Pair(8, 25), ChainMath.adjustRange(16, 50, 8));
    }

    @Test
    void rangeIsCappedWhenVolumeWouldExceed100() {
        // 20 / 80 %: bei 25 Bloecken sind 100 % erreicht, mehr geht nicht
        assertEquals(new ChainMath.Pair(25, 100), ChainMath.adjustRange(20, 80, 30));
        assertEquals(new ChainMath.Pair(25, 100), ChainMath.adjustRange(20, 80, 25));
    }

    @Test
    void rangeClampsAtOne() {
        ChainMath.Pair p = ChainMath.adjustRange(16, 50, -5);
        assertEquals(1, p.range());
        assertEquals(3, p.volume()); // 1 * 3.125 gerundet
    }

    @Test
    void volumeChangeMovesRange() {
        assertEquals(new ChainMath.Pair(24, 75), ChainMath.adjustVolume(16, 50, 75));
        assertEquals(new ChainMath.Pair(8, 25), ChainMath.adjustVolume(16, 50, 25));
    }

    @Test
    void volumeIsCappedWhenRangeWouldExceed30() {
        // 20 / 50 %: Verhaeltnis 0.4 Bloecke je %; 30 Bloecke entsprechen 75 %
        ChainMath.Pair p = ChainMath.adjustVolume(20, 50, 100);
        assertEquals(30, p.range());
        assertEquals(75, p.volume());
    }

    @Test
    void zeroVolumeKeepsRangeAndSurvivesRoundTrip() {
        assertEquals(new ChainMath.Pair(16, 0), ChainMath.adjustVolume(16, 50, 0));
        // Ohne Verhaeltnis (Lautstaerke 0) wird nur die Reichweite gesetzt
        assertEquals(new ChainMath.Pair(10, 0), ChainMath.adjustRange(16, 0, 10));
        assertEquals(new ChainMath.Pair(16, 40), ChainMath.adjustVolume(16, 0, 40));
    }

    @Test
    void resultsAlwaysStayInBounds() {
        for (int range = 1; range <= 30; range++) {
            for (int volume = 0; volume <= 100; volume += 7) {
                for (int target = -10; target <= 140; target += 9) {
                    ChainMath.Pair a = ChainMath.adjustRange(range, volume, target);
                    assertTrue(a.range() >= 1 && a.range() <= 30, "range " + a);
                    assertTrue(a.volume() >= 0 && a.volume() <= 100, "volume " + a);
                    ChainMath.Pair b = ChainMath.adjustVolume(range, volume, target);
                    assertTrue(b.range() >= 1 && b.range() <= 30, "range " + b);
                    assertTrue(b.volume() >= 0 && b.volume() <= 100, "volume " + b);
                }
            }
        }
    }
}
