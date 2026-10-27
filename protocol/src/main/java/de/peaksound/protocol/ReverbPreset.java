package de.peaksound.protocol;

/**
 * Raumhall eines Lautsprechers. Der Client erzeugt ihn mit OpenAL-Effekten (EFX); Parameter nach den Standard-Reverb-
 * Eigenschaften von OpenAL: Nachhallzeit in Sekunden, Verhaeltnis Hoehen/Tiefen, Dichte und Pegel der Echos.
 */
public enum ReverbPreset {
    OFF("off", 0f, 0f, 0f),
    /** Wohnzimmer: kurzer, trockener Hall. */
    ROOM("room", 0.9f, 0.85f, 0.35f),
    /** Konzertsaal: lange, weiche Nachhallfahne. */
    HALL("hall", 2.8f, 0.7f, 0.55f),
    /** Hoehle: sehr lang, dumpf. */
    CAVE("cave", 5.5f, 0.45f, 0.7f),
    /** Badezimmer: mittellang, hell. */
    BATH("bath", 1.7f, 1.25f, 0.6f);

    private final String key;
    private final float decaySeconds;
    private final float hfRatio;
    private final float lateGain;

    ReverbPreset(String key, float decaySeconds, float hfRatio, float lateGain) {
        this.key = key;
        this.decaySeconds = decaySeconds;
        this.hfRatio = hfRatio;
        this.lateGain = lateGain;
    }

    public String key() { return key; }

    public float decaySeconds() { return decaySeconds; }

    public float hfRatio() { return hfRatio; }

    public float lateGain() { return lateGain; }

    public ReverbPreset next() { return values()[(ordinal() + 1) % values().length]; }

    public static ReverbPreset byOrdinal(int o) {
        ReverbPreset[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Raumhall");
        return v[o];
    }
}