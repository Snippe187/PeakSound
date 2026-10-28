package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.peaksound.protocol.DisplayWallLogic.Facing;
import de.peaksound.protocol.DisplayWallLogic.Pos;
import de.peaksound.protocol.DisplayWallLogic.Tile;
import de.peaksound.protocol.DisplayWallLogic.Wall;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DisplayWallLogicTest {
    private static Tile tileAt(Wall w, int x, int y, int z) {
        return w.tiles().stream().filter(t -> t.pos().equals(new Pos(x, y, z))).findFirst().orElseThrow();
    }

    @Test
    void rectangleOnASouthFacingWallIsOneWallWithColumnsLeftToRight() {
        // 3 breit (x 10..12), 2 hoch (y 5..6), Bild zeigt nach Sueden: Betrachter schaut nach Norden, rechts = +x
        Map<Pos, Facing> d = Map.of(
                new Pos(10, 6, 0), Facing.SOUTH, new Pos(11, 6, 0), Facing.SOUTH, new Pos(12, 6, 0), Facing.SOUTH,
                new Pos(10, 5, 0), Facing.SOUTH, new Pos(11, 5, 0), Facing.SOUTH, new Pos(12, 5, 0), Facing.SOUTH);
        List<Wall> walls = DisplayWallLogic.walls(d);
        assertEquals(1, walls.size());
        Wall w = walls.get(0);
        assertEquals(3, w.width());
        assertEquals(2, w.height());
        assertEquals(0, tileAt(w, 10, 6, 0).col());
        assertEquals(0, tileAt(w, 10, 6, 0).row());
        assertEquals(2, tileAt(w, 12, 5, 0).col());
        assertEquals(1, tileAt(w, 12, 5, 0).row());
    }

    @Test
    void northFacingWallRunsTheOtherWayAlongX() {
        Map<Pos, Facing> d = DisplayWallLogic.of(Facing.NORTH, new Pos(0, 0, 0), new Pos(1, 0, 0));
        Wall w = DisplayWallLogic.walls(d).get(0);
        assertEquals(2, w.width());
        assertEquals(0, tileAt(w, 1, 0, 0).col()); // vom Norden aus gesehen liegt +x links
        assertEquals(1, tileAt(w, 0, 0, 0).col());
    }

    @Test
    void eastFacingWallUsesZ() {
        Map<Pos, Facing> d = DisplayWallLogic.of(Facing.EAST, new Pos(0, 0, 0), new Pos(0, 0, 1));
        Wall w = DisplayWallLogic.walls(d).get(0);
        assertEquals(0, tileAt(w, 0, 0, 1).col()); // Betrachter im Osten schaut nach Westen: rechts = -z
        assertEquals(1, tileAt(w, 0, 0, 0).col());
    }

    @Test
    void lShapeFallsBackToSingleBlocks() {
        Map<Pos, Facing> d = DisplayWallLogic.of(Facing.SOUTH, new Pos(0, 0, 0), new Pos(1, 0, 0), new Pos(0, 1, 0));
        List<Wall> walls = DisplayWallLogic.walls(d);
        assertEquals(3, walls.size());
        assertTrue(walls.stream().allMatch(w -> w.width() == 1 && w.height() == 1));
    }

    @Test
    void differentFacingOrPlaneNeverJoin() {
        Map<Pos, Facing> d = Map.of(new Pos(0, 0, 0), Facing.SOUTH, new Pos(1, 0, 0), Facing.NORTH, new Pos(2, 0, 1), Facing.SOUTH);
        assertEquals(3, DisplayWallLogic.walls(d).size());
        Map<Pos, Facing> split = Map.of(new Pos(0, 0, 0), Facing.SOUTH, new Pos(1, 0, 1), Facing.SOUTH);
        assertEquals(2, DisplayWallLogic.walls(split).size());
    }

    @Test
    void tooLargeWallIsSplit() {
        Pos[] row = new Pos[DisplayWallLogic.MAX_SIDE + 1];
        for (int i = 0; i < row.length; i++) row[i] = new Pos(i, 0, 0);
        assertEquals(row.length, DisplayWallLogic.walls(DisplayWallLogic.of(Facing.SOUTH, row)).size());
    }

    @Test
    void sourceRectCropsTheLongSideAndTilesPartitionTheImage() {
        // quadratisches Cover auf einer 2x1-Wand: oben und unten wird beschnitten
        int[] left = DisplayWallLogic.sourceRect(100, 100, 2, 1, 0, 0);
        int[] right = DisplayWallLogic.sourceRect(100, 100, 2, 1, 1, 0);
        assertArrayEquals(new int[] {0, 25, 50, 50}, left);
        assertArrayEquals(new int[] {50, 25, 50, 50}, right);
        // 1x1 mit quadratischem Bild: alles
        assertArrayEquals(new int[] {0, 0, 100, 100}, DisplayWallLogic.sourceRect(100, 100, 1, 1, 0, 0));
    }

    @Test
    void facingCycleAndLookup() {
        assertEquals(Facing.WEST, Facing.SOUTH.next());
        assertEquals(Facing.SOUTH, Facing.EAST.next());
        assertEquals(Facing.SOUTH, Facing.byOrdinal(99));
    }
}