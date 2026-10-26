package de.peaksound.protocol;

/**
 * Die Kanaele eines 7.1.4-Systems (12 Lautsprecher-Positionen). Die Reihenfolge der Konstanten ist Teil des
 * Netzwerkprotokolls und darf nicht veraendert werden.
 */
public enum SpeakerChannel {
    FRONT_LEFT("FL"), FRONT_RIGHT("FR"), CENTER("C"), SUBWOOFER("LFE"),
    SIDE_LEFT("SL"), SIDE_RIGHT("SR"), REAR_LEFT("BL"), REAR_RIGHT("BR"),
    TOP_FRONT_LEFT("TFL"), TOP_FRONT_RIGHT("TFR"), TOP_REAR_LEFT("TBL"), TOP_REAR_RIGHT("TBR");

    private final String shortName;

    SpeakerChannel(String shortName) { this.shortName = shortName; }

    /** Kurzname wie im Heimkino ("FL", "LFE", "TBR"). */
    public String shortName() { return shortName; }

    /** Uebersetzungsschluessel-Suffix, z. B. "front_left". */
    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }

    /**
     * Reihenfolge, in der neue Lautsprecher eines Systems automatisch belegt werden: erst Stereo, dann Center,
     * Seiten, hinten, Subwoofer, zuletzt die Decke.
     */
    public static final SpeakerChannel[] DEFAULT_ORDER = {
            FRONT_LEFT, FRONT_RIGHT, CENTER, SIDE_LEFT, SIDE_RIGHT, REAR_LEFT, REAR_RIGHT, SUBWOOFER,
            TOP_FRONT_LEFT, TOP_FRONT_RIGHT, TOP_REAR_LEFT, TOP_REAR_RIGHT};

    public SpeakerChannel next() { return values()[(ordinal() + 1) % values().length]; }

    public SpeakerChannel previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    static SpeakerChannel byOrdinal(int o) {
        SpeakerChannel[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Kanal");
        return v[o];
    }
}
