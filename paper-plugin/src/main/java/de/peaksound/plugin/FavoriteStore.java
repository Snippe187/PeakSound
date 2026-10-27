package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Favoriten je Spieler (Song-Hashes) in {@code favorites.json}. Geloeschte Songs fallen beim Lesen einfach heraus. */
final class FavoriteStore {
    private final PeakSoundPlugin plugin;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Set<String>> byPlayer = new LinkedHashMap<>();
    private boolean dirty;

    FavoriteStore(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("favorites.json");
    }

    synchronized void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Map<String, Set<String>> data = gson.fromJson(r, new TypeToken<Map<String, Set<String>>>() {}.getType());
            if (data != null) data.forEach((k, v) -> byPlayer.put(k, new HashSet<>(v)));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "favorites.json konnte nicht gelesen werden", e);
        }
    }

    synchronized boolean isFavorite(UUID player, String hash) {
        Set<String> set = byPlayer.get(player.toString());
        return set != null && set.contains(hash);
    }

    /** Schaltet den Favoriten um. @return der neue Zustand */
    synchronized boolean toggle(UUID player, String hash) {
        Set<String> set = byPlayer.computeIfAbsent(player.toString(), k -> new HashSet<>());
        boolean now = set.add(hash);
        if (!now) set.remove(hash);
        dirty = true;
        return now;
    }

    synchronized void forget(String hash) {
        for (Set<String> set : byPlayer.values()) if (set.remove(hash)) dirty = true;
    }

    synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling("favorites.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                gson.toJson(byPlayer, w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.WARNING, "favorites.json konnte nicht gespeichert werden", e);
        }
    }
}
