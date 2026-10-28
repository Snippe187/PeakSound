package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DjMathTest {
    @Test
    void crossfaderEndsAreExclusive() {
        assertEquals(1.0, DjMath.gainA(0), 1e-9);
        assertEquals(0.0, DjMath.gainB(0), 1e-9);
        assertEquals(0.0, DjMath.gainA(1), 1e-9);
        assertEquals(1.0, DjMath.gainB(1), 1e-9);
    }

    @Test
    void equalPowerKeepsTheLoudnessConstant() {
        for (double p = 0; p <= 1.0; p += 0.05) {
            double power = DjMath.gainA(p) * DjMath.gainA(p) + DjMath.gainB(p) * DjMath.gainB(p);
            assertEquals(1.0, power, 1e-9, "Position " + p);
        }
        // in der Mitte etwa -3 dB je Deck
        assertEquals(0.7071, DjMath.gainA(0.5), 1e-3);
    }

    @Test
    void deckPercentCombinesCrossfaderAndDeckVolume() {
        assertEquals(100, DjMath.deckPercent(false, 0, 100));
        assertEquals(0, DjMath.deckPercent(true, 0, 100));
        assertEquals(50, DjMath.deckPercent(false, 0, 50));
        assertEquals(71, DjMath.deckPercent(true, 0.5, 100));
        assertEquals(0, DjMath.deckPercent(false, 0.5, 0));
    }

    @Test
    void positionIsClampedAndLinearOverTheFadeTime() {
        assertEquals(0.0, DjMath.positionAt(0, 1, 1000, 4000, 500), 1e-9);   // vor dem Start
        assertEquals(0.25, DjMath.positionAt(0, 1, 1000, 4000, 2000), 1e-9); // ein Viertel
        assertEquals(0.5, DjMath.positionAt(0, 1, 1000, 4000, 3000), 1e-9);
        assertEquals(1.0, DjMath.positionAt(0, 1, 1000, 4000, 5000), 1e-9);  // fertig
        assertEquals(1.0, DjMath.positionAt(0, 1, 1000, 4000, 99_999), 1e-9);
        assertEquals(0.3, DjMath.positionAt(0.9, 0.3, 0, 0, 10), 1e-9);      // 0 s = sofort
        assertEquals(0.0, DjMath.positionAt(-5, 9, 0, 1000, 0), 1e-9);       // Werte ausserhalb 0-1 werden begrenzt
    }

    @Test
    void remainingTimeAndRamp() {
        assertEquals(3000, DjMath.remainingMs(1000, 4000, 2000));
        assertEquals(0, DjMath.remainingMs(1000, 4000, 9000));
        // Gleiche Gesamtleistung: in der Mitte haben beide Decks je cos(45 Grad) Verstaerkung
        assertEquals(100 * Math.cos(Math.PI / 4), DjMath.rampPercent(100, 0, 500, 1000), 1e-9);
        assertEquals(100 * Math.sin(Math.PI / 4), DjMath.rampPercent(0, 100, 500, 1000), 1e-9);
        double a = DjMath.rampPercent(100, 0, 300, 1000) / 100, b = DjMath.rampPercent(0, 100, 300, 1000) / 100;
        assertEquals(1.0, a * a + b * b, 1e-9);
        assertEquals(0.0, DjMath.rampPercent(100, 0, 2000, 1000), 1e-9);
        assertEquals(100.0, DjMath.rampPercent(100, 0, -5, 1000), 1e-9);
        assertEquals(30.0, DjMath.rampPercent(80, 30, 0, 0), 1e-9);
    }

    @Test
    void lateJoinerStartsAtTheCurrentPointOfTheSameCurve() {
        // Fade A->B ueber 4 s ab t=1000. Ein Spieler, der bei t=3000 dazukommt, startet bei der Haelfte der Rampe.
        double position = DjMath.positionAt(0, 1, 1000, 4000, 3000);
        int volumeAtJoin = DjMath.deckPercent(false, position, 100);
        assertEquals(71, volumeAtJoin);
        assertEquals(2000, DjMath.remainingMs(1000, 4000, 3000));
    }

    @Test
    void djSettingsAreSanitized() {
        DjSettings s = new DjSettings(-5, 99, 250, -1, true).sanitized();
        assertEquals(0, s.crossfader());
        assertEquals(30, s.fadeSeconds());
        assertEquals(100, s.volumeA());
        assertEquals(0, s.volumeB());
        assertTrue(s.swapped());
    }

    @Test
    void onlyTheDjDeskKeepsNetworksApart() {
        for (PeakBlockType t : PeakBlockType.values()) assertEquals(t != PeakBlockType.DJ_DESK && t != PeakBlockType.SONG_LIBRARY && t != PeakBlockType.RADIO_RECEIVER && t != PeakBlockType.DISPLAY,
                t.bridgesNetworks(), t.name());
        assertEquals(PeakItemType.SPOTLIGHT, PeakItemType.forBlock(PeakBlockType.SPOTLIGHT));
        assertEquals(PeakItemType.DJ_DESK, PeakItemType.forBlock(PeakBlockType.DJ_DESK));
    }
}