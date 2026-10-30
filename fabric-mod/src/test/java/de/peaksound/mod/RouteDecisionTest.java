package de.peaksound.mod;

import static de.peaksound.mod.RouteDecision.Route.CORE;
import static de.peaksound.mod.RouteDecision.Route.LEGACY;
import static de.peaksound.mod.RouteDecision.Route.NONE;
import static de.peaksound.mod.RouteDecision.Route.UNDECIDED;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.peakverse.core.client.ClientSession.State;
import org.junit.jupiter.api.Test;

class RouteDecisionTest {
    private static RouteDecision.Route decide(State state, boolean hasPeakSound, boolean core, boolean legacy, long waited) {
        return RouteDecision.decide(state, hasPeakSound, core, legacy, waited);
    }

    @Test
    void newServerWithPeakCoreAndPeakSoundUsesTheCorePath() {
        assertEquals(CORE, decide(State.ACTIVE, true, true, true, 500));
        assertEquals(CORE, decide(State.ACTIVE, true, true, false, 500), "der alte Kanal muss dafuer nicht bekannt sein");
    }

    @Test
    void waitsForThePeakCoreHandshakeWhenBothChannelsAreKnown() {
        assertEquals(UNDECIDED, decide(State.WAITING, false, true, true, 0));
        assertEquals(UNDECIDED, decide(State.WAITING, false, true, true, RouteDecision.WAIT_MS + 5_000), "ein Server mit beiden Kanaelen antwortet, hier wird nicht abgebrochen");
    }

    @Test
    void oldServerWithOnlyTheLegacyChannelUsesItImmediately() {
        assertEquals(LEGACY, decide(State.WAITING, false, false, true, 0), "Server mit Alpha-Plugin: kein Warten auf PeakCore");
    }

    @Test
    void waitsForTheChannelListThenGivesUp() {
        assertEquals(UNDECIDED, decide(State.WAITING, false, false, false, 0));
        assertEquals(UNDECIDED, decide(State.WAITING, false, false, false, RouteDecision.WAIT_MS - 1));
        assertEquals(NONE, decide(State.WAITING, false, false, false, RouteDecision.WAIT_MS), "kein PeakSound auf diesem Server");
    }

    @Test
    void fallsBackToTheLegacyChannelIfPeakCoreFailsButThePluginStillSpeaksIt() {
        assertEquals(LEGACY, decide(State.INCOMPATIBLE, false, true, true, 3_000), "PeakCore-Protokoll passt nicht, das Plugin kann aber noch den alten Kanal");
        assertEquals(LEGACY, decide(State.NO_SERVER_SUPPORT, false, false, true, 11_000));
        assertEquals(NONE, decide(State.INCOMPATIBLE, false, true, false, 3_000));
        assertEquals(NONE, decide(State.NO_SERVER_SUPPORT, false, false, false, 11_000));
    }

    @Test
    void peakCoreWithoutPeakSoundMeansNoPath() {
        assertEquals(NONE, decide(State.ACTIVE, false, true, false, 1_000));
        assertEquals(LEGACY, decide(State.ACTIVE, false, true, true, 1_000), "falls dort doch der alte Kanal bedient wird");
    }

    @Test
    void offlineStaysUndecided() {
        assertEquals(UNDECIDED, decide(State.OFFLINE, false, true, true, 0));
        assertEquals(UNDECIDED, decide(State.OFFLINE, true, true, true, 99_999));
    }

    @Test
    void everyCombinationGivesAnAnswer() {
        for (State s : State.values())
            for (boolean has : new boolean[] {false, true})
                for (boolean core : new boolean[] {false, true})
                    for (boolean legacy : new boolean[] {false, true})
                        for (long waited : new long[] {0, RouteDecision.WAIT_MS, RouteDecision.WAIT_MS * 10})
                            assertEquals(true, decide(s, has, core, legacy, waited) != null);
    }
}
