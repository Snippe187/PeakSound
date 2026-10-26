package de.peaksound.audio;

import de.peaksound.protocol.LightSettings;
import de.peaksound.protocol.LightSettings.LightBand;
import de.peaksound.protocol.LightSettings.LightEffect;
import java.util.List;

/**
 * Berechnet, wie ein Scheinwerfer zu einem Zeitpunkt aussieht. Reine Funktionen von Einstellungen, Musikanalyse und
 * Uhrzeit: Alle Clients bekommen dasselbe Ergebnis, weil sie dieselbe Serveruhr und dieselbe Analyse benutzen.
 * Der Server nutzt dieselbe Logik fuer das echte Licht (Lichtstufe 0-15).
 */
public final class LightLogic {
    private LightLogic() {}

    /**
     * @param rgb       Farbe 0xRRGGBB
     * @param intensity Helligkeit 0-1 (inklusive Einstellung "Helligkeit")
     * @param yaw       Richtung des Strahls in Grad (inklusive Schwenk)
     * @param pitch     Neigung in Grad
     * @param strobing  wahr, wenn gerade der Strobo-Effekt aktiv ist (fuer die Warnung)
     */
    public record Frame(int rgb, double intensity, double yaw, double pitch, boolean strobing) {}

    /**
     * @param analysis   Analyse des laufenden Songs, oder null (nichts spielt)
     * @param positionMs Songposition in Millisekunden (nur mit Analyse sinnvoll)
     * @param clockMs    Serveruhr in Millisekunden, Grundlage fuer Blinken und Effekte (laufen auch ohne Musik)
     * @param allowStrobe false, wenn der Spieler Strobo abgeschaltet hat: Der Effekt wird dann gleichmaessig hell
     */
    public static Frame frame(LightSettings s, Analysis analysis, long positionMs, long clockMs, boolean allowStrobe) {
        double t = clockMs / 1000.0;
        double base = s.brightness() / 100.0;

        double mode = switch (s.mode()) {
            case STEADY -> 1.0;
            case BLINK -> Math.floor(t * s.speed() * 0.5 * 2) % 2 == 0 ? 1.0 : 0.0;
            case BEAT -> analysis == null ? 0.0 : analysis.level(toBand(s.band()), positionMs) / 15.0;
        };

        double effect = 1.0;
        boolean strobing = false;
        double yaw = s.yaw();
        switch (s.effect()) {
            case STROBE -> {
                strobing = true;
                if (allowStrobe) effect = Math.floor(t * s.speed() * 3) % 2 == 0 ? 1.0 : 0.0;
            }
            case SWEEP -> yaw = s.yaw() + 45.0 * Math.sin(2 * Math.PI * s.speed() * 0.05 * t);
            case PULSE -> effect = 0.55 + 0.45 * Math.sin(2 * Math.PI * s.speed() * 0.2 * t);
            default -> { /* kein Zusatz */ }
        }
        return new Frame(color(s, t), base * mode * effect, Math.floorMod((long) Math.round(yaw), 360L), s.pitch(), strobing);
    }

    /** Mehrere Farben wechseln sich ab (hart); mit dem Effekt "Farbwechsel" blenden sie weich ueber, eine Farbe dreht den Farbton. */
    static int color(LightSettings s, double t) {
        List<Integer> colors = s.colors();
        int n = colors.size();
        boolean cycle = s.effect() == LightEffect.COLOR_CYCLE;
        if (n == 1) {
            return cycle ? rotateHue(colors.get(0), t * s.speed() * 0.1) : colors.get(0);
        }
        double pos = t * s.speed() * 0.25;
        int index = (int) Math.floor(pos) % n;
        if (!cycle) return colors.get(index);
        double fraction = pos - Math.floor(pos);
        return mix(colors.get(index), colors.get((index + 1) % n), fraction);
    }

    /** Server: Lichtstufe 0-15 fuer das echte Licht (langsam, deshalb ohne Blinken und Strobo-Takt). */
    public static int lightLevel(LightSettings s, Analysis analysis, long positionMs) {
        double mode = switch (s.mode()) {
            case STEADY -> 1.0;
            case BLINK -> 0.6;
            case BEAT -> analysis == null ? 0.0 : analysis.level(toBand(s.band()), positionMs) / 15.0;
        };
        double effect = switch (s.effect()) {
            case STROBE -> 0.6;
            case PULSE -> 0.75;
            default -> 1.0;
        };
        double level = 15.0 * (s.brightness() / 100.0) * mode * effect;
        if (level <= 0) return 0;
        return Math.max(1, Math.min(15, (int) Math.round(level)));
    }

    private static Analysis.Band toBand(LightBand band) {
        return switch (band) {
            case BASS -> Analysis.Band.BASS;
            case MID -> Analysis.Band.MID;
            case HIGH -> Analysis.Band.HIGH;
        };
    }

    private static int mix(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /** Dreht den Farbton um {@code turns} Umdrehungen. Weisse und graue Farben bekommen Saettigung, sonst gaebe es nichts zu sehen. */
    private static int rotateHue(int rgb, double turns) {
        double r = ((rgb >> 16) & 255) / 255.0, g = ((rgb >> 8) & 255) / 255.0, b = (rgb & 255) / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        double hue = 0;
        if (delta > 1e-9) {
            if (max == r) hue = ((g - b) / delta) % 6;
            else if (max == g) hue = (b - r) / delta + 2;
            else hue = (r - g) / delta + 4;
            hue /= 6;
            if (hue < 0) hue += 1;
        }
        double saturation = max <= 1e-9 ? 0 : delta / max;
        if (saturation < 0.2) saturation = 1;
        double value = Math.max(max, 0.6);
        return hsvToRgb(((hue + turns) % 1.0 + 1.0) % 1.0, saturation, value);
    }

    private static int hsvToRgb(double h, double s, double v) {
        double c = v * s;
        double x = c * (1 - Math.abs((h * 6) % 2 - 1));
        double m = v - c;
        double r, g, b;
        int sector = (int) Math.floor(h * 6) % 6;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return ((int) Math.round((r + m) * 255) << 16) | ((int) Math.round((g + m) * 255) << 8) | (int) Math.round((b + m) * 255);
    }
}