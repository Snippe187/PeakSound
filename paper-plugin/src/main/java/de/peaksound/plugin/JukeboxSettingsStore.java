package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Limits;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

/** Einstellungen aller Jukeboxen (Name, Reichweite, Lautstaerke, Partikel, ...), gespeichert in {@code jukebox-settings.json}. */
final class JukeboxSettingsStore {
    private final PeakSoundPlugin plugin;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<BlockRegistry.Key, JukeboxSettings> settings = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    JukeboxSettingsStore(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("jukebox-settings.json");
    }

    private static String keyString(BlockRegistry.Key k) {
        return k.world() + ";" + k.x() + ";" + k.y() + ";" + k.z();
    }

    private static BlockRegistry.Key parse(String s) {
        String[] p = s.split(";");
        return new BlockRegistry.Key(UUID.fromString(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
    }

    void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Map<String, JukeboxSettings> raw = gson.fromJson(r, new TypeToken<Map<String, JukeboxSettings>>() {}.getType());
            if (raw == null) return;
            raw.forEach((k, v) -> {
                try {
                    settings.put(parse(k), v.sanitized());
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Ungueltige Jukebox-Einstellung uebersprungen: " + k);
                }
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "jukebox-settings.json konnte nicht gelesen werden", e);
        }
    }

    void startAutosave() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            if (dirty) saveNow();
        }, 20, 20, java.util.concurrent.TimeUnit.SECONDS);
    }

    /** Standardwerte aus der Config, wenn fuer diese Jukebox noch nichts gespeichert ist. */
    JukeboxSettings get(BlockRegistry.Key key) {
        JukeboxSettings s = settings.get(key);
        return s != null ? s : defaults();
    }

    JukeboxSettings get(Block block) {
        return get(BlockRegistry.Key.of(block));
    }

    JukeboxSettings defaults() {
        return JukeboxSettings.defaults(
                Math.max(Limits.MIN_RANGE, Math.min(Limits.MAX_RANGE, plugin.getConfig().getInt("playback.default-range", 16))),
                Math.max(0, Math.min(100, plugin.getConfig().getInt("playback.default-volume", 100))));
    }

    void set(Block block, JukeboxSettings value) {
        settings.put(BlockRegistry.Key.of(block), value.sanitized());
        dirty = true;
    }

    void remove(Block block) {
        if (settings.remove(BlockRegistry.Key.of(block)) != null) dirty = true;
    }

    synchronized void saveNow() {
        dirty = false;
        Map<String, JukeboxSettings> raw = new LinkedHashMap<>();
        settings.forEach((k, v) -> raw.put(keyString(k), v));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(raw));
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "jukebox-settings.json konnte nicht gespeichert werden", e);
        }
    }
}
