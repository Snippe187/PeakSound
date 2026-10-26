package de.peaksound.protocol;

/**
 * Bauart eines Lautsprechers: bestimmt, welchen Frequenzbereich er wiedergibt. Zusammen mit der Position
 * ({@link SpeakerChannel}) ergibt das z. B. einen Hochtoener vorn links oder einen Subwoofer hinten.
 */
public enum SpeakerKind {
    /** Vollbereich: spielt alles (die Position des Kanals bestimmt weiterhin das Signal). */
    FULL_RANGE("full", 0, false),
    /** Tieftoener: nur Tiefen und untere Mitten (bis etwa 500 Hz). */
    WOOFER("woofer", 500, false),
    /** Hochtoener: nur Hoehen (ab etwa 2,5 kHz). */
    TWEETER("tweeter", 2500, true),
    /** Subwoofer: nur tiefster Bass (bis etwa 120 Hz). */
    SUBWOOFER("subwoofer", 120, false);

    private final String key;
    private final double cutoffHz;
    private final boolean highPass;

    SpeakerKind(String key, double cutoffHz, boolean highPass) {
        this.key = key;
        this.cutoffHz = cutoffHz;
        this.highPass = highPass;
    }

    public String key() { return key; }

    /** Grenzfrequenz in Hz; 0 = keine Filterung. */
    public double cutoffHz() { return cutoffHz; }

    /** true: Hochpass (Hoehen), false: Tiefpass (Tiefen). */
    public boolean highPass() { return highPass; }

    public SpeakerKind next() { return values()[(ordinal() + 1) % values().length]; }

    public SpeakerKind previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    public static SpeakerKind byOrdinal(int o) {
        SpeakerKind[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Lautsprecher-Bauart");
        return v[o];
    }
}