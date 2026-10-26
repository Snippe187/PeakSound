package de.peaksound.protocol;

/** Wiederholmodus der Playlist. */
public enum LoopMode {
    /** Nach dem letzten Song stoppt die Jukebox. */
    OFF,
    /** Die ganze Playlist wiederholt sich. */
    ALL,
    /** Der aktuelle Song wiederholt sich. */
    ONE;

    public LoopMode next() { return values()[(ordinal() + 1) % values().length]; }

    static LoopMode byOrdinal(int o) {
        LoopMode[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Loop-Modus");
        return v[o];
    }
}
