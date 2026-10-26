package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PlaylistLogicTest {
    private final Random random = new Random(42);

    private int next(int size, int current, LoopMode loop, boolean shuffle, Set<Integer> played) {
        return PlaylistLogic.next(size, current, loop, shuffle, played, random);
    }

    @Test
    void sequentialAdvancesAndStopsAtEnd() {
        assertEquals(1, next(3, 0, LoopMode.OFF, false, new HashSet<>()));
        assertEquals(2, next(3, 1, LoopMode.OFF, false, new HashSet<>()));
        assertEquals(-1, next(3, 2, LoopMode.OFF, false, new HashSet<>()));
    }

    @Test
    void loopAllWrapsAround() {
        assertEquals(0, next(3, 2, LoopMode.ALL, false, new HashSet<>()));
    }

    @Test
    void loopOneRepeatsCurrentButSkipMovesOn() {
        assertEquals(1, next(3, 1, LoopMode.ONE, false, new HashSet<>()));
        assertEquals(2, PlaylistLogic.skip(3, 1, LoopMode.ONE, false, new HashSet<>(), random));
        assertEquals(0, PlaylistLogic.skip(3, 2, LoopMode.ONE, false, new HashSet<>(), random));
    }

    @Test
    void skipAtEndWithoutLoopStops() {
        assertEquals(-1, PlaylistLogic.skip(3, 2, LoopMode.OFF, false, new HashSet<>(), random));
    }

    @Test
    void emptyAndInvalidCurrent() {
        assertEquals(-1, next(0, 0, LoopMode.ALL, false, new HashSet<>()));
        assertEquals(0, next(3, -1, LoopMode.OFF, false, new HashSet<>())); // nichts spielt: von vorn
        assertEquals(0, next(3, 99, LoopMode.OFF, false, new HashSet<>()));
    }

    @Test
    void shufflePlaysEveryTrackOncePerRoundWithoutRepeat() {
        Set<Integer> played = new HashSet<>();
        Set<Integer> seen = new HashSet<>();
        int current = -1;
        for (int i = 0; i < 6; i++) {
            current = next(6, current, LoopMode.OFF, true, played);
            assertTrue(current >= 0, "Schritt " + i);
            assertTrue(seen.add(current), "Song doppelt: " + current);
        }
        assertEquals(6, seen.size());
        assertEquals(-1, next(6, current, LoopMode.OFF, true, played)); // alle gespielt, kein Loop
    }

    @Test
    void shuffleWithLoopAllStartsNewRoundAndAvoidsImmediateRepeat() {
        Set<Integer> played = new HashSet<>();
        int current = -1;
        for (int i = 0; i < 200; i++) {
            int n = next(4, current, LoopMode.ALL, true, played);
            assertTrue(n >= 0 && n < 4);
            if (current >= 0) assertNotEquals(current, n, "direkte Wiederholung bei Schritt " + i);
            current = n;
        }
    }

    @Test
    void shuffleWithSingleTrackLoopAllRepeatsIt() {
        Set<Integer> played = new HashSet<>();
        assertEquals(0, next(1, 0, LoopMode.ALL, true, played));
        assertEquals(0, next(1, 0, LoopMode.ALL, true, played));
        assertEquals(-1, next(1, 0, LoopMode.OFF, true, new HashSet<>()));
    }

    @Test
    void swapTargetsRespectBorders() {
        assertEquals(-1, PlaylistLogic.swapTarget(0, true, 5));
        assertEquals(0, PlaylistLogic.swapTarget(1, true, 5));
        assertEquals(4, PlaylistLogic.swapTarget(3, false, 5));
        assertEquals(-1, PlaylistLogic.swapTarget(4, false, 5));
        assertEquals(-1, PlaylistLogic.swapTarget(7, false, 5));
        assertEquals(-1, PlaylistLogic.swapTarget(-1, true, 5));
    }

    @Test
    void currentTrackFollowsSwap() {
        assertEquals(2, PlaylistLogic.followSwap(1, 1, 2));
        assertEquals(1, PlaylistLogic.followSwap(2, 1, 2));
        assertEquals(4, PlaylistLogic.followSwap(4, 1, 2));
    }

    @Test
    void loopModeCycles() {
        assertEquals(LoopMode.ALL, LoopMode.OFF.next());
        assertEquals(LoopMode.ONE, LoopMode.ALL.next());
        assertEquals(LoopMode.OFF, LoopMode.ONE.next());
    }
}
