package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class SyncMathTest {
    @Test
    void clockOffsetUsesTheMiddleOfTheRoundTrip() {
        // Client sendet bei 1000, empfaengt bei 1100: Mitte = 1050. Server meldet 5050 -> Server ist 4000 ms voraus.
        assertEquals(4000, SyncMath.clockOffsetMs(1000, 1100, 5050));
        // negative Abweichung (Server hinkt hinterher)
        assertEquals(-500, SyncMath.clockOffsetMs(2000, 2000, 1500));
        // ohne Sendezeit (unbekannt) zaehlt die Empfangszeit
        assertEquals(10, SyncMath.clockOffsetMs(0, 100, 110));
    }

    @Test
    void positionNeverGoesNegative() {
        assertEquals(1500, SyncMath.positionMs(11_500, 10_000));
        assertEquals(0, SyncMath.positionMs(9_000, 10_000));
    }

    @Test
    void lateJoinerComputesTheSamePositionAsEveryoneElse() {
        long startedAtServer = 1_000_000;
        // Spieler A bekommt das Start-Paket sofort, Spieler B erst 12 Sekunden spaeter
        long serverTimeA = startedAtServer + 100;
        int offsetA = 100;
        long serverTimeB = startedAtServer + 12_000;
        int offsetB = 12_000;
        assertEquals(startedAtServer, SyncMath.startedAt(serverTimeA, offsetA));
        assertEquals(startedAtServer, SyncMath.startedAt(serverTimeB, offsetB));
        long now = startedAtServer + 20_000;
        assertEquals(SyncMath.positionMs(now, SyncMath.startedAt(serverTimeA, offsetA)),
                SyncMath.positionMs(now, SyncMath.startedAt(serverTimeB, offsetB)));
    }

    @Test
    void clientWithSkewedClockStillAgrees() {
        // Der Client geht 3 s vor. Mit dem Offset landet er trotzdem auf der Serverzeit.
        long serverNow = 50_000;
        long clientNow = serverNow + 3_000;
        long offset = SyncMath.clockOffsetMs(clientNow - 40, clientNow + 40, serverNow);
        assertEquals(serverNow, clientNow + offset);
    }

    @Test
    void framesFollowPosition() {
        assertEquals(44_100, SyncMath.frameAt(1000, 44_100));
        assertEquals(66_150, SyncMath.frameAt(1500, 44_100));
        assertEquals(0, SyncMath.frameAt(0, 48_000));
    }

    @Test
    void longestRunningSongLeadsTheNetwork() {
        var leader = SyncMath.pickLeader(List.of(
                new SyncMath.Candidate<>("B", 2_000L), new SyncMath.Candidate<>("A", 1_000L), new SyncMath.Candidate<>("C", 3_000L)));
        assertEquals("A", leader.orElseThrow().key());
    }

    @Test
    void emptyNetworkHasNoLeaderAndTiesAreStable() {
        assertTrue(SyncMath.pickLeader(List.<SyncMath.Candidate<String>>of()).isEmpty());
        var leader = SyncMath.pickLeader(List.of(new SyncMath.Candidate<>("X", 5L), new SyncMath.Candidate<>("Y", 5L)));
        assertEquals("X", leader.orElseThrow().key());
    }
}
