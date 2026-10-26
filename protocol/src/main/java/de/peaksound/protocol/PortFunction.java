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
    OFF("off");

    private final String key;

    PortFunction(String key) { this.key = key; }

    public String key() { return key; }

    public PortFunction next() { return values()[(ordinal() + 1) % values().length]; }

    public PortFunction previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    static PortFunction byOrdinal(int o) {
        PortFunction[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Ausgangsfunktion");
        return v[o];
    }

    /** Standard der vier Seiten Norden, Osten, Sueden, Westen. */
    public static java.util.List<PortFunction> defaultPorts() {
        return java.util.List.of(BASS, MID, HIGH, OVERALL);
    }
}