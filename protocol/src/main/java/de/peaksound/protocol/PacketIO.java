package de.peaksound.protocol;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Minimale, strikt begrenzte Binaer-Ein-/Ausgabe. Alle Laengen werden geprueft,
 * damit ein manipulierter Client keine riesigen Allokationen erzwingen kann.
 */
final class PacketIO {
    private PacketIO() {}

    static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Writer(int packetId) { out.write(packetId); }

        void writeInt(int v) {
            out.write(v >>> 24); out.write(v >>> 16); out.write(v >>> 8); out.write(v);
        }

        void writeBoolean(boolean v) { out.write(v ? 1 : 0); }

        void writeString(String s, int maxLength) {
            if (s.length() > maxLength) throw new IllegalArgumentException("String zu lang");
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            out.write(b.length >>> 8); out.write(b.length);
            out.write(b, 0, b.length);
        }

        byte[] toBytes() { return out.toByteArray(); }
    }

    static int readInt(ByteBuffer in) { return in.getInt(); }

    static boolean readBoolean(ByteBuffer in) {
        byte b = in.get();
        if (b != 0 && b != 1) throw new MalformedPacketException("Ungueltiger Boolean");
        return b == 1;
    }

    static String readString(ByteBuffer in, int maxLength) {
        int len = in.getShort() & 0xFFFF;
        if (len > maxLength * 4 || len > in.remaining()) throw new MalformedPacketException("Ungueltige Stringlaenge");
        byte[] b = new byte[len];
        in.get(b);
        String s = new String(b, StandardCharsets.UTF_8);
        if (s.length() > maxLength) throw new MalformedPacketException("String zu lang");
        return s;
    }
}
