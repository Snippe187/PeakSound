package de.peaksound.protocol;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Einstellungen eines Scheinwerfers (Menue). Der Server prueft jeden Wert mit {@link #sanitized()}.
 *
 * @param colors     1 bis 4 Farben (0xRRGGBB); bei mehreren wechseln sie sich ab
 * @param brightness Helligkeit in Prozent (1-100); bestimmt Strahlstaerke und echtes Licht (Stufe 1-15)
 * @param mode       dauerhaft, blinkend oder im Takt der Musik
 * @param effect     zusaetzlicher Effekt (Farbwechsel, Strobo, Schwenken, Pulsieren)
 * @param band       Frequenzbereich, auf den der Modus "im Takt" reagiert
 * @param yaw        Richtung des Strahls in Grad (0-359, 0 = Sueden, wie in Minecraft)
 * @param pitch      Neigung in Grad (-90 = nach unten, 90 = nach oben)
 * @param speed      Geschwindigkeit der Effekte und des Blinkens (1-10)
 */
public record LightSettings(List<Integer> colors, int brightness, LightMode mode, LightEffect effect, LightBand band,
                            int yaw, int pitch, int speed) {
    public static final int MAX_COLORS = 4;

    public enum LightMode {
        STEADY, BLINK, BEAT;

        public LightMode next() { return values()[(ordinal() + 1) % values().length]; }

        static LightMode byOrdinal(int o) {
            LightMode[] v = values();
            if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Modus");
            return v[o];
        }
    }

    public enum LightEffect {
        NONE, COLOR_CYCLE, STROBE, SWEEP, PULSE;

        public LightEffect next() { return values()[(ordinal() + 1) % values().length]; }

        static LightEffect byOrdinal(int o) {
            LightEffect[] v = values();
            if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Effekt");
            return v[o];
        }
    }

    public enum LightBand {
        BASS, MID, HIGH;

        public LightBand next() { return values()[(ordinal() + 1) % values().length]; }

        static LightBand byOrdinal(int o) {
            LightBand[] v = values();
            if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekanntes Band");
            return v[o];
        }
    }

    public static LightSettings defaults() {
        return new LightSettings(List.of(0xFFFFFF), 100, LightMode.STEADY, LightEffect.NONE, LightBand.BASS, 0, 60, 5);
    }

    public LightSettings sanitized() {
        List<Integer> clean = new ArrayList<>();
        if (colors != null) {
            for (Integer c : colors) {
                if (c != null && clean.size() < MAX_COLORS) clean.add(c & 0xFFFFFF);
            }
        }
        if (clean.isEmpty()) clean.add(0xFFFFFF);
        return new LightSettings(List.copyOf(clean), clamp(brightness, 1, 100), mode == null ? LightMode.STEADY : mode,
                effect == null ? LightEffect.NONE : effect, band == null ? LightBand.BASS : band,
                Math.floorMod(yaw, 360), clamp(pitch, -90, 90), clamp(speed, 1, 10));
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    public LightSettings withColors(List<Integer> v) { return new LightSettings(v, brightness, mode, effect, band, yaw, pitch, speed); }

    public LightSettings withBrightness(int v) { return new LightSettings(colors, v, mode, effect, band, yaw, pitch, speed); }

    public LightSettings withMode(LightMode v) { return new LightSettings(colors, brightness, v, effect, band, yaw, pitch, speed); }

    public LightSettings withEffect(LightEffect v) { return new LightSettings(colors, brightness, mode, v, band, yaw, pitch, speed); }

    public LightSettings withBand(LightBand v) { return new LightSettings(colors, brightness, mode, effect, v, yaw, pitch, speed); }

    public LightSettings withYaw(int v) { return new LightSettings(colors, brightness, mode, effect, band, v, pitch, speed); }

    public LightSettings withPitch(int v) { return new LightSettings(colors, brightness, mode, effect, band, yaw, v, speed); }

    public LightSettings withSpeed(int v) { return new LightSettings(colors, brightness, mode, effect, band, yaw, pitch, v); }

    void write(PacketIO.Writer w) {
        w.writeByte(colors.size());
        for (int c : colors) w.writeInt(c & 0xFFFFFF);
        w.writeByte(brightness).writeByte(mode.ordinal()).writeByte(effect.ordinal()).writeByte(band.ordinal())
                .writeShort(yaw).writeShort(pitch + 90).writeByte(speed);
    }

    static LightSettings read(ByteBuffer in) {
        int count = PacketIO.readByte(in);
        if (count < 1 || count > MAX_COLORS) throw new MalformedPacketException("Farbanzahl");
        List<Integer> colors = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int c = PacketIO.readInt(in);
            if ((c & 0xFFFFFF) != c) throw new MalformedPacketException("Ungueltige Farbe");
            colors.add(c);
        }
        int brightness = PacketIO.readByte(in);
        LightMode mode = LightMode.byOrdinal(PacketIO.readByte(in));
        LightEffect effect = LightEffect.byOrdinal(PacketIO.readByte(in));
        LightBand band = LightBand.byOrdinal(PacketIO.readByte(in));
        int yaw = PacketIO.readShort(in);
        int pitch = PacketIO.readShort(in) - 90;
        int speed = PacketIO.readByte(in);
        LightSettings s = new LightSettings(List.copyOf(colors), brightness, mode, effect, band, yaw, pitch, speed);
        if (!s.equals(s.sanitized())) throw new MalformedPacketException("Einstellungen ausserhalb des Bereichs");
        return s;
    }
}
