package de.peaksound.protocol;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Display-Bloecke, die nebeneinander in einer Ebene liegen und ein volles Rechteck bilden, zeigen zusammen ein grosses
 * Bild (Cover-Wand). Hier steckt nur die Geometrie (ohne Minecraft, mit Unit-Tests): welche Bloecke eine Wand bilden und
 * welche Kachel (Spalte/Zeile von links oben, aus Sicht des Betrachters) jeder Block ist.
 */
public final class DisplayWallLogic {
    /** Groesste Wand in Kacheln je Richtung; groessere Flaechen werden als Einzelbloecke behandelt. */
    public static final int MAX_SIDE = 8;

    private DisplayWallLogic() {}

    /** Blickrichtung des Bildschirms: die Seite, auf der das Bild zu sehen ist. */
    public enum Facing {
        SOUTH(0, 1), WEST(-1, 0), NORTH(0, -1), EAST(1, 0);

        private final int dx;
        private final int dz;

        Facing(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public int dx() { return dx; }

        public int dz() { return dz; }

        /** "Rechts" aus Sicht eines Betrachters, der auf das Bild schaut (x-Anteil). */
        public int rightX() { return dz; }

        /** "Rechts" aus Sicht des Betrachters (z-Anteil). */
        public int rightZ() { return -dx; }

        public Facing next() { return values()[(ordinal() + 1) % values().length]; }

        public static Facing byOrdinal(int o) {
            Facing[] v = values();
            return o < 0 || o >= v.length ? SOUTH : v[o];
        }
    }

    public record Pos(int x, int y, int z) {}

    /** Ein Block der Wand: Spalte von links, Zeile von oben (aus Sicht des Betrachters). */
    public record Tile(Pos pos, int col, int row) {}

    public record Wall(Facing facing, List<Tile> tiles, int width, int height) {}

    private static int plane(Pos p, Facing f) { return p.x() * f.dx() + p.z() * f.dz(); }

    private static int u(Pos p, Facing f) { return p.x() * f.rightX() + p.z() * f.rightZ(); }

    /** Fasst alle Display-Bloecke zu Waenden zusammen; Bloecke, die kein volles Rechteck bilden, sind Einzelbloecke. */
    public static List<Wall> walls(Map<Pos, Facing> displays) {
        List<Wall> out = new ArrayList<>();
        Set<Pos> seen = new HashSet<>();
        for (Map.Entry<Pos, Facing> entry : displays.entrySet()) {
            if (seen.contains(entry.getKey())) continue;
            Facing f = entry.getValue();
            List<Pos> component = new ArrayList<>();
            Deque<Pos> queue = new ArrayDeque<>();
            queue.add(entry.getKey());
            seen.add(entry.getKey());
            while (!queue.isEmpty()) {
                Pos p = queue.poll();
                component.add(p);
                int rx = f.rightX(), rz = f.rightZ();
                Pos[] neighbours = {
                        new Pos(p.x() + rx, p.y(), p.z() + rz), new Pos(p.x() - rx, p.y(), p.z() - rz),
                        new Pos(p.x(), p.y() + 1, p.z()), new Pos(p.x(), p.y() - 1, p.z())};
                for (Pos n : neighbours) {
                    if (!seen.contains(n) && displays.get(n) == f && plane(n, f) == plane(p, f)) {
                        seen.add(n);
                        queue.add(n);
                    }
                }
            }
            out.addAll(split(component, f));
        }
        return out;
    }

    private static List<Wall> split(List<Pos> component, Facing f) {
        int minU = Integer.MAX_VALUE, maxU = Integer.MIN_VALUE, minV = Integer.MAX_VALUE, maxV = Integer.MIN_VALUE;
        for (Pos p : component) {
            minU = Math.min(minU, u(p, f));
            maxU = Math.max(maxU, u(p, f));
            minV = Math.min(minV, p.y());
            maxV = Math.max(maxV, p.y());
        }
        int w = maxU - minU + 1, h = maxV - minV + 1;
        List<Wall> out = new ArrayList<>();
        if (w * h == component.size() && w <= MAX_SIDE && h <= MAX_SIDE) {
            List<Tile> tiles = new ArrayList<>();
            for (Pos p : component) tiles.add(new Tile(p, u(p, f) - minU, maxV - p.y()));
            out.add(new Wall(f, List.copyOf(tiles), w, h));
        } else {
            for (Pos p : component) out.add(new Wall(f, List.of(new Tile(p, 0, 0)), 1, 1));
        }
        return out;
    }

    /**
     * Welcher Ausschnitt des Quellbilds gehoert zu einer Kachel? Das Bild wird mittig zugeschnitten ("cover"), sodass es die
     * ganze Wand fuellt. Liefert {x, y, breite, hoehe} im Quellbild.
     */
    public static int[] sourceRect(int srcW, int srcH, int wallW, int wallH, int col, int row) {
        double wallAspect = (double) wallW / wallH;
        double srcAspect = (double) srcW / srcH;
        double cropW = srcAspect > wallAspect ? srcH * wallAspect : srcW; // Quelle breiter als die Wand: seitlich beschneiden
        double cropH = srcAspect > wallAspect ? srcH : srcW / wallAspect;
        double x0 = (srcW - cropW) / 2.0, y0 = (srcH - cropH) / 2.0;
        int x = (int) Math.round(x0 + cropW * col / wallW);
        int y = (int) Math.round(y0 + cropH * row / wallH);
        int w = (int) Math.round(cropW / wallW);
        int h = (int) Math.round(cropH / wallH);
        return new int[] {x, y, Math.max(1, w), Math.max(1, h)};
    }

    /** Nur fuer Tests: Zuordnung Position -> Facing aus einer Liste bauen. */
    static Map<Pos, Facing> of(Facing f, Pos... positions) {
        Map<Pos, Facing> m = new HashMap<>();
        for (Pos p : positions) m.put(p, f);
        return m;
    }
}