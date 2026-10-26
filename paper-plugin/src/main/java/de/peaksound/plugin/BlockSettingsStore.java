package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

/** Einstellungen je PeakSound-Block (Scheinwerfer, DJ-Pult), gespeichert in einer JSON-Datei. */
final class BlockSettingsStore<T> {
    private final PeakSoundPlugin plugin;
    private final Path file;
    private final String fileName;
    private final Type mapType;
    private final T defaults;
    private final UnaryOperator<T> sanitizer;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<BlockRegistry.Key, T> values = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    BlockSettingsStore(PeakSoundPlugin plugin, String fileName, Class<T> type, T defaults, UnaryOperator<T> sanitizer) {
        this.plugin = plugin;
        this.fileName = fileName;
        this.file = plugin.getDataFolder().toPath().resolve(fileName);
        this.mapType = TypeToken.getParameterized(Map.class, String.class, type).getType();
        this.defaults = defaults;
        this.sanitizer = sanitizer;
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
            Map<String, T> raw = gson.fromJson(r, mapType);
            if (raw == null) return;
            raw.forEach((k, v) -> {
                try {
                    values.put(parse(k), sanitizer.apply(v));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Ungueltiger Eintrag in " + fileName + " uebersprungen: " + k);
                }
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, fileName + " konnte nicht gelesen werden", e);
        }
    }

    void startAutosave() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            if (dirty) saveNow();
        }, 20, 20, java.util.concurrent.TimeUnit.SECONDS);
    }

    T get(BlockRegistry.Key key) {
        return values.getOrDefault(key, defaults);
    }

    void set(BlockRegistry.Key key, T value) {
        values.put(key, sanitizer.apply(value));
        dirty = true;
    }

    void remove(Block block) {
        if (values.remove(BlockRegistry.Key.of(block)) != null) dirty = true;
    }

    synchronized void saveNow() {
        dirty = false;
        Map<String, T> raw = new LinkedHashMap<>();
        values.forEach((k, v) -> raw.put(keyString(k), v));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(raw));
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, fileName + " konnte nicht gespeichert werden", e);
        }
    }
}