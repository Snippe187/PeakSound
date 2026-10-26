package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Einstellungen eines Lautsprechers (Menue). Der Server prueft jeden Wert mit {@link #sanitized()}.
 *
 * @param name    frei waehlbarer Name (max. {@link #MAX_NAME} Zeichen)
 * @param channel Funktion/Position im Surround-System
 * @param volume  eigene Lautstaerke in Prozent (0-100), zusaetzlich zur Lautstaerke der Jukebox
 * @param kind    Bauart (Vollbereich, Tieftoener, Hochtoener, Subwoofer)
 */
public record SpeakerSettings(String name, SpeakerChannel channel, int volume, SpeakerKind kind) {
    public static final int MAX_NAME = 32;

    public SpeakerSettings {
        if (kind == null) kind = SpeakerKind.FULL_RANGE; // aeltere gespeicherte Lautsprecher kennen noch keine Bauart
    }

    public SpeakerSettings(String name, SpeakerChannel channel, int volume) {
        this(name, channel, volume, SpeakerKind.FULL_RANGE);
    }

    public static SpeakerSettings defaults(SpeakerChannel channel) {
        return new SpeakerSettings("", channel, 100);
    }

    public SpeakerSettings sanitized() {
        String clean = name == null ? "" : name.replaceAll("\\p{Cntrl}", " ").trim();
        if (clean.length() > MAX_NAME) clean = clean.substring(0, MAX_NAME);
        return new SpeakerSettings(clean, channel == null ? SpeakerChannel.FRONT_LEFT : channel,
                Math.max(0, Math.min(100, volume)), kind == null ? SpeakerKind.FULL_RANGE : kind);
    }

    public SpeakerSettings withName(String v) { return new SpeakerSettings(v, channel, volume, kind); }

    public SpeakerSettings withChannel(SpeakerChannel v) { return new SpeakerSettings(name, v, volume, kind); }

    public SpeakerSettings withVolume(int v) { return new SpeakerSettings(name, channel, v, kind); }

    public SpeakerSettings withKind(SpeakerKind v) { return new SpeakerSettings(name, channel, volume, v); }

    void write(PacketIO.Writer w) {
        w.writeString(name, MAX_NAME).writeByte(channel.ordinal()).writeByte(volume).writeByte(kind.ordinal());
    }

    static SpeakerSettings read(ByteBuffer in) {
        String name = PacketIO.readString(in, MAX_NAME);
        SpeakerChannel channel = SpeakerChannel.byOrdinal(PacketIO.readByte(in));
        int volume = PacketIO.readByte(in);
        if (volume > 100) throw new MalformedPacketException("Lautstaerke ausserhalb des Bereichs");
        return new SpeakerSettings(name, channel, volume, SpeakerKind.byOrdinal(PacketIO.readByte(in)));
    }
}
