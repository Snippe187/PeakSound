package de.peaksound.protocol;

/**
 * Rechnung fuer den Ketten-Schalter der Jukebox: Reichweite und Lautstaerke bewegen sich proportional.
 * Beispiel: 16 Bloecke / 50 % -> Reichweite 24 ergibt Lautstaerke 75 %.
 * An den Grenzen wird der Wert, den der Spieler bewegt, so weit begrenzt, dass der andere im Bereich bleibt
 * und das Verhaeltnis erhalten bleibt (z. B. 20 Bloecke / 80 %: mehr als 25 Bloecke gehen nicht).
 */
public final class ChainMath {
    private ChainMath() {}

    public record Pair(int range, int volume) {}

    /** Spieler aendert die Reichweite; die Lautstaerke folgt. */
    public static Pair adjustRange(int range, int volume, int newRange) {
        int target = clamp(newRange, Limits.MIN_RANGE, Limits.MAX_RANGE);
        if (volume <= 0 || range <= 0) return new Pair(target, Math.max(0, volume)); // kein Verhaeltnis bekannt
        double ratio = (double) volume / range;
        int newVolume = (int) Math.round(target * ratio);
        if (newVolume > 100) {
            // Lautstaerke waere ueber 100 %: Reichweite so weit begrenzen, dass 100 % erreicht werden
            target = clamp((int) Math.floor(100.0 / ratio), Limits.MIN_RANGE, Limits.MAX_RANGE);
            newVolume = clamp((int) Math.round(target * ratio), 0, 100);
        }
        return new Pair(target, newVolume);
    }

    /** Spieler aendert die Lautstaerke; die Reichweite folgt. Bei 0 % bleibt die Reichweite unveraendert. */
    public static Pair adjustVolume(int range, int volume, int newVolume) {
        int target = clamp(newVolume, 0, 100);
        if (volume <= 0 || target == 0) return new Pair(range, target);
        double ratio = (double) range / volume;
        int newRange = (int) Math.round(target * ratio);
        if (newRange > Limits.MAX_RANGE) {
            newRange = Limits.MAX_RANGE;
            target = clamp((int) Math.round(newRange / ratio), 0, 100);
        } else if (newRange < Limits.MIN_RANGE) {
            newRange = Limits.MIN_RANGE;
            target = clamp((int) Math.round(newRange / ratio), 0, 100);
        }
        return new Pair(newRange, target);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
