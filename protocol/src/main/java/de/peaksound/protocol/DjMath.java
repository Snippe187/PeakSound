package de.peaksound.protocol;

/**
 * Rechnung fuer das Ueberblenden des DJ-Pults. Der Crossfader verschiebt die Lautstaerke von Deck A nach Deck B mit
 * konstanter Gesamtleistung (cos/sin), damit die Mischung in der Mitte nicht leiser wird. Das Ueberblenden laeuft
 * ueber eine Zeit: Alle Spieler berechnen dieselbe Kurve aus Startzeit und Dauer, deshalb ist es synchron.
 */
public final class DjMath {
    private DjMath() {}

    /** Verstaerkung (0-1) von Deck A und Deck B bei Reglerstellung {@code position} 0-1. */
    public static double gainA(double position) { return Math.cos(clamp01(position) * Math.PI / 2); }

    public static double gainB(double position) { return Math.sin(clamp01(position) * Math.PI / 2); }

    /** Lautstaerke in Prozent (0-100) fuer ein Deck: Crossfader-Gain mal Decklautstaerke. */
    public static int deckPercent(boolean deckB, double position, int deckVolume) {
        double gain = deckB ? gainB(position) : gainA(position);
        return (int) Math.round(100.0 * gain * Math.max(0, Math.min(100, deckVolume)) / 100.0);
    }

    /** Reglerstellung zu einem Zeitpunkt der Ueberblendung (linear von {@code from} nach {@code to}). */
    public static double positionAt(double from, double to, long startMs, long durationMs, long nowMs) {
        if (durationMs <= 0 || nowMs >= startMs + durationMs) return clamp01(to);
        if (nowMs <= startMs) return clamp01(from);
        double t = (double) (nowMs - startMs) / durationMs;
        return clamp01(from + (to - from) * t);
    }

    /** Wie viele Millisekunden die Ueberblendung noch dauert (0 = fertig). */
    public static long remainingMs(long startMs, long durationMs, long nowMs) {
        return Math.max(0, startMs + durationMs - nowMs);
    }

    /** Interpolierte Lautstaerke eines Clients waehrend einer Ueberblendung (lineare Rampe in Prozent). */
    public static double rampPercent(double fromPercent, double toPercent, long elapsedMs, long durationMs) {
        if (durationMs <= 0 || elapsedMs >= durationMs) return toPercent;
        if (elapsedMs <= 0) return fromPercent;
        return fromPercent + (toPercent - fromPercent) * ((double) elapsedMs / durationMs);
    }

    private static double clamp01(double v) { return Math.max(0, Math.min(1, v)); }
}
