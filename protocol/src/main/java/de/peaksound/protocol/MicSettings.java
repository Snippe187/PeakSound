package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Einstellungen eines Mikrofon-Blocks. Der Server prueft jeden Wert mit {@link #sanitized()}.
 *
 * @param enabled     Mikrofon an/aus
 * @param range       Sprechreichweite in Bloecken: Spieler in dieser Entfernung werden ueber die Lautsprecher uebertragen
 * @param volume      Lautstaerke der Stimme in Prozent (0-200)
 * @param ducking     Musik leiser stellen, solange jemand ins Mikrofon spricht
 * @param duckPercent Lautstaerke der Musik waehrend des Sprechens in Prozent (0-100)
 */
public record MicSettings(boolean enabled, int range, int volume, boolean ducking, int duckPercent) {
    public static final int MAX_RANGE = 16;
    public static final int MAX_VOLUME = 200;

    public static MicSettings defaults() { return new MicSettings(true, 4, 100, false, 30); }

    public MicSettings sanitized() {
        return new MicSettings(enabled, clamp(range, 1, MAX_RANGE), clamp(volume, 0, MAX_VOLUME), ducking, clamp(duckPercent, 0, 100));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public MicSettings withEnabled(boolean v) { return new MicSettings(v, range, volume, ducking, duckPercent); }

    public MicSettings withRange(int v) { return new MicSettings(enabled, v, volume, ducking, duckPercent); }

    public MicSettings withVolume(int v) { return new MicSettings(enabled, range, v, ducking, duckPercent); }

    public MicSettings withDucking(boolean v) { return new MicSettings(enabled, range, volume, v, duckPercent); }

    public MicSettings withDuckPercent(int v) { return new MicSettings(enabled, range, volume, ducking, v); }

    void write(PacketIO.Writer w) {
        w.writeBoolean(enabled).writeByte(range).writeShort(volume).writeBoolean(ducking).writeByte(duckPercent);
    }

    static MicSettings read(ByteBuffer in) {
        MicSettings s = new MicSettings(PacketIO.readBoolean(in), PacketIO.readByte(in), PacketIO.readShort(in),
                PacketIO.readBoolean(in), PacketIO.readByte(in));
        if (!s.equals(s.sanitized())) throw new MalformedPacketException("Einstellungen ausserhalb des Bereichs");
        return s;
    }
}