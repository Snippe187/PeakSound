package de.peaksound.protocol;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Findet das Redstone-Netz einer PeakSound-Jukebox: alle PeakSound-Bloecke, die ueber Redstone-Staub
 * miteinander verbunden sind. Das Signal spielt keine Rolle, nur die Verbindung zaehlt. Die Suche arbeitet
 * auf einem abstrakten {@link Grid}, ist also ohne Minecraft testbar, und ist hart begrenzt, damit niemand
 * mit riesigen Staubfeldern den Server lahmlegen kann.
 *
 * <p>Verbindungsregeln (wie beim Staub in Vanilla):
 * <ul>
 *   <li>Staub verbindet sich mit Staub auf gleicher Hoehe (4 Richtungen).</li>
 *   <li>Staub steigt an einem festen Block hoch, wenn ueber dem Staub nichts Festes liegt.</li>
 *   <li>Staub faellt neben sich ab, wenn der Block daneben nicht fest ist.</li>
 *   <li>Ein PeakSound-Block ist mit Staub verbunden, der ihn seitlich beruehrt oder auf ihm liegt.</li>
 *   <li>PeakSound-Bloecke sind selbst Knoten: Staub links und rechts von einer Jukebox gehoert zum selben Netz.</li>
 * </ul>
 */
public final class DustNetwork {
    private DustNetwork() {}

    /** Ganzzahlige Blockposition. */
    public record Pos(int x, int y, int z) {
        Pos add(int dx, int dy, int dz) { return new Pos(x + dx, y + dy, z + dz); }
    }

    /** Sicht auf die Welt. Nicht geladene Bereiche muessen wie leere Luft antworten. */
    public interface Grid {
        boolean isDust(int x, int y, int z);

        /** Fester, undurchsichtiger Block, der Staub blockiert und Staub tragen kann. */
        boolean isSolid(int x, int y, int z);

        /** Der PeakSound-Block an dieser Stelle, oder null. */
        PeakBlockType device(int x, int y, int z);
    }

    /**
     * @param devices   alle gefundenen PeakSound-Bloecke (inklusive Startblock) mit ihrem Typ
     * @param dust      alle Staub-Positionen des Netzes
     * @param truncated true, wenn ein Limit erreicht wurde und das Netz in Wirklichkeit groesser ist
     */
    public record Result(Map<Pos, PeakBlockType> devices, Set<Pos> dust, boolean truncated) {}

    private static final int[][] HORIZONTAL = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /**
     * Wie {@link #find}, beginnt aber an einem Staubblock statt an einem Geraet. Gibt es kein verbundenes Geraet,
     * ist das Ergebnis ohne Geraete. Damit findet das DJ-Pult heraus, welches Netz hinter einem seiner Staubblocke liegt.
     */
    public static Result findFromDust(Grid grid, Pos startDust, int maxDust, int maxDevices) {
        Map<Pos, PeakBlockType> devices = new LinkedHashMap<>();
        Set<Pos> dust = new HashSet<>();
        if (!grid.isDust(startDust.x(), startDust.y(), startDust.z())) return new Result(devices, dust, false);
        dust.add(startDust);
        boolean truncated = false;
        Deque<Pos> queue = new ArrayDeque<>();
        queue.add(startDust);
        Deque<Pos> deviceQueue = new ArrayDeque<>();
        while (!queue.isEmpty() || !deviceQueue.isEmpty()) {
            if (!deviceQueue.isEmpty()) {
                Pos node = deviceQueue.poll();
                if (!devices.get(node).bridgesNetworks()) continue;
                for (Pos d : deviceNeighbours(grid, node)) {
                    if (dust.contains(d)) continue;
                    if (dust.size() >= maxDust) { truncated = true; continue; }
                    dust.add(d);
                    queue.add(d);
                }
                continue;
            }
            Pos node = queue.poll();
            for (Pos next : dustNeighbours(grid, node)) {
                if (dust.contains(next)) continue;
                if (dust.size() >= maxDust) { truncated = true; continue; }
                dust.add(next);
                queue.add(next);
            }
            for (Pos dev : touchedDevices(grid, node)) {
                if (devices.containsKey(dev)) continue;
                if (devices.size() >= maxDevices) { truncated = true; continue; }
                devices.put(dev, grid.device(dev.x(), dev.y(), dev.z()));
                deviceQueue.add(dev);
            }
        }
        return new Result(devices, dust, truncated);
    }

    public static Result find(Grid grid, Pos start, int maxDust, int maxDevices) {
        Map<Pos, PeakBlockType> devices = new LinkedHashMap<>();
        Set<Pos> dust = new HashSet<>();
        PeakBlockType startType = grid.device(start.x(), start.y(), start.z());
        if (startType == null) return new Result(devices, dust, false);
        devices.put(start, startType);

        boolean truncated = false;
        Deque<Pos> queue = new ArrayDeque<>();
        queue.add(start);
        while (!queue.isEmpty()) {
            Pos node = queue.poll();
            if (devices.containsKey(node)) {
                // Das DJ-Pult haengt zwei Netze an sich, legt sie aber nicht zusammen (nur Blatt, nie Knoten)
                if (!devices.get(node).bridgesNetworks() && !node.equals(start)) continue;
                // Geraet: Staub an den Seiten und auf dem Geraet gehoert zum Netz
                for (Pos d : deviceNeighbours(grid, node)) {
                    if (dust.contains(d)) continue;
                    if (dust.size() >= maxDust) { truncated = true; continue; }
                    dust.add(d);
                    queue.add(d);
                }
            } else {
                // Staub: Nachbarstaub und beruehrte Geraete
                for (Pos next : dustNeighbours(grid, node)) {
                    if (dust.contains(next)) continue;
                    if (dust.size() >= maxDust) { truncated = true; continue; }
                    dust.add(next);
                    queue.add(next);
                }
                for (Pos dev : touchedDevices(grid, node)) {
                    if (devices.containsKey(dev)) continue;
                    if (devices.size() >= maxDevices) { truncated = true; continue; }
                    devices.put(dev, grid.device(dev.x(), dev.y(), dev.z()));
                    queue.add(dev);
                }
            }
        }
        return new Result(devices, dust, truncated);
    }

    /** Staub, das ein Geraet beruehrt: auf dem Geraet und an den vier Seiten. */
    private static Iterable<Pos> deviceNeighbours(Grid grid, Pos device) {
        Set<Pos> out = new java.util.LinkedHashSet<>();
        if (grid.isDust(device.x(), device.y() + 1, device.z())) out.add(device.add(0, 1, 0));
        for (int[] h : HORIZONTAL) {
            if (grid.isDust(device.x() + h[0], device.y(), device.z() + h[1])) out.add(device.add(h[0], 0, h[1]));
        }
        return out;
    }

    private static Iterable<Pos> dustNeighbours(Grid grid, Pos p) {
        Set<Pos> out = new java.util.LinkedHashSet<>();
        for (int[] h : HORIZONTAL) {
            int nx = p.x() + h[0], nz = p.z() + h[1];
            if (grid.isDust(nx, p.y(), nz)) out.add(new Pos(nx, p.y(), nz));
            // Aufwaerts: neben dem Staub steht ein fester Block mit Staub darauf, ueber uns ist frei
            if (grid.isSolid(nx, p.y(), nz) && !grid.isSolid(p.x(), p.y() + 1, p.z()) && grid.isDust(nx, p.y() + 1, nz)) {
                out.add(new Pos(nx, p.y() + 1, nz));
            }
            // Abwaerts: neben dem Staub ist kein fester Block, darunter liegt Staub
            if (!grid.isSolid(nx, p.y(), nz) && grid.isDust(nx, p.y() - 1, nz)) out.add(new Pos(nx, p.y() - 1, nz));
        }
        return out;
    }

    /** Geraete, die dieser Staub beruehrt: an den vier Seiten und unter dem Staub. */
    private static Iterable<Pos> touchedDevices(Grid grid, Pos dust) {
        Set<Pos> out = new java.util.LinkedHashSet<>();
        if (grid.device(dust.x(), dust.y() - 1, dust.z()) != null) out.add(dust.add(0, -1, 0));
        for (int[] h : HORIZONTAL) {
            if (grid.device(dust.x() + h[0], dust.y(), dust.z() + h[1]) != null) out.add(dust.add(h[0], 0, h[1]));
        }
        return out;
    }
}
