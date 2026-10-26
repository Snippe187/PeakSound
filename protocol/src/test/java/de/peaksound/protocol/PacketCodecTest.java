package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class PacketCodecTest {
    @Test
    void helloRoundTrip() {
        Hello hello = new Hello(1, "0.1.0");
        assertEquals(hello, PacketCodec.decode(hello.encode()));
    }

    @Test
    void helloAckRoundTrip() {
        HelloAck ack = new HelloAck(1, true, false);
        assertEquals(ack, PacketCodec.decode(ack.encode()));
    }

    @Test
    void rejectsEmptyAndOversized() {
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(new byte[0]));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(new byte[PacketCodec.MAX_PACKET_SIZE + 1]));
    }

    @Test
    void rejectsUnknownId() {
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(new byte[] {99}));
    }

    @Test
    void rejectsTruncatedAndTrailingBytes() {
        byte[] ok = new Hello(1, "x").encode();
        byte[] truncated = java.util.Arrays.copyOf(ok, ok.length - 1);
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(truncated));
        byte[] trailing = java.util.Arrays.copyOf(ok, ok.length + 1);
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(trailing));
    }

    @Test
    void rejectsLyingStringLength() {
        // ID, protocolVersion=1, Stringlaenge 60000 aber keine Daten
        byte[] evil = {1, 0, 0, 0, 1, (byte) 0xEA, 0x60};
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decode(evil));
    }

    @Test
    void rejectsOverlongVersionString() {
        assertThrows(IllegalArgumentException.class, () -> new Hello(1, "x".repeat(33)).encode());
    }

    @Test
    void itemTypesResolveById() {
        for (PeakItemType t : PeakItemType.values()) {
            assertEquals(t, PeakItemType.byId(t.id()).orElseThrow());
        }
        assertTrue(PeakItemType.byId("nope").isEmpty());
    }
}
