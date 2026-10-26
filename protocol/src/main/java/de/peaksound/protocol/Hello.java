package de.peaksound.protocol;

import java.nio.ByteBuffer;

/** Client -> Server: erstes Paket nach dem Beitritt. */
public record Hello(int protocolVersion, String modVersion) {
    public static final int ID = 1;
    public static final int MAX_VERSION_LENGTH = 32;

    public byte[] encode() {
        PacketIO.Writer w = new PacketIO.Writer(ID);
        w.writeInt(protocolVersion);
        w.writeString(modVersion, MAX_VERSION_LENGTH);
        return w.toBytes();
    }

    static Hello decode(ByteBuffer in) {
        return new Hello(PacketIO.readInt(in), PacketIO.readString(in, MAX_VERSION_LENGTH));
    }
}
