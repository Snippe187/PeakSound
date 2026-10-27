package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Einstellungen eines Lautsprechers (Menue). Der Server prueft jeden Wert mit {@link #sanitized()}.
 *
 * @param name    frei waehlbarer Name (max. {@link #MAX_NAME} Zeichen)
 * @param channel Funktion/Position im Surround-System
 * @param volume  eigene Lautstaerke in Prozent (0-100), zusaetzlich zur Lautstaerke der Jukebox
 * @param kind    Bauart (Vollbereich, Tieftoener, Hochtoener, Subwoofer)
 * @param redstone nur mit Redstone-Signal an diesem Lautsprecher spielen (ohne Signal stumm)
 */
public record SpeakerSettings(String name, SpeakerChannel channel, int volume, SpeakerKind kind, boolean redstone,
                               int delayMs, ReverbPreset reverb) {
    public static final int MAX_NAME = 32;
    /** Laengste Verzoegerung in Millisekunden (Schall legt etwa 34 cm pro ms zurueck: 500 ms = 170 Meter). */
    public static final int MAX_DELAY_MS = 500;

    public SpeakerSettings {
        if (kind == null) kind = SpeakerKind.FULL_RANGE; // aeltere gespeicherte Lautsprecher kennen noch keine Bauart
        if (reverb == null) reverb = ReverbPreset.OFF;   // und keinen Raumhall
    }

    /** Ohne Verzoegerung und Raumhall (aeltere Aufrufe und gespeicherte Daten). */
    public SpeakerSettings(String name, SpeakerChannel channel, int volume, SpeakerKind kind, boolean redstone) {
        this(name, channel, volume, kind, redstone, 0, ReverbPreset.OFF);
    }

    public SpeakerSettings(String name, SpeakerChannel channel, int volume) {
        this(name, channel, volume, SpeakerKind.FULL_RANGE, false);
    }

    public SpeakerSettings(String name, SpeakerChannel channel, int volume, SpeakerKind kind) {
        this(name, channel, volume, kind, false);
    }

    public static SpeakerSettings defaults(SpeakerChannel channel) {
        return new SpeakerSettings("", channel, 100);
    }

    public SpeakerSettings sanitized() {
        String clean = name == null ? "" : name.replaceAll("\\p{Cntrl}", " ").trim();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        return new SpeakerSettings(clean, channel == null ? SpeakerChannel.FRONT_LEFT : channel,
                Math.max(0, Math.min(100, volume)), kind == null ? SpeakerKind.FULL_RANGE : kind, redstone,
                Math.max(0, Math.min(MAX_DELAY_MS, delayMs)), reverb == null ? ReverbPreset.OFF : reverb);
    }

    public SpeakerSettings withName(String v) { return new SpeakerSettings(v, channel, volume, kind, redstone, delayMs, reverb); }

    public SpeakerSettings withChannel(SpeakerChannel v) { return new SpeakerSettings(name, v, volume, kind, redstone, delayMs, reverb); }

    public SpeakerSettings withVolume(int v) { return new SpeakerSettings(name, channel, v, kind, redstone, delayMs, reverb); }

    public SpeakerSettings withRedstone(boolean v) { return new SpeakerSettings(name, channel, volume, kind, v, delayMs, reverb); }

    public SpeakerSettings withKind(SpeakerKind v) { return new SpeakerSettings(name, channel, volume, v, redstone, delayMs, reverb); }

    public SpeakerSettings withDelayMs(int v) { return new SpeakerSettings(name, channel, volume, kind, redstone, v, reverb); }

    public SpeakerSettings withReverb(ReverbPreset v) { return new SpeakerSettings(name, channel, volume, kind, redstone, delayMs, v); }

    void write(PacketIO.Writer w) {
        w.writeString(name, MAX_NAME).writeByte(channel.ordinal()).writeByte(volume).writeByte(kind.ordinal()).writeBoolean(redstone)
                .writeShort(delayMs).writeByte(reverb.ordinal());
    }

    static SpeakerSettings read(ByteBuffer in) {
        String name = PacketIO.readString(in, MAX_NAME);
        SpeakerChannel channel = SpeakerChannel.byOrdinal(PacketIO.readByte(in));
        int volume = PacketIO.readByte(in);
        if (volume > 100) throw new MalformedPacketException("Lautstaerke ausserhalb des Bereichs");
        SpeakerKind kind = SpeakerKind.byOrdinal(PacketIO.readByte(in));
        boolean redstone = PacketIO.readBoolean(in);
        int delay = PacketIO.readShort(in);
        if (delay > MAX_DELAY_MS) throw new MalformedPacketException("Verzoegerung ausserhalb des Bereichs");
        return new SpeakerSettings(name, channel, volume, kind, redstone, delay, ReverbPreset.byOrdinal(PacketIO.readByte(in)));
    }
}
