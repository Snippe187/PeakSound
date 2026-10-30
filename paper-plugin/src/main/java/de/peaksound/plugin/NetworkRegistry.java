package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.protocol.DustNetwork;
import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

/**
 * Redstone-Netze der PeakSound-Bloecke. Die Suche ({@link DustNetwork}) laeuft nur, wenn ein Netz gebraucht wird
 * und kein gueltiger Cache-Eintrag existiert. Der Cache wird nur bei Blockaenderungen in der Naehe eines Netzes
 * verworfen ({@link #invalidateAt}); zusaetzlich prueft eine guenstige Stichprobe alle paar Sekunden, ob die
 * Staub- und Geraete-Positionen noch stimmen (faengt z. B. von Wasser weggespuelten Staub).
 */
final class NetworkRegistry {
    /** Ein berechnetes Netz. Positionen sind Welt-Koordinaten in {@code world}. */
    static final class Net {
        final UUID world;
        final Map<BlockRegistry.Key, PeakBlockType> members = new HashMap<>();
        final Set<BlockRegistry.Key> dust = new HashSet<>();
        final boolean truncated;
        final int minX, minY, minZ, maxX, maxY, maxZ;
        long validatedAt;

        Net(UUID world, DustNetwork.Result r) {
            this.world = world;
            this.truncated = r.truncated();
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE;
            int x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
            List<DustNetwork.Pos> all = new ArrayList<>(r.dust());
            all.addAll(r.devices().keySet());
            for (DustNetwork.Pos p : all) {
                x0 = Math.min(x0, p.x()); y0 = Math.min(y0, p.y()); z0 = Math.min(z0, p.z());
                x1 = Math.max(x1, p.x()); y1 = Math.max(y1, p.y()); z1 = Math.max(z1, p.z());
            }
            minX = x0; minY = y0; minZ = z0; maxX = x1; maxY = y1; maxZ = z1;
            r.devices().forEach((p, t) -> members.put(new BlockRegistry.Key(world, p.x(), p.y(), p.z()), t));
            r.dust().forEach(p -> dust.add(new BlockRegistry.Key(world, p.x(), p.y(), p.z())));
            validatedAt = System.currentTimeMillis();
        }

        /** Alle Jukeboxen dieses Netzes. */
        List<BlockRegistry.Key> jukeboxes() {
            List<BlockRegistry.Key> out = new ArrayList<>();
            members.forEach((k, t) -> { if (t == PeakBlockType.JUKEBOX) out.add(k); });
            return out;
        }

        /** Liegt die Position im Netz oder direkt daneben (eine Aenderung dort kann das Netz veraendern)? */
        boolean near(UUID w, int x, int y, int z) {
            return world.equals(w) && x >= minX - 1 && x <= maxX + 1 && y >= minY - 2 && y <= maxY + 2
                    && z >= minZ - 1 && z <= maxZ + 1;
        }
    }

    private static final long VALIDATE_EVERY_MS = 10_000;

    private final JavaPlugin plugin;
    private final BlockRegistry registry;
    private final Map<BlockRegistry.Key, Net> byMember = new HashMap<>();
    private final Set<Net> all = new HashSet<>();

    NetworkRegistry(JavaPlugin plugin, BlockRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    /** Das Netz, zu dem dieser PeakSound-Block gehoert (bei Bedarf berechnet), oder null, wenn der Block nicht geladen ist. */
    Net networkOf(BlockRegistry.Key key) {
        World world = Bukkit.getWorld(key.world());
        if (world == null || !world.isChunkLoaded(key.x() >> 4, key.z() >> 4)) return null;
        Net cached = byMember.get(key);
        if (cached != null) {
            if (System.currentTimeMillis() - cached.validatedAt < VALIDATE_EVERY_MS || stillValid(world, cached)) {
                if (System.currentTimeMillis() - cached.validatedAt >= VALIDATE_EVERY_MS) cached.validatedAt = System.currentTimeMillis();
                return cached;
            }
            drop(cached);
        }
        DustNetwork.Result result = DustNetwork.find(new WorldGrid(world), new DustNetwork.Pos(key.x(), key.y(), key.z()),
                Math.max(8, Ctx.config().getInt("network.max-dust", 256)),
                Math.max(1, Ctx.config().getInt("network.max-devices", 32)));
        if (result.devices().isEmpty()) return null; // Block ist (nicht mehr) ein PeakSound-Block
        Net net = new Net(key.world(), result);
        if (net.truncated) {
            Ctx.logger().warning("Redstone-Netz bei " + key.x() + " " + key.y() + " " + key.z()
                    + " ist groesser als das Limit (network.max-dust / max-devices) und wird abgeschnitten.");
        }
        all.add(net);
        net.members.keySet().forEach(k -> byMember.put(k, net));
        return net;
    }

    /**
     * Die Netze, die ueber Staub direkt am DJ-Pult haengen (hoechstens zwei werden vom Pult genutzt). Nicht gecacht:
     * Der Aufrufer merkt sich das Ergebnis kurz.
     */
    List<Net> netsAround(Block desk) {
        World world = desk.getWorld();
        WorldGrid grid = new WorldGrid(world);
        int maxDust = Math.max(8, Ctx.config().getInt("network.max-dust", 256));
        int maxDevices = Math.max(1, Ctx.config().getInt("network.max-devices", 32));
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        List<DustNetwork.Pos> starts = new ArrayList<>();
        starts.add(new DustNetwork.Pos(desk.getX(), desk.getY() + 1, desk.getZ()));
        for (int[] s : sides) {
            for (int dy = -1; dy <= 1; dy++) starts.add(new DustNetwork.Pos(desk.getX() + s[0], desk.getY() + dy, desk.getZ() + s[1]));
        }
        List<Net> out = new ArrayList<>();
        for (DustNetwork.Pos start : starts) {
            if (!grid.isDust(start.x(), start.y(), start.z())) continue;
            if (out.stream().anyMatch(n -> n.dust.contains(new BlockRegistry.Key(world.getUID(), start.x(), start.y(), start.z())))) continue;
            Net net = new Net(world.getUID(), DustNetwork.findFromDust(grid, start, maxDust, maxDevices));
            if (!net.jukeboxes().isEmpty()) out.add(net);
        }
        return out;
    }

    /** Ein Block wurde gesetzt, entfernt oder bewegt: betroffene Netze verwerfen. */    void invalidateAt(World world, int x, int y, int z) {
        if (all.isEmpty()) return;
        UUID id = world.getUID();
        List<Net> hit = null;
        for (Net n : all) {
            if (n.near(id, x, y, z)) {
                if (hit == null) hit = new ArrayList<>();
                hit.add(n);
            }
        }
        if (hit != null) hit.forEach(this::drop);
    }

    void invalidateAll() {
        all.clear();
        byMember.clear();
    }

    private void drop(Net net) {
        all.remove(net);
        net.members.keySet().forEach(k -> byMember.remove(k, net));
    }

    /** Stichprobe: Liegen Staub und Geraete noch dort, wo wir sie vermuten? */
    private boolean stillValid(World world, Net net) {
        for (BlockRegistry.Key k : net.dust) {
            if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;
            if (world.getBlockAt(k.x(), k.y(), k.z()).getType() != Material.REDSTONE_WIRE) return false;
        }
        for (Map.Entry<BlockRegistry.Key, PeakBlockType> e : net.members.entrySet()) {
            BlockRegistry.Key k = e.getKey();
            if (!world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;
            if (registry.get(world.getBlockAt(k.x(), k.y(), k.z())).orElse(null) != e.getValue()) return false;
        }
        return true;
    }

    /** Verbindet {@link DustNetwork} mit der Bukkit-Welt. Nicht geladene Chunks gelten als leer. */
    private final class WorldGrid implements DustNetwork.Grid {
        private final World world;

        WorldGrid(World world) { this.world = world; }

        private Block at(int x, int y, int z) {
            return world.isChunkLoaded(x >> 4, z >> 4) ? world.getBlockAt(x, y, z) : null;
        }

        @Override
        public boolean isDust(int x, int y, int z) {
            Block b = at(x, y, z);
            return b != null && b.getType() == Material.REDSTONE_WIRE;
        }

        @Override
        public boolean isSolid(int x, int y, int z) {
            Block b = at(x, y, z);
            return b != null && b.getType().isOccluding();
        }

        @Override
        public PeakBlockType device(int x, int y, int z) {
            Block b = at(x, y, z);
            return b == null || b.getType() != Material.NOTE_BLOCK ? null : registry.get(b).orElse(null);
        }
    }
}
