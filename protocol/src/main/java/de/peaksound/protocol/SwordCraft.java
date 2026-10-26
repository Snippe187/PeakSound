package de.peaksound.protocol;

import java.util.OptionalInt;

/**
 * Rechnung fuer das Rezept "Obsidianblock + Diamantschwert = 9 leere Platten":
 * Das Schwert kommt mit der HALBEN verbleibenden Haltbarkeit zurueck.
 */
public final class SwordCraft {
    private SwordCraft() {}

    /**
     * @param maxDurability Maximale Haltbarkeit des Schwerts (z. B. 1561)
     * @param damage        aktueller Schaden (0 = neu)
     * @return neuer Schadenswert, oder leer wenn das Schwert zerstoert wird
     */
    public static OptionalInt damageAfterCraft(int maxDurability, int damage) {
        if (maxDurability <= 0) throw new IllegalArgumentException("maxDurability");
        int remaining = maxDurability - Math.clamp(damage, 0, maxDurability);
        int halved = remaining / 2;
        if (halved < 1) return OptionalInt.empty();
        return OptionalInt.of(maxDurability - halved);
    }

    /** Wie viele Crafts sind mit Shift-Klick moeglich, bis Material oder Schwert aufgebraucht sind? */
    public static int maxCrafts(int maxDurability, int damage, int obsidianAvailable) {
        int crafts = 0;
        int currentDamage = damage;
        while (crafts < obsidianAvailable) {
            crafts++;
            OptionalInt next = damageAfterCraft(maxDurability, currentDamage);
            if (next.isEmpty()) break;
            currentDamage = next.getAsInt();
        }
        return crafts;
    }
}
