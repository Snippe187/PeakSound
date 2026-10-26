package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RemoteLogicTest {
    private static RemoteSettings s(RemoteAction a, boolean invert) { return new RemoteSettings(a, invert); }

    @Test
    void impulseActionsFireOnRisingEdgeOnly() {
        RemoteSettings skip = s(RemoteAction.SKIP, false);
        assertTrue(RemoteLogic.evaluate(skip, 0, 15).fire());
        assertFalse(RemoteLogic.evaluate(skip, 15, 15).fire());
        assertFalse(RemoteLogic.evaluate(skip, 15, 0).fire());
        assertFalse(RemoteLogic.evaluate(skip, 0, 0).fire());
    }

    @Test
    void invertFiresWhenSignalGoesOff() {
        RemoteSettings skip = s(RemoteAction.SKIP, true);
        assertTrue(RemoteLogic.evaluate(skip, 15, 0).fire());
        assertFalse(RemoteLogic.evaluate(skip, 0, 15).fire());
    }

    @Test
    void playLevelFiresOnEveryChangeBetweenOnAndOff() {
        RemoteSettings play = s(RemoteAction.PLAY_LEVEL, false);
        assertTrue(RemoteLogic.evaluate(play, 0, 7).fire());
        assertTrue(RemoteLogic.evaluate(play, 7, 0).fire());
        assertFalse(RemoteLogic.evaluate(play, 7, 12).fire()); // beide an: nichts zu tun
        assertFalse(RemoteLogic.evaluate(play, 0, 0).fire());
    }

    @Test
    void analogActionsFireOnAnyStrengthChange() {
        RemoteSettings vol = s(RemoteAction.VOLUME_BY_STRENGTH, false);
        RemoteLogic.Trigger t = RemoteLogic.evaluate(vol, 3, 9);
        assertTrue(t.fire());
        assertEquals(9, t.strength());
        assertFalse(RemoteLogic.evaluate(vol, 9, 9).fire());
        assertEquals(6, RemoteLogic.evaluate(s(RemoteAction.SONG_BY_STRENGTH, true), 0, 9).strength()); // umgekehrt: 15 - 9
    }

    @Test
    void volumeAndSongMapping() {
        assertEquals(0, RemoteLogic.volumeFor(0));
        assertEquals(100, RemoteLogic.volumeFor(15));
        assertEquals(53, RemoteLogic.volumeFor(8));
        assertEquals(-1, RemoteLogic.songIndexFor(0, 5));
        assertEquals(0, RemoteLogic.songIndexFor(1, 5));
        assertEquals(4, RemoteLogic.songIndexFor(5, 5));
        assertEquals(-1, RemoteLogic.songIndexFor(6, 5));
    }
}