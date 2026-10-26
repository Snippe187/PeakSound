package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.peaksound.protocol.SpeakerChannel;
import de.peaksound.protocol.SpeakerSettings;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;

/** Einstellungen und Nummern aller Lautsprecher, gespeichert in {@code speakers.json}. */
final class SpeakerStore {
    /** Ein Lautsprecher: seine Einstellungen und die Nummer in seinem System (0 = nicht verbunden). */
    record Data(SpeakerSettings settings, int number) {}

    private final PeakSoundPlugin plugin;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<BlockRegistry.Key, Data> speakers = new ConcurrentHashMap<>();
    private volatile boolean dirty;

    SpeakerStore(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("speakers.json");
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
            Map<String, Data> raw = gson.fromJson(r, new TypeToken<Map<String, Data>>() {}.getType());
            if (raw == null) return;
            raw.forEach((k, v) -> {
                try {
                    int number = Math.max(0, Math.min(de.peaksound.protocol.SpeakerNumbering.MAX_NUMBER, v.number()));
                    speakers.put(parse(k), new Data(v.settings().sanitized(), number));
                } catch (RuntimeException e) {
                    plugin.getLogger().warning("Ungueltiger Lautsprecher-Eintrag uebersprungen: " + k);
                }
            });
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "speakers.json konnte nicht gelesen werden", e);
        }
    }

    void startAutosave() {
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            if (dirty) saveNow();
        }, 20, 20, java.util.concurrent.TimeUnit.SECONDS);
    }

    Optional<Data> get(BlockRegistry.Key key) {
        return Optional.ofNullable(speakers.get(key));
    }

    /** Legt den Eintrag an, falls er fehlt (mit dem vorgeschlagenen Kanal). */
    Data getOrCreate(BlockRegistry.Key key, SpeakerChannel suggestedChannel) {
        return speakers.computeIfAbsent(key, k -> {
            dirty = true;
            return new Data(SpeakerSettings.defaults(suggestedChannel), 0);
        });
    }

    void put(BlockRegistry.Key key, Data data) {
        speakers.put(key, data);
        dirty = true;
    }

    void remove(Block block) {
        if (speakers.remove(BlockRegistry.Key.of(block)) != null) dirty = true;
    }

    synchronized void saveNow() {
        dirty = false;
        Map<String, Data> raw = new LinkedHashMap<>();
        speakers.forEach((k, v) -> raw.put(keyString(k), v));
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(raw));
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "speakers.json konnte nicht gespeichert werden", e);
        }
    }
}
