package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Einstellungen eines Radio-Blocks (Sender oder Empfaenger). Der Server prueft jeden Wert mit {@link #sanitized()}.
 *
 * @param enabled   Sender sendet bzw. Empfaenger spielt
 * @param frequency Frequenz in Zehntel-MHz ({@link RadioLogic#MIN_FREQUENCY} bis {@link RadioLogic#MAX_FREQUENCY}, 984 = 98,4 MHz)
 * @param range     Reichweite des Empfaengers in Bloecken (beim Sender ohne Wirkung)
 * @param volume    Lautstaerke des Empfaengers in Prozent (beim Sender ohne Wirkung)
 */
public record RadioSettings(boolean enabled, int frequency, int range, int volume) {
    public static final int MAX_RANGE = 32;

    public static RadioSettings defaults() { return new RadioSettings(true, 1000, 12, 80); }

    public RadioSettings sanitized() {
        return new RadioSettings(enabled, RadioLogic.clampFrequency(frequency), clamp(range, 1, MAX_RANGE), clamp(volume, 0, 100));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public RadioSettings withEnabled(boolean v) { return new RadioSettings(v, frequency, range, volume); }

    public RadioSettings withFrequency(int v) { return new RadioSettings(enabled, v, range, volume); }

    public RadioSettings withRange(int v) { return new RadioSettings(enabled, frequency, v, volume); }

    public RadioSettings withVolume(int v) { return new RadioSettings(enabled, frequency, range, v); }

    void write(PacketIO.Writer w) {
        w.writeBoolean(enabled).writeShort(frequency).writeByte(range).writeByte(volume);
    }

    static RadioSettings read(ByteBuffer in) {
        RadioSettings s = new RadioSettings(PacketIO.readBoolean(in), PacketIO.readShort(in), PacketIO.readByte(in), PacketIO.readByte(in));
        if (!s.equals(s.sanitized())) throw new MalformedPacketException("Einstellungen ausserhalb des Bereichs");
        return s;
    }
}
