package de.peaksound.protocol;

import java.util.Set;
import java.util.random.RandomGenerator;

/**
 * Reihenfolge-Logik der Playlist (CD-Wechsler), unabhaengig von Minecraft. Indizes zaehlen nur Slots mit
 * spielbaren Songs; {@code -1} bedeutet "Wiedergabe endet".
 */
public final class PlaylistLogic {
    /** Hoechstzahl von Playlist-Eintraegen (Hopper + angeschlossene Kisten). */
    public static final int MAX_ENTRIES = 128;

    private PlaylistLogic() {}

    /**
     * Der Song ist zu Ende: welcher kommt als Naechstes?
     *
     * @param size    Anzahl der Eintraege
     * @param current Index des gerade beendeten Songs (-1 = keiner)
     * @param played  bereits gespielte Indizes in diesem Durchlauf (nur bei Shuffle genutzt; wird veraendert)
     */
    public static int next(int size, int current, LoopMode loop, boolean shuffle, Set<Integer> played, RandomGenerator random) {
        if (size <= 0) return -1;
        boolean currentValid = current >= 0 && current < size;
        if (loop == LoopMode.ONE && currentValid) return current;
        if (shuffle) return nextShuffled(size, currentValid ? current : -1, loop, played, random);
        int n = currentValid ? current + 1 : 0;
        if (n < size) return n;
        return loop == LoopMode.ALL ? 0 : -1;
    }

    /** Der Spieler drueckt "Skip": immer zum naechsten Song, auch bei "Ein Song wiederholen". */
    public static int skip(int size, int current, LoopMode loop, boolean shuffle, Set<Integer> played, RandomGenerator random) {
        LoopMode effective = loop == LoopMode.ONE ? LoopMode.ALL : loop;
        return next(size, current, effective, shuffle, played, random);
    }

    private static int nextShuffled(int size, int current, LoopMode loop, Set<Integer> played, RandomGenerator random) {
        if (current >= 0) played.add(current);
        int remaining = 0;
        for (int i = 0; i < size; i++) if (!played.contains(i)) remaining++;
        if (remaining == 0) {
            if (loop != LoopMode.ALL) return -1;
            played.clear(); // neuer Durchlauf
            if (current >= 0) played.add(current); // derselbe Song nicht direkt nochmal (ausser es gibt nur einen)
            remaining = size - played.size();
            if (remaining == 0) {
                played.clear();
                return current >= 0 ? current : 0;
            }
        }
        int pick = random.nextInt(remaining);
        for (int i = 0; i < size; i++) {
            if (played.contains(i)) continue;
            if (pick-- == 0) return i;
        }
        return -1;
    }

    /** Nachbar-Index fuer "nach oben/unten verschieben", oder -1 am Rand. */
    public static int swapTarget(int index, boolean up, int size) {
        int target = up ? index - 1 : index + 1;
        return index < 0 || index >= size || target < 0 || target >= size ? -1 : target;
    }

    /** Wo liegt der laufende Song nach dem Tausch zweier Eintraege? */
    public static int followSwap(int current, int a, int b) {
        if (current == a) return b;
        if (current == b) return a;
        return current;
    }
}
