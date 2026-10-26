package de.peaksound.protocol;

import java.nio.ByteBuffer;

/** Server -> Client: Antwort auf {@link Hello}. */
public record HelloAck(int protocolVersion, boolean compatible, boolean operator) {
    public static final int ID = 2;

    public byte[] encode() {
        PacketIO.Writer w = new PacketIO.Writer(ID);
        w.writeInt(protocolVersion);
        w.writeBoolean(compatible);
        w.writeBoolean(operator);
        return w.toBytes();
    }

    static HelloAck decode(ByteBuffer in) {
        return new HelloAck(PacketIO.readInt(in), PacketIO.readBoolean(in), PacketIO.readBoolean(in));
    }
}
