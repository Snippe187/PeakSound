package de.peaksound.plugin;

import de.peaksound.protocol.PeakBlockType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Prueft Anfragen eines Clients an einen PeakSound-Block (Menues, Aktionen): Rate-Limit, geladener Chunk,
 * richtiger Blocktyp, Spieler in Reichweite. Der Server vertraut Koordinaten aus Paketen nie.
 */
final class EditAccess {
    /** Wie weit der Spieler hoechstens vom Block entfernt sein darf, um ihn zu bearbeiten. */
    static final double MAX_DISTANCE = 8.0;
    /** Mindestabstand zwischen zwei Anfragen eines Spielers (Schutz vor Paketfluten). */
    private static final long MIN_INTERVAL_MS = 50;

    private final BlockRegistry registry;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    EditAccess(BlockRegistry registry) {
        this.registry = registry;
    }

    /** Nur das Rate-Limit (fuer Anfragen ohne Block, z. B. Bibliothek ueber ein Upload-Ticket). */
    boolean rateOk(Player player) {
        long now = System.currentTimeMillis();
        Long last = lastRequest.put(player.getUniqueId(), now);
        return last == null || now - last >= MIN_INTERVAL_MS;
    }

    /** @return der Block, oder null, wenn die Anfrage abgelehnt wird */
    Block validated(Player player, int x, int y, int z, PeakBlockType expected) {
        long now = System.currentTimeMillis();
        Long last = lastRequest.put(player.getUniqueId(), now);
        if (last != null && now - last < MIN_INTERVAL_MS) return null;

        World world = player.getWorld();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block block = world.getBlockAt(x, y, z);
        if (registry.get(block).filter(t -> t == expected).isEmpty()) return null;
        double dx = player.getX() - (block.getX() + 0.5);
        double dy = player.getY() - block.getY();
        double dz = player.getZ() - (block.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE * MAX_DISTANCE + 4) return null;
        return block;
    }

    void onQuit(Player player) {
        lastRequest.remove(player.getUniqueId());
    }
}
