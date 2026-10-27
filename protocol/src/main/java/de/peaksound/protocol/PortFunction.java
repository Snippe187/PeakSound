package de.peaksound.protocol;

/**
 * Was eine Comparator-Seite (Norden, Osten, Sueden, Westen) einer Jukebox als Redstone-Signal ausgibt.
 * Die ersten vier entsprechen dem bisherigen Equalizer.
 */
public enum PortFunction {
    BASS("bass"), MID("mid"), HIGH("high"), OVERALL("overall"),
    /** 15, solange etwas spielt, sonst 0. */
    RUNNING("running"),
    /** Kurzer Impuls (15), wenn ein Song endet. */
    SONG_END("song_end"),
    /** Kurzer Impuls (15) bei jedem erkannten Taktschlag. */
    BEAT("beat"),
    OFF("off"),
    /** Nimmt beschriebene Platten aus einem Hopper oder Behaelter an dieser Seite ins Jukebox-Inventar. */
    ITEM_IN("item_in"),
    /** Legt Platten aus dem Jukebox-Inventar in einen Hopper oder Behaelter an dieser Seite. */
    ITEM_OUT("item_out");

    private final String key;

    PortFunction(String key) { this.key = key; }

    public String key() { return key; }

    /** Seiten 0-3 (N, O, S, W) duerfen alles, oben/unten (4, 5) nur Aus und die Item-Modi. */
    public static java.util.List<PortFunction> allowedFor(int side) {
        return side < 4 ? java.util.List.of(values()) : java.util.List.of(OFF, ITEM_IN, ITEM_OUT);
    }

    public PortFunction next(int side) {
        java.util.List<PortFunction> a = allowedFor(side);
        int i = a.indexOf(this);
        return a.get((i + 1) % a.size());
    }

    public PortFunction previous(int side) {
        java.util.List<PortFunction> a = allowedFor(side);
        int i = a.indexOf(this);
        return a.get((i + a.size() - 1) % a.size());
    }

    public boolean item() { return this == ITEM_IN || this == ITEM_OUT; }

    static PortFunction byOrdinal(int o) {
        PortFunction[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Ausgangsfunktion");
        return v[o];
    }

    /** Standard der sechs Seiten Norden, Osten, Sueden, Westen, oben, unten. */
    public static java.util.List<PortFunction> defaultPorts() {
        return java.util.List.of(BASS, MID, HIGH, OVERALL, OFF, OFF);
    }
}