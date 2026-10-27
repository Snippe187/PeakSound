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
            new Packet.AdminListRequest(),
            new Packet.LightUpdate(1, 2, 3, new LightSettings(java.util.List.of(0xFF0000, 0x00FF00), 80, LightSettings.LightMode.BEAT,
                    LightSettings.LightEffect.SWEEP, LightSettings.LightBand.HIGH, 270, -30, 7)),
            new Packet.AnalysisRequest("a".repeat(64)),
            new Packet.DjRequest(1, 2, 3),
            new Packet.DebugRequest(),
            new Packet.JukeboxInvAction(1, 2, 3, Packet.JukeboxInvAction.Kind.PUT, 35),
            new Packet.JukeboxInvAction(1, 2, 3, Packet.JukeboxInvAction.Kind.TAKE, 14),
            new Packet.JukeboxUpdate(1, 2, 3, JukeboxSettings.defaults(16, 80).withPort(4, PortFunction.ITEM_IN).withPort(5, PortFunction.ITEM_OUT).withPort(0, PortFunction.ITEM_IN)),
            new Packet.RemoteUpdate(1, 2, 3, new RemoteSettings(RemoteAction.VOLUME_BY_STRENGTH, true)),
            new Packet.JukeboxUpdate(1, 2, 3, JukeboxSettings.defaults(16, 80).withPort(2, PortFunction.BEAT).withPort(3, PortFunction.SONG_END)),
            new Packet.SpeakerUpdate(1, 2, 3, new SpeakerSettings("R", SpeakerChannel.CENTER, 50, SpeakerKind.WOOFER, true)),
            new Packet.DjUpdate(1, 2, 3, new DjSettings(0, 5, 100, 100, false, true)),
            new Packet.MicUpdate(1, 2, 3, new MicSettings(true, 4, 100, false, 30, true)),
            new Packet.LibraryQuery(1, 2, 3, "stereo wide", 2),
            new Packet.LibraryWrite(1, 2, 3, "a".repeat(64)),
            new Packet.MicUpdate(4, 5, 6, new MicSettings(true, 8, 150, true, 25)),
            new Packet.DjUpdate(1, 2, 3, new DjSettings(60, 8, 90, 70, true)),
            new Packet.DjDeckAction(1, 2, 3, 1, Packet.DjDeckAction.Action.PLAY, 4),
            new Packet.AdminAction(Packet.AdminAction.Action.SET_PLAYER_LIMIT, "", 12345L, -9876L, 25),
            new Packet.SpeakerUpdate(1, 64, -3, new SpeakerSettings("Links", SpeakerChannel.REAR_LEFT, 70, SpeakerKind.TWEETER)),
            new Packet.SpeakerOverviewRequest(1, 64, -3),
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
            new Packet.PlaybackStart(1, -60, 3, "e".repeat(64), 1500, 1_700_000_000_000L, 180_000, 2, 16, 80, 5, 2),
            new Packet.PlaybackStop(1, -60, 3),
            new Packet.PlaybackUpdate(1, -60, 3, 20, 40),
            new Packet.OpenJukeboxMenu(1, 64, -3, JukeboxSettings.defaults(16, 100), true, "Titel", "Interpret"),
            new Packet.CoverData("a".repeat(64), new byte[1000]),
            new Packet.OperatorState(true),
            new Packet.OpenLightMenu(1, 2, 3, LightSettings.defaults(), true),
            new Packet.LightStart(1, 2, 3, LightSettings.defaults(), "b".repeat(64), 123456789L),
            new Packet.LightStop(1, 2, 3),
            new Packet.AnalysisData("c".repeat(64), new byte[5000]),
            new Packet.PlaybackFade(1, 2, 3, 40, 5000),
            new Packet.DjState(1, 2, 3, DjSettings.defaults(), true,
                    new Packet.DjDeck(true, "A", true, "Song", 0, java.util.List.of(new Packet.PlaylistEntry("Song", "X", 1000, true))),
                    new Packet.DjDeck(false, "", false, "", -1, java.util.List.of())),
            new Packet.AdminSongs(java.util.List.of(new Packet.AdminSong("a".repeat(64), "Titel", "Interpret", "Spieler", 6, 180_000, 4_000_000, true, 1_700_000_000_000L)),
                    42, 1_000_000_000L, 2_000_000_000L, 10,
                    java.util.List.of(new Packet.AdminPlayer("Spieler", 1L, 2L, 3, -1))),
            new Packet.OpenSpeakerMenu(1, 64, -3, SpeakerSettings.defaults(SpeakerChannel.CENTER), 4, "Disco"),
            new Packet.SpeakerOverview(1, 64, -3, java.util.List.of(
                    new Packet.SpeakerEntry(1, "A", SpeakerChannel.FRONT_LEFT, 100), new Packet.SpeakerEntry(2, "", SpeakerChannel.SUBWOOFER, 40))),
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
                LoopMode.ALL, true, true);
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
    void debugInfoRoundTripsUtf8AndIsBounded() {
        String text = "Diagnose: Gr\u00fc\u00dfe aus dem Spiel \u266a" + "x".repeat(5000);
        Packet.Clientbound back = PacketCodec.decodeClientbound(new Packet.DebugInfo(text).encode());
        assertEquals(text, ((Packet.DebugInfo) back).text());
        assertThrows(IllegalArgumentException.class, () -> new Packet.DebugInfo("y".repeat(Limits.MAX_DEBUG_BYTES + 1)).encode());
    }

    @Test
    void libraryResultsRoundTrip() {
        var songs = java.util.List.of(new Packet.LibrarySong("b".repeat(64), "Titel", "Interpret", "Uploader", 123_000));
        var packet = new Packet.LibraryResults(1, 2, 3, 0, 4, 20, 2, songs);
        assertEquals(packet, PacketCodec.decodeClientbound(packet.encode()));
        var tooMany = java.util.Collections.nCopies(Limits.LIBRARY_PAGE_SIZE + 1, songs.get(0));
        assertThrows(IllegalArgumentException.class, () -> new Packet.LibraryResults(1, 2, 3, 0, 1, 7, 0, tooMany).encode());
    }

    @Test
    void rejectsInvalidInventorySlots() {
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.JukeboxInvAction(0, 0, 0, Packet.JukeboxInvAction.Kind.TAKE, 15).encode()));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.JukeboxInvAction(0, 0, 0, Packet.JukeboxInvAction.Kind.PUT, 36).encode()));
    }

    @Test
    void verticalSidesOnlyAllowItemModes() {
        JukeboxSettings s = new JukeboxSettings("", 16, 80, false, true, 3, 5, 2, true, true, LoopMode.OFF, false, false,
                java.util.List.of(PortFunction.BASS, PortFunction.MID, PortFunction.HIGH, PortFunction.OVERALL, PortFunction.BASS, PortFunction.ITEM_OUT)).sanitized();
        assertEquals(PortFunction.OFF, s.ports().get(4));   // Bass an der Oberseite ist nicht erlaubt
        assertEquals(PortFunction.ITEM_OUT, s.ports().get(5));
        assertEquals(6, s.ports().size());
        assertEquals(PortFunction.ITEM_IN, PortFunction.OFF.next(4));
        assertEquals(PortFunction.OFF, PortFunction.ITEM_OUT.next(5));
        assertEquals(PortFunction.ITEM_OUT, PortFunction.OFF.previous(5));
    }

    @Test
    void rejectsInvalidPortFunction() {
        byte[] bad = new Packet.JukeboxUpdate(0, 0, 0, JukeboxSettings.defaults(16, 80)).encode();
        bad[bad.length - 1] = 99; // unbekannte Ausgangsfunktion
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(bad));
    }

    @Test
    void rejectsInvalidMicSettings() {
        byte[] bad = new Packet.MicUpdate(0, 0, 0, MicSettings.defaults()).encode();
        bad[bad.length - 2] = 101; // Ducking-Pegel ueber 100 % (das letzte Byte ist der Redstone-Schalter)
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(bad));
    }

    @Test
    void rejectsInvalidLightAndDjSettings() {
        // Farbe mit gesetzten Bits ueber 24 Bit (das Hoechstwertige Byte der ersten Farbe von Hand setzen), Helligkeit 0, Crossfader 200
        byte[] badColor = new Packet.LightUpdate(0, 0, 0, LightSettings.defaults()).encode();
        badColor[14] = 1;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(badColor));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.LightUpdate(0, 0, 0, new LightSettings(java.util.List.of(0xFFFFFF), 0, LightSettings.LightMode.STEADY,
                        LightSettings.LightEffect.NONE, LightSettings.LightBand.BASS, 0, 0, 5)).encode()));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.DjUpdate(0, 0, 0, new DjSettings(200, 4, 100, 100, false)).encode()));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.DjDeckAction(0, 0, 0, 5, Packet.DjDeckAction.Action.PLAY, 0).encode()));
    }

    @Test
    void lightSettingsAreSanitized() {
        LightSettings s = new LightSettings(java.util.List.of(1, 2, 3, 4, 5, 6), 500, null, null, null, -90, 200, 99).sanitized();
        assertEquals(LightSettings.MAX_COLORS, s.colors().size());
        assertEquals(100, s.brightness());
        assertEquals(270, s.yaw());
        assertEquals(90, s.pitch());
        assertEquals(10, s.speed());
        assertEquals(java.util.List.of(0xFFFFFF), new LightSettings(java.util.List.of(), 1, null, null, null, 0, 0, 1).sanitized().colors());
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
