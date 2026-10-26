package de.peaksound.protocol;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

/** Decodiert eingehende Pakete. Gibt entweder ein bekanntes Paket-Objekt oder wirft {@link MalformedPacketException}. */
public final class PacketCodec {
    /** Harte Obergrenze fuer jedes Paket (Plugin-Messaging Client -> Server erlaubt 32 KiB). */
    public static final int MAX_PACKET_SIZE = 32 * 1024 - 1;

    private PacketCodec() {}

    public static Object decode(byte[] data) {
        if (data.length == 0 || data.length > MAX_PACKET_SIZE) {
            throw new MalformedPacketException("Ungueltige Paketgroesse");
        }
        ByteBuffer in = ByteBuffer.wrap(data);
        int id = in.get() & 0xFF;
        try {
            Object packet = switch (id) {
                case Hello.ID -> Hello.decode(in);
                case HelloAck.ID -> HelloAck.decode(in);
                default -> throw new MalformedPacketException("Unbekannte Paket-ID " + id);
            };
            if (in.hasRemaining()) throw new MalformedPacketException("Ueberzaehlige Bytes");
            return packet;
        } catch (BufferUnderflowException e) {
            throw new MalformedPacketException("Paket zu kurz");
        }
    }
}
