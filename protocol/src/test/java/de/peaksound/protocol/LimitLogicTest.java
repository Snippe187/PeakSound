package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LimitLogicTest {
    @Test
    void playerOverrideBeatsTheDefault() {
        assertEquals(5, LimitLogic.effectiveSongLimit(5, 10));
        assertEquals(10, LimitLogic.effectiveSongLimit(null, 10));
        assertEquals(10, LimitLogic.effectiveSongLimit(LimitLogic.USE_DEFAULT, 10));
        assertEquals(0, LimitLogic.effectiveSongLimit(0, 10)); // eigener Wert 0 = fuer diesen Spieler unbegrenzt
    }

    @Test
    void limitsAreClamped() {
        assertEquals(0, LimitLogic.clampSongLimit(-5));
        assertEquals(LimitLogic.MAX_SONG_LIMIT, LimitLogic.clampSongLimit(Long.MAX_VALUE));
        assertEquals(LimitLogic.MAX_SONG_LIMIT, LimitLogic.effectiveSongLimit(null, Integer.MAX_VALUE));
        assertEquals(LimitLogic.MAX_STORAGE_MB, LimitLogic.clampStorageMb(Long.MAX_VALUE));
        assertEquals(0, LimitLogic.clampStorageMb(-1));
    }

    @Test
    void uploadAllowedBelowTheLimitOnly() {
        assertTrue(LimitLogic.mayUpload(0, 3));
        assertTrue(LimitLogic.mayUpload(2, 3));
        assertFalse(LimitLogic.mayUpload(3, 3));
        assertFalse(LimitLogic.mayUpload(10, 3));
        assertTrue(LimitLogic.mayUpload(1_000_000, 0)); // 0 = unbegrenzt
    }

    @Test
    void storageLimitIsExactAtTheBoundary() {
        assertTrue(LimitLogic.fitsStorage(900, 100, 1000));
        assertFalse(LimitLogic.fitsStorage(900, 101, 1000));
        assertTrue(LimitLogic.fitsStorage(Long.MAX_VALUE / 2, 5, 0)); // unbegrenzt
        assertEquals(2L * 1024 * 1024, LimitLogic.mbToBytes(2));
    }

    @Test
    void shrinkingTheLimitBelowUsageBlocksNewUploadsButKeepsOldOnes() {
        // 3 Songs gespeichert, Limit wird auf 2 gesenkt: kein weiterer Upload, nichts wird geloescht
        int stored = 3;
        assertFalse(LimitLogic.mayUpload(stored, 2));
        assertEquals(3, stored);
    }

    // ---- Pakete ----

    @Test
    void adminPacketsRoundTripWithLongLists() {
        List<Packet.AdminSong> songs = new ArrayList<>();
        for (int i = 0; i < Packet.AdminSongs.MAX_ADMIN_SONGS; i++) {
            songs.add(new Packet.AdminSong(String.format("%064x", i), "T" + i, "A", "U", 2, 1000, 5000, i % 2 == 0, i));
        }
        Packet.AdminSongs packet = new Packet.AdminSongs(songs, 5000, 5L, 10L, 3, List.of());
        byte[] bytes = packet.encode();
        assertTrue(bytes.length < PacketCodec.MAX_CLIENTBOUND_SIZE, "passt in ein Paket: " + bytes.length);
        assertEquals(packet, PacketCodec.decodeClientbound(bytes));
    }

    @Test
    void rejectsTooLongAdminListsOnEncode() {
        List<Packet.AdminSong> songs = new ArrayList<>();
        for (int i = 0; i <= Packet.AdminSongs.MAX_ADMIN_SONGS; i++) songs.add(new Packet.AdminSong("a".repeat(64), "t", "a", "u", 1, 1, 1, false, 0));
        assertThrows(IllegalArgumentException.class, () -> new Packet.AdminSongs(songs, 1, 0, 0, 0, List.of()).encode());
    }

    @Test
    void adminActionsAreOnlyAcceptedFromTheClientDirection() {
        byte[] action = new Packet.AdminAction(Packet.AdminAction.Action.DELETE_SONG, "a".repeat(64), 0, 0, 0).encode();
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeClientbound(action));
        byte[] list = new Packet.AdminSongs(List.of(), 0, 0, 0, 0, List.of()).encode();
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(list)); // ein Client darf keine Listen "senden"
        byte[] op = new Packet.OperatorState(true).encode();
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(op)); // und sich nicht selbst zum Operator erklaeren
    }

    @Test
    void rejectsInvalidAdminActionValues() {
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.AdminAction(Packet.AdminAction.Action.SET_STORAGE_MB, "", 0, 0, LimitLogic.MAX_STORAGE_MB + 1).encode()));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(
                new Packet.AdminAction(Packet.AdminAction.Action.SET_DEFAULT_LIMIT, "", 0, 0, -5).encode()));
        byte[] unknown = new Packet.AdminAction(Packet.AdminAction.Action.DELETE_SONG, "", 0, 0, 0).encode();
        unknown[1] = 99;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(unknown));
    }
}
