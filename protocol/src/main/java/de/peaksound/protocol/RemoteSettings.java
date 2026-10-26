package de.peaksound.protocol;

import java.nio.ByteBuffer;

/**
 * Einstellungen einer Musik-Fernbedienung.
 *
 * @param action Aktion, die ein Signal ausloest
 * @param invert Signal umdrehen: die Aktion laeuft, wenn das Signal AUS geht (bei Pegel-Aktionen: Signalstaerke 15 - Staerke)
 */
public record RemoteSettings(RemoteAction action, boolean invert) {
    public static RemoteSettings defaults() { return new RemoteSettings(RemoteAction.SKIP, false); }

    public RemoteSettings sanitized() {
        return new RemoteSettings(action == null ? RemoteAction.SKIP : action, invert);
    }

    public RemoteSettings withAction(RemoteAction v) { return new RemoteSettings(v, invert); }

    public RemoteSettings withInvert(boolean v) { return new RemoteSettings(action, v); }

    void write(PacketIO.Writer w) {
        w.writeByte(action.ordinal()).writeBoolean(invert);
    }

    static RemoteSettings read(ByteBuffer in) {
        return new RemoteSettings(RemoteAction.byOrdinal(PacketIO.readByte(in)), PacketIO.readBoolean(in));
    }
}