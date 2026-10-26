package de.peaksound.audio;

import static org.junit.jupiter.api.Assertions.*;

import de.peaksound.protocol.LightSettings;
import de.peaksound.protocol.LightSettings.LightBand;
import de.peaksound.protocol.LightSettings.LightEffect;
import de.peaksound.protocol.LightSettings.LightMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class LightLogicTest {
    private static LightSettings settings(List<Integer> colors, LightMode mode, LightEffect effect, LightBand band, int speed) {
        return new LightSettings(colors, 100, mode, effect, band, 90, 45, speed).sanitized();
    }

    private static Analysis analysis(int bass, int mid, int high) {
        // Ein Zeitschritt je 50 ms: Position 0 = Wert, danach Stille
        return new Analysis(50, new byte[] {(byte) bass, 0}, new byte[] {(byte) mid, 0}, new byte[] {(byte) high, 0}, new int[0]);
    }

    @Test
    void steadyLightIsAlwaysOnWithTheChosenColor() {
        LightSettings s = settings(List.of(0x336699), LightMode.STEADY, LightEffect.NONE, LightBand.BASS, 5);
        for (long t = 0; t < 10_000; t += 777) {
            LightLogic.Frame f = LightLogic.frame(s, null, -1, t, true);
            assertEquals(1.0, f.intensity(), 1e-9);
            assertEquals(0x336699, f.rgb());
            assertEquals(90, f.yaw());
            assertEquals(45, f.pitch());
            assertFalse(f.strobing());
        }
    }

    @Test
    void brightnessScalesTheIntensity() {
        LightSettings s = new LightSettings(List.of(0xFFFFFF), 40, LightMode.STEADY, LightEffect.NONE, LightBand.BASS, 0, 0, 5);
        assertEquals(0.4, LightLogic.frame(s, null, 0, 0, true).intensity(), 1e-9);
    }

    @Test
    void blinkingTogglesAtTheSpeedRate() {
        // speed 2 = 1 Hz = 500 ms an, 500 ms aus
        LightSettings s = settings(List.of(0xFFFFFF), LightMode.BLINK, LightEffect.NONE, LightBand.BASS, 2);
        assertEquals(1.0, LightLogic.frame(s, null, 0, 100, true).intensity());
        assertEquals(0.0, LightLogic.frame(s, null, 0, 600, true).intensity());
        assertEquals(1.0, LightLogic.frame(s, null, 0, 1100, true).intensity());
        // alle Clients mit derselben Uhr sehen dasselbe
        assertEquals(LightLogic.frame(s, null, 0, 4321, true), LightLogic.frame(s, null, 99, 4321, true));
    }

    @Test
    void beatModeFollowsTheChosenBand() {
        Analysis a = analysis(15, 8, 0);
        assertEquals(1.0, LightLogic.frame(settings(List.of(1), LightMode.BEAT, LightEffect.NONE, LightBand.BASS, 5), a, 0, 0, true).intensity(), 1e-9);
        assertEquals(8 / 15.0, LightLogic.frame(settings(List.of(1), LightMode.BEAT, LightEffect.NONE, LightBand.MID, 5), a, 0, 0, true).intensity(), 1e-9);
        assertEquals(0.0, LightLogic.frame(settings(List.of(1), LightMode.BEAT, LightEffect.NONE, LightBand.HIGH, 5), a, 0, 0, true).intensity(), 1e-9);
        // spaeter im Song: Stille
        assertEquals(0.0, LightLogic.frame(settings(List.of(1), LightMode.BEAT, LightEffect.NONE, LightBand.BASS, 5), a, 60, 0, true).intensity(), 1e-9);
        // nichts spielt: dunkel
        assertEquals(0.0, LightLogic.frame(settings(List.of(1), LightMode.BEAT, LightEffect.NONE, LightBand.BASS, 5), null, -1, 0, true).intensity(), 1e-9);
    }

    @Test
    void severalColorsAlternate() {
        LightSettings s = settings(List.of(0xFF0000, 0x00FF00, 0x0000FF), LightMode.STEADY, LightEffect.NONE, LightBand.BASS, 4); // 1 Farbe/s
        assertEquals(0xFF0000, LightLogic.frame(s, null, 0, 500, true).rgb());
        assertEquals(0x00FF00, LightLogic.frame(s, null, 0, 1500, true).rgb());
        assertEquals(0x0000FF, LightLogic.frame(s, null, 0, 2500, true).rgb());
        assertEquals(0xFF0000, LightLogic.frame(s, null, 0, 3500, true).rgb()); // wieder von vorn
    }

    @Test
    void colorCycleBlendsSmoothlyBetweenColors() {
        LightSettings s = settings(List.of(0xFF0000, 0x0000FF), LightMode.STEADY, LightEffect.COLOR_CYCLE, LightBand.BASS, 4);
        assertEquals(0xFF0000, LightLogic.frame(s, null, 0, 0, true).rgb());
        int mid = LightLogic.frame(s, null, 0, 500, true).rgb();
        assertEquals(0x80, (mid >> 16) & 255, 1);
        assertEquals(0x80, mid & 255, 1);
    }

    @Test
    void singleColorCycleRotatesTheHue() {
        LightSettings s = settings(List.of(0xFFFFFF), LightMode.STEADY, LightEffect.COLOR_CYCLE, LightBand.BASS, 10);
        int a = LightLogic.frame(s, null, 0, 0, true).rgb();
        int b = LightLogic.frame(s, null, 0, 300, true).rgb();
        assertNotEquals(a, b, "Weiss ohne Sattigung wuerde sich nie veraendern");
    }

    @Test
    void strobeFlashesUnlessTheViewerTurnedItOff() {
        LightSettings s = settings(List.of(0xFFFFFF), LightMode.STEADY, LightEffect.STROBE, LightBand.BASS, 5);
        boolean sawOn = false, sawOff = false;
        for (long t = 0; t < 2000; t += 20) {
            LightLogic.Frame f = LightLogic.frame(s, null, 0, t, true);
            assertTrue(f.strobing());
            if (f.intensity() > 0) sawOn = true; else sawOff = true;
        }
        assertTrue(sawOn && sawOff);
        // abgeschaltet: gleichmaessig hell, kein Flackern
        for (long t = 0; t < 2000; t += 20) assertEquals(1.0, LightLogic.frame(s, null, 0, t, false).intensity(), 1e-9);
    }

    @Test
    void sweepSwingsAroundTheBaseDirection() {
        LightSettings s = settings(List.of(0xFFFFFF), LightMode.STEADY, LightEffect.SWEEP, LightBand.BASS, 10);
        double min = 999, max = -999;
        for (long t = 0; t < 4000; t += 10) {
            double yaw = LightLogic.frame(s, null, 0, t, true).yaw();
            double rel = ((yaw - 90 + 540) % 360) - 180; // Abweichung von der Grundrichtung
            min = Math.min(min, rel);
            max = Math.max(max, rel);
        }
        assertEquals(-45, min, 1.5);
        assertEquals(45, max, 1.5);
    }

    @Test
    void pulseStaysInsideItsRange() {
        LightSettings s = settings(List.of(0xFFFFFF), LightMode.STEADY, LightEffect.PULSE, LightBand.BASS, 7);
        for (long t = 0; t < 5000; t += 13) {
            double i = LightLogic.frame(s, null, 0, t, true).intensity();
            assertTrue(i >= 0.1 - 1e-9 && i <= 1.0 + 1e-9, "Intensitaet " + i);
        }
    }

    @Test
    void realLightLevelStaysBetweenZeroAndFifteen() {
        for (LightMode mode : LightMode.values()) {
            for (LightEffect effect : LightEffect.values()) {
                for (int brightness : new int[] {1, 50, 100}) {
                    LightSettings s = new LightSettings(List.of(0xFFFFFF), brightness, mode, effect, LightBand.MID, 0, 0, 5);
                    int level = LightLogic.lightLevel(s, analysis(15, 15, 15), 0);
                    assertTrue(level >= 0 && level <= 15, mode + "/" + effect + ": " + level);
                }
            }
        }
        LightSettings steady = new LightSettings(List.of(0xFFFFFF), 100, LightMode.STEADY, LightEffect.NONE, LightBand.BASS, 0, 0, 5);
        assertEquals(15, LightLogic.lightLevel(steady, null, 0));
        assertEquals(1, LightLogic.lightLevel(steady.withBrightness(1), null, 0)); // bei Helligkeit > 0 mindestens Stufe 1
        LightSettings beat = steady.withMode(LightMode.BEAT);
        assertEquals(0, LightLogic.lightLevel(beat, null, 0)); // nichts spielt: dunkel
        assertEquals(15, LightLogic.lightLevel(beat, analysis(15, 0, 0), 0));
    }
}