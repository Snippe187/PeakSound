package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Einstellungen eines DJ-Pults (Menue). Der Server prueft jeden Wert mit {@link #sanitized()}.
 *
 * @param crossfader  Reglerstellung 0-100: 0 = nur Deck A, 100 = nur Deck B
 * @param fadeSeconds Ueberblendzeit in Sekunden (0 = sofort, bis 30)
 * @param volumeA     Lautstaerke von Deck A in Prozent
 * @param volumeB     Lautstaerke von Deck B in Prozent
 * @param redstone    Der Crossfader folgt einem Redstone-Signal am Pult: ohne Signal Deck A (0), mit Signal Deck B (100)
 * @param swapped     Decks vertauscht (Deck A und Deck B sind sonst nach Position der Jukeboxen festgelegt)
 */
public record DjSettings(int crossfader, int fadeSeconds, int volumeA, int volumeB, boolean swapped, boolean redstone) {
    public DjSettings(int crossfader, int fadeSeconds, int volumeA, int volumeB, boolean swapped) {
        this(crossfader, fadeSeconds, volumeA, volumeB, swapped, false);
    }

    public static DjSettings defaults() { return new DjSettings(0, 4, 100, 100, false, false); }

    public DjSettings sanitized() {
        return new DjSettings(clamp(crossfader, 0, 100), clamp(fadeSeconds, 0, 30), clamp(volumeA, 0, 100),
                clamp(volumeB, 0, 100), swapped, redstone);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public DjSettings withCrossfader(int v) { return new DjSettings(v, fadeSeconds, volumeA, volumeB, swapped, redstone); }

    public DjSettings withFadeSeconds(int v) { return new DjSettings(crossfader, v, volumeA, volumeB, swapped, redstone); }

    public DjSettings withVolumeA(int v) { return new DjSettings(crossfader, fadeSeconds, v, volumeB, swapped, redstone); }

    public DjSettings withVolumeB(int v) { return new DjSettings(crossfader, fadeSeconds, volumeA, v, swapped, redstone); }

    public DjSettings withRedstone(boolean v) { return new DjSettings(crossfader, fadeSeconds, volumeA, volumeB, swapped, v); }

    public DjSettings withSwapped(boolean v) { return new DjSettings(crossfader, fadeSeconds, volumeA, volumeB, v, redstone); }

    void write(PacketIO.Writer w) {
        w.writeByte(crossfader).writeByte(fadeSeconds).writeByte(volumeA).writeByte(volumeB).writeBoolean(swapped).writeBoolean(redstone);
    }

    static DjSettings read(ByteBuffer in) {
        DjSettings s = new DjSettings(PacketIO.readByte(in), PacketIO.readByte(in), PacketIO.readByte(in),
                PacketIO.readByte(in), PacketIO.readBoolean(in), PacketIO.readBoolean(in));
        if (!s.equals(s.sanitized())) throw new MalformedPacketException("Einstellungen ausserhalb des Bereichs");
        return s;
    }
}
