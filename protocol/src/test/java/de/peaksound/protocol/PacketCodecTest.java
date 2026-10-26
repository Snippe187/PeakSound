package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PacketCodecTest {
    private static final List<Packet.Serverbound> SERVERBOUND = List.of(
            new Packet.Hello(1, "0.1.0"),
            new Packet.UploadStart(7, "Mein Song", 1234567),
            new Packet.UploadChunk(3, 0, new byte[] {1, 2, 3}),
            new Packet.SongRequest("a".repeat(64), 5),
            new Packet.JukeboxUpdate(1, 64, -3, JukeboxSettings.defaults(16, 50)),
            new Packet.CoverRequest("f".repeat(64)),
            new Packet.PlaylistRequest(1, 64, -3),
            new Packet.PlaylistAction(1, 64, -3, Packet.PlaylistAction.Action.MOVE_UP, 4));

    private static final List<Packet.Clientbound> CLIENTBOUND = List.of(
            new Packet.HelloAck(1, true, false, 1_700_000_000_000L, 240),
            new Packet.OpenUpload(9),
            new Packet.UploadAccept(3, Limits.UPLOAD_CHUNK_BYTES, 16),
            new Packet.UploadReject(3, Packet.UploadReject.Reason.RATE_LIMIT, 42),
            new Packet.UploadAck(3, 8),
            new Packet.UploadResult(3, true, "b".repeat(64), "Titel", "Interpret", 200_000, 6),
            new Packet.SongChunk("c".repeat(64), 1, 4, new byte[Limits.DOWNLOAD_CHUNK_BYTES]),
            new Packet.SongUnavailable("d".repeat(64)),
            new Packet.PlaybackStart(1, -60, 3, "e".repeat(64), 1500, 1_700_000_000_000L, 180_000, 2, 16, 80),
            new Packet.PlaybackStop(1, -60, 3),
            new Packet.PlaybackUpdate(1, -60, 3, 20, 40),
            new Packet.OpenJukeboxMenu(1, 64, -3, JukeboxSettings.defaults(16, 100), true, "Titel", "Interpret"),
            new Packet.CoverData("a".repeat(64), new byte[1000]),
            new Packet.PlaylistState(1, 64, -3, 1, true, java.util.List.of(
                    new Packet.PlaylistEntry("A", "X", 1000, true), new Packet.PlaylistEntry("B", "", 2000, false))));

    @Test
    void serverboundRoundTrip() {
        for (Packet.Serverbound p : SERVERBOUND) {
            Packet.Serverbound decoded = PacketCodec.decodeServerbound(p.encode());
            assertEquals(p.getClass(), decoded.getClass());
            assertArrayEquals(p.encode(), decoded.encode());
        }
    }

    @Test
    void clientboundRoundTrip() {
        for (Packet.Clientbound p : CLIENTBOUND) {
            Packet.Clientbound decoded = PacketCodec.decodeClientbound(p.encode());
            assertEquals(p.getClass(), decoded.getClass());
            assertArrayEquals(p.encode(), decoded.encode());
        }
    }

    @Test
    void helloRoundTripKeepsValues() {
        Packet.Hello hello = new Packet.Hello(1, "0.1.0");
        assertEquals(hello, PacketCodec.decodeServerbound(hello.encode()));
    }

    @Test
    void directionIsEnforced() {
        // Ein Client darf keine Server-Pakete senden (z. B. PlaybackStart faelschen)
        for (Packet.Clientbound p : CLIENTBOUND) {
            assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(p.encode()));
        }
        for (Packet.Serverbound p : SERVERBOUND) {
            assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeClientbound(p.encode()));
        }
    }

    @Test
    void rejectsEmptyAndOversized() {
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(new byte[0]));
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeServerbound(new byte[PacketCodec.MAX_SERVERBOUND_SIZE + 1]));
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeClientbound(new byte[PacketCodec.MAX_CLIENTBOUND_SIZE + 1]));
    }

    @Test
    void rejectsUnknownId() {
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(new byte[] {99}));
    }

    @Test
    void rejectsTruncatedAndTrailingBytes() {
        byte[] ok = new Packet.Hello(1, "x").encode();
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeServerbound(Arrays.copyOf(ok, ok.length - 1)));
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeServerbound(Arrays.copyOf(ok, ok.length + 1)));
    }

    @Test
    void rejectsLyingStringLength() {
        byte[] evil = {Packet.Hello.ID, 0, 0, 0, 1, (byte) 0xEA, 0x60};
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(evil));
    }

    @Test
    void rejectsLyingByteArrayLength() {
        // UploadChunk mit behaupteter Laenge 0x7FFFFFFF
        byte[] evil = {Packet.UploadChunk.ID, 0, 0, 0, 1, 0, 0, 0, 0, 0x7F, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(evil));
    }

    @Test
    void rejectsOutOfRangeValues() {
        // Dateigroesse 0 und groesser als das Limit
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeServerbound(new Packet.UploadStart(1, "x", 0).encode()));
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeServerbound(new Packet.UploadStart(1, "x", Limits.MAX_UPLOAD_BYTES + 1).encode()));
        // Negativer Chunk-Index
        assertThrows(MalformedPacketException.class,
                () -> PacketCodec.decodeServerbound(new Packet.UploadChunk(1, -1, new byte[1]).encode()));
        // Ungueltiger Boolean
        byte[] badBool = new Packet.HelloAck(1, true, false, 0, 1).encode();
        badBool[5] = 7;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeClientbound(badBool));
    }

    @Test
    void settingsRoundTripAndSanitize() {
        JukeboxSettings s = new JukeboxSettings("  Disco\u0007 ".concat("x".repeat(60)), 99, -5, true, false, 50, 0, 9, false, true,
                LoopMode.ALL, true);
        JukeboxSettings clean = s.sanitized();
        assertEquals(30, clean.range());
        assertEquals(0, clean.volume());
        assertEquals(10, clean.particleAmount());
        assertEquals(1, clean.particleDensity());
        assertEquals(5, clean.particleHeight());
        assertTrue(clean.name().length() <= JukeboxSettings.MAX_NAME);
        assertFalse(clean.name().contains("\u0007"));
        Packet.JukeboxUpdate decoded = (Packet.JukeboxUpdate) PacketCodec.decodeServerbound(
                new Packet.JukeboxUpdate(0, 0, 0, clean).encode());
        assertEquals(clean, decoded.settings());
    }

    @Test
    void rejectsOutOfRangeSettingsFromClient() {
        // Reichweite 99 und Lautstaerke 200 direkt im Paket: ein ehrlicher Client sendet das nie
        byte[] packet = new Packet.JukeboxUpdate(0, 0, 0, JukeboxSettings.defaults(16, 50)).encode();
        int nameLen = ((packet[13] & 0xFF) << 8) | (packet[14] & 0xFF);
        int rangeIndex = 15 + nameLen;
        packet[rangeIndex] = 99;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(packet));
        packet[rangeIndex] = 16;
        packet[rangeIndex + 1] = (byte) 200;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(packet));
    }

    @Test
    void rejectsInvalidPlaylistData() {
        // Aktion mit unbekannter Nummer und Index ausserhalb der Obergrenze
        byte[] action = new Packet.PlaylistAction(0, 0, 0, Packet.PlaylistAction.Action.PLAY, 1).encode();
        byte[] badAction = action.clone();
        badAction[13] = 77;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(badAction));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.PlaylistAction(0, 0, 0, Packet.PlaylistAction.Action.PLAY, PlaylistLogic.MAX_ENTRIES + 1).encode()));
        // aktueller Index zeigt hinter das Ende der Liste
        byte[] state = new Packet.PlaylistState(0, 0, 0, 0, false, java.util.List.of(
                new Packet.PlaylistEntry("A", "", 1, true))).encode();
        state[16] = 9; // current = 9 (letztes Byte des Int-Felds)
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeClientbound(state));
    }

    @Test
    void rejectsOverlongStringsOnEncode() {
        assertThrows(IllegalArgumentException.class, () -> new Packet.Hello(1, "x".repeat(33)).encode());
        assertThrows(IllegalArgumentException.class,
                () -> new Packet.UploadChunk(1, 0, new byte[Limits.UPLOAD_CHUNK_BYTES + 1]).encode());
    }

    @Test
    void itemTypesResolveById() {
        for (PeakItemType t : PeakItemType.values()) {
            assertEquals(t, PeakItemType.byId(t.id()).orElseThrow());
        }
        assertTrue(PeakItemType.byId("nope").isEmpty());
    }
}
