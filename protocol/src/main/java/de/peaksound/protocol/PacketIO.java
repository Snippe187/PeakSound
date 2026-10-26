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

        Writer writeByte(int v) { out.write(v); return this; }

        Writer writeShort(int v) { out.write(v >>> 8); out.write(v); return this; }

        Writer writeInt(int v) {
            out.write(v >>> 24); out.write(v >>> 16); out.write(v >>> 8); out.write(v);
            return this;
        }

        Writer writeLong(long v) {
            writeInt((int) (v >>> 32));
            return writeInt((int) v);
        }

        Writer writeBoolean(boolean v) { out.write(v ? 1 : 0); return this; }

        Writer writeString(String s, int maxLength) {
            if (s.length() > maxLength) throw new IllegalArgumentException("String zu lang");
            byte[] b = s.getBytes(StandardCharsets.UTF_8);
            writeShort(b.length);
            out.write(b, 0, b.length);
            return this;
        }

        /** Bytes mit 4-Byte-Laengenpraefix. */
        Writer writeBytes(byte[] data, int maxLength) {
            if (data.length > maxLength) throw new IllegalArgumentException("Daten zu gross");
            writeInt(data.length);
            out.write(data, 0, data.length);
            return this;
        }

        byte[] toBytes() { return out.toByteArray(); }
    }

    static int readByte(ByteBuffer in) { return in.get() & 0xFF; }

    static int readShort(ByteBuffer in) { return in.getShort() & 0xFFFF; }

    static int readInt(ByteBuffer in) { return in.getInt(); }

    static long readLong(ByteBuffer in) { return in.getLong(); }

    /** Liest einen Wert im Bereich [min, max]; alles andere ist manipuliert. */
    static int readIntRange(ByteBuffer in, int min, int max) {
        int v = in.getInt();
        if (v < min || v > max) throw new MalformedPacketException("Wert ausserhalb des Bereichs");
        return v;
    }

    static boolean readBoolean(ByteBuffer in) {
        byte b = in.get();
        if (b != 0 && b != 1) throw new MalformedPacketException("Ungueltiger Boolean");
        return b == 1;
    }

    static String readString(ByteBuffer in, int maxLength) {
        int len = readShort(in);
        if (len > maxLength * 4 || len > in.remaining()) throw new MalformedPacketException("Ungueltige Stringlaenge");
        byte[] b = new byte[len];
        in.get(b);
        String s = new String(b, StandardCharsets.UTF_8);
        if (s.length() > maxLength) throw new MalformedPacketException("String zu lang");
        return s;
    }

    static byte[] readBytes(ByteBuffer in, int maxLength) {
        int len = in.getInt();
        if (len < 0 || len > maxLength || len > in.remaining()) throw new MalformedPacketException("Ungueltige Datenlaenge");
        byte[] b = new byte[len];
        in.get(b);
        return b;
    }
}
