package de.peaksound.protocol;

/** Aktion einer Musik-Fernbedienung, ausgeloest durch ein Redstone-Signal an ihr. */
public enum RemoteAction {
    /** Signal an = spielt (Playlist startet oder Platte laeuft weiter), Signal aus = stoppt. */
    PLAY_LEVEL("play_level", false),
    /** Jede steigende Flanke wechselt zwischen Pause und Weiter (die Position bleibt erhalten). */
    PAUSE_TOGGLE("pause_toggle", false),
    /** Steigende Flanke: naechster Song. */
    SKIP("skip", false),
    /** Steigende Flanke: voriger Song. */
    PREVIOUS("previous", false),
    /** Steigende Flanke: Zufallswiedergabe an/aus. */
    SHUFFLE("shuffle", false),
    /** Steigende Flanke: Wiederholen Aus, Playlist, Song. */
    REPEAT("repeat", false),
    /** Signalstaerke 1-15 spielt den Eintrag mit dieser Nummer. */
    SONG_BY_STRENGTH("song_by_strength", true),
    /** Signalstaerke 0-15 stellt die Lautstaerke auf 0 bis 100 %. */
    VOLUME_BY_STRENGTH("volume_by_strength", true);

    private final String key;
    private final boolean analog;

    RemoteAction(String key, boolean analog) {
        this.key = key;
        this.analog = analog;
    }

    public String key() { return key; }

    /** Wertet die Signalstaerke aus (statt nur an/aus). */
    public boolean analog() { return analog; }

    public RemoteAction next() { return values()[(ordinal() + 1) % values().length]; }

    public RemoteAction previous() { return values()[(ordinal() + values().length - 1) % values().length]; }

    static RemoteAction byOrdinal(int o) {
        RemoteAction[] v = values();
        if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Aktion");
        return v[o];
    }
}