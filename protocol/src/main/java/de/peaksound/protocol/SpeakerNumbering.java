package de.peaksound.protocol;

/**
 * Die Nummer eines Lautsprechers (1, 2, 3 ... innerhalb seines Systems) steht auf seiner Textur. Weil der
 * Lautsprecher ein Notenblock-Zustand ist, wird die Nummer im Zustand kodiert: Instrument {@code custom_head},
 * Ton = Nummer - 1, powered = true. Diese Kombination kommt im normalen Spiel praktisch nie vor. Die Mod
 * ordnet jedem Zustand eine Textur mit aufgedruckter Nummer zu.
 *
 * <p>Ein Lautsprecher ohne Nummer (nicht mit einer Jukebox verbunden) bleibt im Grundzustand
 * {@link PeakBlockType#SPEAKER}.
 */
public final class SpeakerNumbering {
    private SpeakerNumbering() {}

    /** Hoechste Lautsprecher-Nummer, also Obergrenze fuer Lautsprecher pro System (Ton 0-24). */
    public static final int MAX_NUMBER = 25;

    public static final String INSTRUMENT = "custom_head";

    /** Notenblock-Ton fuer die Nummer (1-25), sonst Fehler. */
    public static int noteFor(int number) {
        if (number < 1 || number > MAX_NUMBER) throw new IllegalArgumentException("Nummer " + number);
        return number - 1;
    }

    /** Nummer zu einem Notenblock-Zustand, oder 0, wenn der Zustand keine Lautsprecher-Nummer ist. */
    public static int numberOf(String instrument, int note, boolean powered) {
        if (!powered || !INSTRUMENT.equals(instrument) || note < 0 || note >= MAX_NUMBER) return 0;
        return note + 1;
    }
}
