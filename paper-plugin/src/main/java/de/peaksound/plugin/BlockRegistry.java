package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.protocol.PeakBlockType;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Merkt sich, welche Vanilla-Bloecke in der Welt PeakSound-Bloecke sind.
 * Wird in {@code blocks.yml} gespeichert; Schreiben passiert asynchron.
 */
public final class BlockRegistry {
    public record Key(UUID world, int x, int y, int z) {
        static Key of(Block b) { return new Key(b.getWorld().getUID(), b.getX(), b.getY(), b.getZ()); }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<Key, PeakBlockType> blocks = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    BlockRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(Ctx.dataFolder(), "blocks.yml");
    }

    void load() {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String line : yaml.getStringList("blocks")) {
            String[] p = line.split(";");
            if (p.length != 5) continue;
            try {
                PeakBlockType.byId(p[4]).ifPresent(type ->
                        blocks.put(new Key(UUID.fromString(p[0]), Integer.parseInt(p[1]),
                                Integer.parseInt(p[2]), Integer.parseInt(p[3])), type));
            } catch (IllegalArgumentException e) {
                Ctx.logger().warning("Ungueltiger Eintrag in blocks.yml uebersprungen: " + line);
            }
        }
    }

    /** Startet das periodische asynchrone Speichern (nur wenn sich etwas geaendert hat). */
    void startAutosave() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            if (dirty) saveNow();
        }, 30, 30, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Positionen aller registrierten Bloecke eines Typs. */
    List<Key> keysOf(PeakBlockType type) {
        List<Key> out = new ArrayList<>();
        blocks.forEach((k, t) -> { if (t == type) out.add(k); });
        return out;
    }

    void register(Block block, PeakBlockType type) {
        blocks.put(Key.of(block), type);
        dirty = true;
    }

    void remove(Block block) {
        if (blocks.remove(Key.of(block)) != null) dirty = true;
    }

    /**
     * @return der PeakSound-Typ des Blocks. Stimmt das Material nicht mehr (z. B. durch WorldEdit),
     *         wird der veraltete Eintrag entfernt und leer zurueckgegeben.
     */
    Optional<PeakBlockType> get(Block block) {
        PeakBlockType type = blocks.get(Key.of(block));
        if (type == null) return Optional.empty();
        if (block.getType() != Material.NOTE_BLOCK) {
            remove(block);
            return Optional.empty();
        }
        return Optional.of(type);
    }

    /**
     * Prueft alle Eintraege in geladenen Chunks: Ist dort kein Notenblock mehr (WorldEdit, /setblock, Explosionen anderer
     * Plugins), wird der Eintrag entfernt. Nur auf dem Hauptthread aufrufen.
     * @return die entfernten Bloecke (damit Einstellungen und Wiedergabe aufgeraeumt werden koennen)
     */
    Map<Block, PeakBlockType> purgeStale() {
        Map<Block, PeakBlockType> gone = new java.util.LinkedHashMap<>();
        for (Map.Entry<Key, PeakBlockType> e : blocks.entrySet()) {
            Key k = e.getKey();
            var world = Bukkit.getWorld(k.world());
            if (world == null || !world.isChunkLoaded(k.x() >> 4, k.z() >> 4)) continue;
            Block block = world.getBlockAt(k.x(), k.y(), k.z());
            if (block.getType() != Material.NOTE_BLOCK) gone.put(block, e.getValue());
        }
        for (Block b : gone.keySet()) remove(b);
        return gone;
    }

    synchronized void saveNow() {
        dirty = false;
        List<String> lines = new ArrayList<>(blocks.size());
        blocks.forEach((k, v) -> lines.add(k.world() + ";" + k.x() + ";" + k.y() + ";" + k.z() + ";" + v.id()));
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("blocks", lines);
        try {
            Ctx.dataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            dirty = true;
            Ctx.logger().log(Level.SEVERE, "blocks.yml konnte nicht gespeichert werden", e);
        }
    }
}
