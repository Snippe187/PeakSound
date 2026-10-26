package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import de.peaksound.protocol.LimitLogic;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Upload-Limits, die Operatoren im Spiel aendern koennen: Songlimit pro Spieler (mit Standardwert), serverweites
 * Speicher-Limit. Startwerte stammen aus der config.yml; was ein Operator einstellt, steht in {@code limits.json}
 * und hat Vorrang. {@code 0} heisst "unbegrenzt".
 */
final class AdminLimits {
    /** Gespeicherte Overrides; null-Felder bedeuten "Wert aus der config.yml". */
    private static final class Data {
        Integer defaultMaxSongs;
        Long maxStorageMb;
        Map<String, Integer> perPlayer = new HashMap<>();
    }

    private final PeakSoundPlugin plugin;
    private final Path file;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private Data data = new Data();

    AdminLimits(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("limits.json");
    }

    synchronized void load() {
        if (!Files.exists(file)) return;
        try (Reader r = Files.newBufferedReader(file)) {
            Data loaded = gson.fromJson(r, Data.class);
            if (loaded != null) {
                if (loaded.perPlayer == null) loaded.perPlayer = new HashMap<>();
                data = loaded;
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "limits.json konnte nicht gelesen werden", e);
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(data));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "limits.json konnte nicht gespeichert werden", e);
        }
    }

    /** Standard-Songlimit pro Spieler (0 = unbegrenzt). */
    synchronized int defaultMaxSongs() {
        return LimitLogic.clampSongLimit(data.defaultMaxSongs != null ? data.defaultMaxSongs
                : plugin.getConfig().getInt("upload.max-songs-per-player", 10));
    }

    /** Das fuer diesen Spieler geltende Songlimit (0 = unbegrenzt). */
    synchronized int maxSongsFor(UUID player) {
        return LimitLogic.effectiveSongLimit(data.perPlayer.get(player.toString()), defaultMaxSongs());
    }

    /** Eigener Wert des Spielers, oder {@link LimitLogic#USE_DEFAULT}. */
    synchronized int overrideFor(UUID player) {
        Integer v = data.perPlayer.get(player.toString());
        return v == null ? LimitLogic.USE_DEFAULT : v;
    }

    /** Serverweites Speicher-Limit in Bytes (0 = unbegrenzt). */
    synchronized long maxStorageBytes() {
        long mb = data.maxStorageMb != null ? data.maxStorageMb : plugin.getConfig().getLong("upload.max-storage-mb", 2048);
        return LimitLogic.mbToBytes(mb);
    }

    synchronized void setDefaultMaxSongs(long value) {
        data.defaultMaxSongs = LimitLogic.clampSongLimit(value);
        save();
    }

    synchronized void setPlayerLimit(UUID player, long value) {
        if (value == LimitLogic.USE_DEFAULT) data.perPlayer.remove(player.toString());
        else data.perPlayer.put(player.toString(), LimitLogic.clampSongLimit(value));
        save();
    }

    synchronized void setStorageMb(long value) {
        data.maxStorageMb = LimitLogic.clampStorageMb(value);
        save();
    }

    /** Spieler mit eigenem Limit (fuer die Anzeige, auch wenn sie noch nichts hochgeladen haben). */
    synchronized Map<String, Integer> overrides() {
        return new HashMap<>(data.perPlayer);
    }
}
