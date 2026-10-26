package de.peaksound.mod.client;

import java.util.Arrays;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Ein drehbarer Mini-Wuerfel fuer Menues (kleiner Software-Renderer: Rotation, Rueckseiten-Culling, Flaechenfuellung per
 * Scanline). Flaechen 0-3 = Norden, Osten, Sueden, Westen, 4 = oben, 5 = unten (wie in Minecraft: Norden = -Z, Osten = +X).
 * Mit der Maus drehen ({@link #drag}) und eine Seite anklicken ({@link #pick}).
 */
final class CubeView {
    static final String[] NAMES = {"N", "E", "S", "W", "\u2191", "\u2193"};
    static final String[] NAMES_KEYS = {"north", "east", "south", "west", "up", "down"};

    // Eckpunkte je Flaeche (x, y, z) und Normalen
    private static final double[][][] FACES = {
            {{-1, -1, -1}, {1, -1, -1}, {1, 1, -1}, {-1, 1, -1}},   // Norden (-Z)
            {{1, -1, -1}, {1, -1, 1}, {1, 1, 1}, {1, 1, -1}},       // Osten (+X)
            {{-1, -1, 1}, {1, -1, 1}, {1, 1, 1}, {-1, 1, 1}},       // Sueden (+Z)
            {{-1, -1, -1}, {-1, -1, 1}, {-1, 1, 1}, {-1, 1, -1}},   // Westen (-X)
            {{-1, 1, -1}, {1, 1, -1}, {1, 1, 1}, {-1, 1, 1}},       // oben (+Y)
            {{-1, -1, -1}, {1, -1, -1}, {1, -1, 1}, {-1, -1, 1}}    // unten (-Y)
    };
    private static final double[][] NORMALS = {{0, 0, -1}, {1, 0, 0}, {0, 0, 1}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
    /** Helligkeit je Flaeche (oben hell, unten dunkel), damit der Wuerfel plastisch wirkt. */
    private static final double[] SHADE = {0.80, 0.92, 0.80, 0.70, 1.0, 0.55};

    double yaw = 215;
    double pitch = 28;
    int selected = 0;

    /** Zuletzt gezeichnete Polygone (Bildschirmkoordinaten) je Flaeche, null = nicht sichtbar. */
    private final int[][] polyX = new int[6][];
    private final int[][] polyY = new int[6][];

    void drag(double dx, double dy) {
        yaw += dx * 0.8;
        pitch = Math.max(-85, Math.min(85, pitch + dy * 0.8));
    }

    private double[] project(double[] p) {
        double ry = Math.toRadians(yaw), rp = Math.toRadians(pitch);
        double x1 = p[0] * Math.cos(ry) + p[2] * Math.sin(ry);
        double z1 = -p[0] * Math.sin(ry) + p[2] * Math.cos(ry);
        double y2 = p[1] * Math.cos(rp) - z1 * Math.sin(rp);
        double z2 = p[1] * Math.sin(rp) + z1 * Math.cos(rp);
        return new double[] {x1, y2, z2};
    }

    /**
     * @param fill   Grundfarbe (0xRRGGBB) je Flaeche
     * @param line1  obere Beschriftung je Flaeche, {@code line2} untere
     */
    void render(GuiGraphicsExtractor g, Font font, int cx, int cy, int scale, int[] fill, String[] line1, String[] line2) {
        Arrays.fill(polyX, null);
        Arrays.fill(polyY, null);
        for (int f = 0; f < 6; f++) {
            double[] n = project(NORMALS[f]);
            if (n[2] <= 0.02) continue; // Rueckseite
            int[] xs = new int[4], ys = new int[4];
            for (int i = 0; i < 4; i++) {
                double[] p = project(FACES[f][i]);
                xs[i] = (int) Math.round(cx + p[0] * scale * 0.55);
                ys[i] = (int) Math.round(cy - p[1] * scale * 0.55);
            }
            polyX[f] = xs;
            polyY[f] = ys;
            double shade = SHADE[f] * (0.75 + 0.25 * n[2]);
            fillPolygon(g, xs, ys, shaded(fill[f], shade));
            outline(g, xs, ys, f == selected ? 0xFFFFFFFF : 0xFF14141A, f == selected ? 2 : 1);
            int mx = (xs[0] + xs[1] + xs[2] + xs[3]) / 4, my = (ys[0] + ys[1] + ys[2] + ys[3]) / 4;

            g.text(font, NAMES[f], mx - font.width(NAMES[f]) / 2, my - 10, 0xFFFFFFFF);
            if (!line2[f].isEmpty()) g.text(font, line2[f], mx - font.width(line2[f]) / 2, my + 1, 0xFFF0F0F0);
        }
    }

    /** Die Flaeche unter dem Mauszeiger (0-5) oder -1. */
    int pick(double mx, double my) {
        for (int f = 0; f < 6; f++) {
            if (polyX[f] != null && inside(polyX[f], polyY[f], mx, my)) return f;
        }
        return -1;
    }

    private static boolean inside(int[] xs, int[] ys, double px, double py) {
        boolean positive = false, negative = false;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            double cross = (xs[j] - xs[i]) * (py - ys[i]) - (ys[j] - ys[i]) * (px - xs[i]);
            if (cross > 0) positive = true;
            if (cross < 0) negative = true;
        }
        return !(positive && negative);
    }

    private static int shaded(int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k), gr = (int) Math.min(255, ((rgb >> 8) & 255) * k), b = (int) Math.min(255, (rgb & 255) * k);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static void fillPolygon(GuiGraphicsExtractor g, int[] xs, int[] ys, int color) {
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int y : ys) {
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        for (int y = minY; y < maxY; y++) {
            double sample = y + 0.5;
            double left = Double.MAX_VALUE, right = -Double.MAX_VALUE;
            for (int i = 0; i < 4; i++) {
                int j = (i + 1) % 4;
                double y0 = ys[i], y1 = ys[j];
                if ((y0 <= sample && y1 > sample) || (y1 <= sample && y0 > sample)) {
                    double x = xs[i] + (sample - y0) * (xs[j] - xs[i]) / (y1 - y0);
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
            if (left < right) g.fill((int) Math.round(left), y, (int) Math.round(right), y + 1, color);
        }
    }

    private static void outline(GuiGraphicsExtractor g, int[] xs, int[] ys, int color, int width) {
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            int steps = Math.max(Math.abs(xs[j] - xs[i]), Math.abs(ys[j] - ys[i]));
            for (int s = 0; s <= steps; s++) {
                int x = xs[i] + (steps == 0 ? 0 : (xs[j] - xs[i]) * s / steps);
                int y = ys[i] + (steps == 0 ? 0 : (ys[j] - ys[i]) * s / steps);
                g.fill(x, y, x + width, y + width, color);
            }
        }
    }
}