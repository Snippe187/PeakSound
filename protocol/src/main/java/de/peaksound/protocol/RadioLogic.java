package de.peaksound.protocol;

import java.util.Collection;
import java.util.Locale;

/** Frequenzen und Rauschen des Radios (ohne Minecraft, mit Unit-Tests). Frequenzen sind ganze Zehntel-MHz. */
public final class RadioLogic {
    /** 87,5 MHz */
    public static final int MIN_FREQUENCY = 875;
    /** 108,0 MHz */
    public static final int MAX_FREQUENCY = 1080;
    /** Rauschen in Prozent, wenn kein Sender in der Naehe der Frequenz liegt. */
    public static final int FULL_STATIC = 100;
    /** Ab diesem Abstand (Zehntel-MHz) zum naechsten Sender ist es reines Rauschen. */
    public static final int STATIC_DISTANCE = 5;

    private RadioLogic() {}

    public static int clampFrequency(int tenths) {
        return Math.max(MIN_FREQUENCY, Math.min(MAX_FREQUENCY, tenths));
    }

    /** Anzeige, z. B. 984 -> "98.4 MHz" (immer mit Punkt, unabhaengig von der Systemsprache). */
    public static String format(int tenths) {
        return String.format(Locale.ROOT, "%d.%d MHz", tenths / 10, tenths % 10);
    }

    /**
     * Wie stark rauscht es bei der eingestellten Frequenz? 0 = ein Sender liegt genau darauf (klarer Empfang);
     * je weiter der naechste Sender entfernt ist, desto lauter das Rauschen; ohne Sender voll.
     */
    public static int staticLevel(int tuned, Collection<Integer> stations) {
        int nearest = Integer.MAX_VALUE;
        for (int f : stations) nearest = Math.min(nearest, Math.abs(f - tuned));
        if (nearest == 0) return 0;
        if (nearest >= STATIC_DISTANCE) return FULL_STATIC;
        return 40 + nearest * 12;
    }
}
