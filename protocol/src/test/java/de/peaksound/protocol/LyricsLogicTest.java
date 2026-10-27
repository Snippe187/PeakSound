package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LyricsLogicTest {
    @Test
    void timestampedLyricsAreSyncedAndSorted() {
        var l = LyricsLogic.parse("[ar:Band]\n[00:12.50]Zweite\n[00:01.00]Erste\n[01:00]Dritte", 120_000);
        assertTrue(l.synced());
        assertEquals(3, l.lines().size());
        assertEquals("Erste", l.lines().get(0).text());
        assertEquals(12_500, l.lines().get(1).startMs());
        assertEquals(60_000, l.lines().get(2).startMs());
    }

    @Test
    void severalStampsOnOneLineRepeatIt() {
        var l = LyricsLogic.parse("[00:10.00][00:30.00]Refrain\n[00:20.00]Strophe", 60_000);
        assertEquals(3, l.lines().size());
        assertEquals("Refrain", l.lines().get(2).text());
    }

    @Test
    void plainLyricsAreSpreadOverTheSong() {
        var l = LyricsLogic.parse("Eins\n\nZwei\nDrei\nVier", 100_000);
        assertFalse(l.synced());
        assertEquals(4, l.lines().size());
        assertEquals(5_000, l.lines().get(0).startMs());
        assertTrue(l.lines().get(3).startMs() < 100_000);
        assertTrue(l.lines().get(1).startMs() > l.lines().get(0).startMs());
    }

    @Test
    void emptyTextGivesNoLines() {
        assertTrue(LyricsLogic.parse("  \n ", 1000).isEmpty());
        assertTrue(LyricsLogic.parse(null, 1000).isEmpty());
    }

    @Test
    void lineAtFindsTheCurrentLine() {
        var lines = LyricsLogic.parse("[00:10.00]A\n[00:20.00]B", 60_000).lines();
        assertEquals(-1, LyricsLogic.lineAt(lines, 5_000));
        assertEquals(0, LyricsLogic.lineAt(lines, 10_000));
        assertEquals(1, LyricsLogic.lineAt(lines, 25_000));
    }

    @Test
    void lyricsPacketsRoundTrip() {
        var info = new Packet.SongInfo("a".repeat(64), "Titel", "Band", 123_456_789L, 200_000);
        assertEquals(info, PacketCodec.decodeClientbound(info.encode()));
        var data = new Packet.LyricsData("b".repeat(64), "Zeile\nZweite \u00e4\u00f6\u00fc");
        assertEquals(data, PacketCodec.decodeClientbound(data.encode()));
        var req = new Packet.LyricsRequest("c".repeat(64));
        assertEquals(req, PacketCodec.decodeServerbound(req.encode()));
    }
}
