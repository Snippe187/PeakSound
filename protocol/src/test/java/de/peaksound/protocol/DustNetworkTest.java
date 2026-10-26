package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import de.peaksound.protocol.DustNetwork.Pos;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DustNetworkTest {
    /** Kleine Testwelt aus Staub, festen Bloecken und PeakSound-Geraeten. */
    private static final class World implements DustNetwork.Grid {
        final Set<Pos> dust = new HashSet<>();
        final Set<Pos> solid = new HashSet<>();
        final Map<Pos, PeakBlockType> devices = new HashMap<>();

        World dust(int x, int y, int z) { dust.add(new Pos(x, y, z)); return this; }
        World solid(int x, int y, int z) { solid.add(new Pos(x, y, z)); return this; }
        World jukebox(int x, int y, int z) { devices.put(new Pos(x, y, z), PeakBlockType.JUKEBOX); solid(x, y, z); return this; }
        World desk(int x, int y, int z) { devices.put(new Pos(x, y, z), PeakBlockType.DJ_DESK); solid(x, y, z); return this; }
        World speaker(int x, int y, int z) { devices.put(new Pos(x, y, z), PeakBlockType.SPEAKER); solid(x, y, z); return this; }
        World dustLine(int fromX, int toX, int y, int z) { for (int x = fromX; x <= toX; x++) dust(x, y, z); return this; }

        @Override public boolean isDust(int x, int y, int z) { return dust.contains(new Pos(x, y, z)); }
        @Override public boolean isSolid(int x, int y, int z) { return solid.contains(new Pos(x, y, z)); }
        @Override public PeakBlockType device(int x, int y, int z) { return devices.get(new Pos(x, y, z)); }
    }

    private static DustNetwork.Result find(World w, int x, int y, int z) {
        return DustNetwork.find(w, new Pos(x, y, z), 512, 32);
    }

    @Test
    void lonelyJukeboxIsItsOwnNetwork() {
        World w = new World().jukebox(0, 0, 0);
        DustNetwork.Result r = find(w, 0, 0, 0);
        assertEquals(1, r.devices().size());
        assertTrue(r.dust().isEmpty());
        assertFalse(r.truncated());
    }

    @Test
    void notAJukeboxGivesEmptyResult() {
        assertTrue(find(new World(), 0, 0, 0).devices().isEmpty());
    }

    @Test
    void twoJukeboxesConnectedByDustLine() {
        World w = new World().jukebox(0, 0, 0).dustLine(1, 5, 0, 0).jukebox(6, 0, 0);
        DustNetwork.Result r = find(w, 0, 0, 0);
        assertEquals(2, r.devices().size());
        assertEquals(5, r.dust().size());
        // gleiche Antwort von der anderen Seite
        assertEquals(r.devices().keySet(), find(w, 6, 0, 0).devices().keySet());
    }

    @Test
    void gapInTheDustSeparatesNetworks() {
        World w = new World().jukebox(0, 0, 0).dustLine(1, 2, 0, 0).dustLine(4, 5, 0, 0).jukebox(6, 0, 0);
        assertEquals(1, find(w, 0, 0, 0).devices().size());
        assertEquals(1, find(w, 6, 0, 0).devices().size());
    }

    @Test
    void jukeboxesWithoutDustAreNotConnectedEvenWhenAdjacent() {
        World w = new World().jukebox(0, 0, 0).jukebox(1, 0, 0);
        assertEquals(1, find(w, 0, 0, 0).devices().size());
    }

    @Test
    void dustOnTopOfAJukeboxConnects() {
        World w = new World().jukebox(0, 0, 0).dust(0, 1, 0).dustLine(1, 3, 1, 0).solid(4, 0, 0).jukebox(4, 0, 0).dust(4, 1, 0);
        // Staub auf beiden Jukeboxen, verbunden ueber eine Linie auf Hoehe 1
        w.dust(3, 1, 0);
        assertEquals(2, find(w, 0, 0, 0).devices().size());
    }

    @Test
    void dustClimbsUpASolidBlock() {
        // Staub (1,0,0), fester Block (2,0,0), Staub darauf (2,1,0), dann Staub (3,1,0) und Jukebox daneben
        World w = new World().jukebox(0, 0, 0).dust(1, 0, 0).solid(2, 0, 0).dust(2, 1, 0).dust(3, 1, 0).jukebox(4, 1, 0);
        assertEquals(2, find(w, 0, 0, 0).devices().size());
    }

    @Test
    void solidBlockAboveDustBlocksTheClimb() {
        World w = new World().jukebox(0, 0, 0).dust(1, 0, 0).solid(2, 0, 0).dust(2, 1, 0).dust(3, 1, 0).jukebox(4, 1, 0)
                .solid(1, 1, 0); // Deckel ueber dem ersten Staub
        assertEquals(1, find(w, 0, 0, 0).devices().size());
    }

    @Test
    void dustDescendsAStep() {
        World w = new World().jukebox(0, 1, 0).dust(1, 1, 0).dust(2, 0, 0).dust(3, 0, 0).jukebox(4, 0, 0);
        assertEquals(2, find(w, 0, 1, 0).devices().size());
    }

    @Test
    void solidBlockBesideDustBlocksTheDescent() {
        World w = new World().jukebox(0, 1, 0).dust(1, 1, 0).solid(2, 1, 0).dust(2, 0, 0).dust(3, 0, 0).jukebox(4, 0, 0);
        assertEquals(1, find(w, 0, 1, 0).devices().size());
    }

    @Test
    void junctionsAndSpeakersAreCollected() {
        World w = new World().jukebox(0, 0, 0).dustLine(1, 3, 0, 0)
                .dust(3, 0, 1).dust(3, 0, 2).speaker(3, 0, 3)  // Abzweig zu einem Lautsprecher
                .dust(2, 0, -1).jukebox(2, 0, -2);               // Abzweig zu einer zweiten Jukebox (Staub beruehrt sie von der Seite)
        w.dust(2, 0, -2 + 1); // direkt neben der zweiten Jukebox
        DustNetwork.Result r = find(w, 0, 0, 0);
        assertEquals(3, r.devices().size());
        assertTrue(r.devices().containsValue(PeakBlockType.SPEAKER));
    }

    @Test
    void connectsThroughADevice() {
        // Jukebox A - Staub - Jukebox B - Staub - Jukebox C: B vermittelt zwischen den Staublinien
        World w = new World().jukebox(0, 0, 0).dust(1, 0, 0).jukebox(2, 0, 0).dust(3, 0, 0).jukebox(4, 0, 0);
        assertEquals(3, find(w, 0, 0, 0).devices().size());
        assertEquals(3, find(w, 4, 0, 0).devices().size());
    }

    @Test
    void djDeskKeepsItsTwoDecksSeparate() {
        // Jukebox A - Staub - DJ-Pult - Staub - Jukebox B: zwei getrennte Netze, das Pult haengt an beiden
        World w = new World().jukebox(0, 0, 0).dust(1, 0, 0).dust(2, 0, 0).desk(3, 0, 0).dust(4, 0, 0).dust(5, 0, 0).jukebox(6, 0, 0);
        DustNetwork.Result a = find(w, 0, 0, 0);
        DustNetwork.Result b = find(w, 6, 0, 0);
        assertEquals(2, a.devices().size()); // Jukebox A + Pult
        assertTrue(a.devices().containsValue(PeakBlockType.DJ_DESK));
        assertEquals(2, b.devices().size()); // Jukebox B + Pult
        assertEquals(1, a.devices().values().stream().filter(t -> t == PeakBlockType.JUKEBOX).count());
        assertEquals(1, b.devices().values().stream().filter(t -> t == PeakBlockType.JUKEBOX).count());
    }

    @Test
    void searchFromDustFindsTheNetworkBehindIt() {
        World w = new World().jukebox(0, 0, 0).dustLine(1, 4, 0, 0).speaker(5, 0, 0);
        DustNetwork.Result r = DustNetwork.findFromDust(w, new Pos(3, 0, 0), 512, 32);
        assertEquals(2, r.devices().size());
        assertTrue(r.devices().containsValue(PeakBlockType.JUKEBOX));
        assertTrue(DustNetwork.findFromDust(w, new Pos(40, 0, 0), 512, 32).devices().isEmpty()); // kein Staub dort
    }

    @Test
    void searchFromDustDoesNotCrossADjDesk() {
        World w = new World().dustLine(1, 2, 0, 0).desk(3, 0, 0).dustLine(4, 5, 0, 0).jukebox(6, 0, 0);
        DustNetwork.Result left = DustNetwork.findFromDust(w, new Pos(1, 0, 0), 512, 32);
        assertEquals(1, left.devices().size()); // nur das Pult, nicht die Jukebox dahinter
        assertTrue(left.devices().containsValue(PeakBlockType.DJ_DESK));
    }

    @Test
    void dustLimitTruncatesTheSearch() {
        World w = new World().jukebox(0, 0, 0).dustLine(1, 300, 0, 0).jukebox(301, 0, 0);
        DustNetwork.Result r = DustNetwork.find(w, new Pos(0, 0, 0), 50, 32);
        assertTrue(r.truncated());
        assertEquals(50, r.dust().size());
        assertEquals(1, r.devices().size());
    }

    @Test
    void deviceLimitTruncatesTheSearch() {
        World w = new World().jukebox(0, 0, 0);
        for (int i = 1; i <= 10; i++) w.dust(i * 2 - 1, 0, 0).jukebox(i * 2, 0, 0);
        DustNetwork.Result r = DustNetwork.find(w, new Pos(0, 0, 0), 512, 4);
        assertTrue(r.truncated());
        assertEquals(4, r.devices().size());
    }

    @Test
    void ringOfDustTerminates() {
        World w = new World().jukebox(0, 0, 0);
        for (int x = 1; x <= 4; x++) w.dust(x, 0, 0).dust(x, 0, 4);
        for (int z = 0; z <= 4; z++) w.dust(1, 0, z).dust(4, 0, z);
        DustNetwork.Result r = find(w, 0, 0, 0);
        assertFalse(r.truncated());
        assertEquals(w.dust.size(), r.dust().size());
    }
}
