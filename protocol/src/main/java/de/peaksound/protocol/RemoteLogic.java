package de.peaksound.protocol;

/** Entscheidet, wann und wie eine Fernbedienung auf ihr Redstone-Signal reagiert (ohne Minecraft, mit Unit-Tests). */
public final class RemoteLogic {
    private RemoteLogic() {}

    /** Ergebnis einer Auswertung: ob die Aktion jetzt laeuft und mit welchem Wert (Signalstaerke nach Umkehr). */
    public record Trigger(boolean fire, int strength) {}

    /**
     * @param previous Signalstaerke beim letzten Mal (0-15)
     * @param current  Signalstaerke jetzt (0-15)
     */
    public static Trigger evaluate(RemoteSettings settings, int previous, int current) {
        int prev = clamp(previous), now = clamp(current);
        if (settings.invert()) {
            prev = 15 - prev;
            now = 15 - now;
        }
        if (settings.action().analog()) return new Trigger(now != prev, now);          // jede Aenderung der Staerke
        if (settings.action() == RemoteAction.PLAY_LEVEL) return new Trigger(now != 0 != (prev != 0), now); // Wechsel an <-> aus
        return new Trigger(prev == 0 && now > 0, now);                                  // steigende Flanke
    }

    /** Lautstaerke in Prozent fuer Signalstaerke 0-15. */
    public static int volumeFor(int strength) {
        return Math.round(clamp(strength) * 100f / 15f);
    }

    /** Der Playlist-Index (0-basiert) zu Signalstaerke 1-15, oder -1 bei 0 oder wenn die Playlist kuerzer ist. */
    public static int songIndexFor(int strength, int playlistSize) {
        int i = clamp(strength) - 1;
        return i >= 0 && i < playlistSize ? i : -1;
    }

    private static int clamp(int v) { return Math.max(0, Math.min(15, v)); }
}