package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.PeakItemType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Verbindungsanzeige: Wer einen Peak-Tuner in der Hand haelt, sieht leuchtende Linien von jeder Jukebox zu den
 * Geraeten, die an ihrem Redstone-Netz haengen (Lautsprecher tuerkis, weitere Jukeboxen orange). Gezeichnet wird mit
 * Partikeln, die nur dieser Spieler sieht, das funktioniert also auch ohne Mod. Es wird nur gerechnet, solange jemand
 * einen Tuner haelt; Netze kommen aus dem Cache, Reichweite und Linienlaenge sind begrenzt.
 */
final class ConnectionDisplay {
    /** Jukeboxen und Geraete weiter als so viele Bloecke vom Spieler werden nicht gezeichnet. */
    private static final double RADIUS = 40;
    /** Hoechstzahl Partikel pro Spieler und Durchgang (schuetzt Client und Netzwerk). */
    private static final int MAX_PARTICLES = 450;
    private static final double STEP = 0.6;

    private static final Particle.DustOptions SPEAKER = new Particle.DustOptions(Color.fromRGB(0x38, 0xd6, 0xc4), 1.0f);
    private static final Particle.DustOptions JUKEBOX = new Particle.DustOptions(Color.fromRGB(0xff, 0x7a, 0x3d), 1.0f);

    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final PeakItems items;

    ConnectionDisplay(BlockRegistry registry, NetworkRegistry networks, PeakItems items) {
        this.registry = registry;
        this.networks = networks;
        this.items = items;
    }

    private boolean holdsTuner(Player p) {
        return isTuner(p.getInventory().getItemInMainHand()) || isTuner(p.getInventory().getItemInOffHand());
    }

    private boolean isTuner(ItemStack stack) {
        return items.typeOf(stack).filter(t -> t == PeakItemType.PEAK_TUNER).isPresent();
    }

    /** Alle 10 Ticks. */
    void tick() {
        List<BlockRegistry.Key> jukeboxes = null;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!holdsTuner(p)) continue;
            if (jukeboxes == null) jukeboxes = registry.keysOf(PeakBlockType.JUKEBOX);
            draw(p, jukeboxes);
        }
    }

    private void draw(Player p, List<BlockRegistry.Key> jukeboxes) {
        World world = p.getWorld();
        Set<NetworkRegistry.Net> seen = new HashSet<>();
        int[] budget = {MAX_PARTICLES};
        for (BlockRegistry.Key j : jukeboxes) {
            if (!j.world().equals(world.getUID()) || distance(p, j) > RADIUS) continue;
            NetworkRegistry.Net net = networks.networkOf(j);
            if (net == null || !seen.add(net)) continue;
            List<BlockRegistry.Key> hubs = net.jukeboxes();
            if (hubs.isEmpty()) continue;
            for (var member : net.members.entrySet()) {
                BlockRegistry.Key target = member.getKey();
                if (distance(p, target) > RADIUS) continue;
                boolean isJukebox = member.getValue() == PeakBlockType.JUKEBOX;
                BlockRegistry.Key hub = isJukebox ? hubs.get(0) : nearest(target, hubs);
                if (hub.equals(target)) continue;
                line(p, hub, target, isJukebox ? JUKEBOX : SPEAKER, budget);
                if (budget[0] <= 0) return;
            }
        }
    }

    private static BlockRegistry.Key nearest(BlockRegistry.Key from, List<BlockRegistry.Key> hubs) {
        BlockRegistry.Key best = hubs.get(0);
        long bestD = Long.MAX_VALUE;
        for (BlockRegistry.Key h : hubs) {
            long dx = h.x() - from.x(), dy = h.y() - from.y(), dz = h.z() - from.z();
            long d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) { best = h; bestD = d; }
        }
        return best;
    }

    private static double distance(Player p, BlockRegistry.Key k) {
        double dx = p.getX() - (k.x() + 0.5), dy = p.getY() - (k.y() + 0.5), dz = p.getZ() - (k.z() + 0.5);
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** Linie von Blockmitte zu Blockmitte als Partikelreihe (nur fuer diesen Spieler sichtbar). */
    private static void line(Player p, BlockRegistry.Key from, BlockRegistry.Key to, Particle.DustOptions color, int[] budget) {
        double x0 = from.x() + 0.5, y0 = from.y() + 0.5, z0 = from.z() + 0.5;
        double dx = to.x() + 0.5 - x0, dy = to.y() + 0.5 - y0, dz = to.z() + 0.5 - z0;
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (length < 0.1 || length > 64) return;
        int steps = (int) Math.max(1, Math.min(budget[0], length / STEP));
        for (int i = 0; i <= steps && budget[0] > 0; i++) {
            double t = (double) i / steps;
            p.spawnParticle(Particle.DUST, x0 + dx * t, y0 + dy * t, z0 + dz * t, 1, 0, 0, 0, 0, color);
            budget[0]--;
        }
    }
}
