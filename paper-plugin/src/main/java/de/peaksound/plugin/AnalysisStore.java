package de.peaksound.plugin;

import de.peaksound.audio.Analysis;
import de.peaksound.audio.AudioException;
import de.peaksound.audio.Analyzer;
import de.peaksound.audio.PcmSource;
import de.peaksound.protocol.AudioFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import org.bukkit.Bukkit;

/**
 * Speichert die Musikanalyse (Takt und Frequenzbaender) neben den Songs. Die Analyse entsteht beim Upload;
 * fehlt sie (aeltere Songs), wird sie einmal asynchron nachberechnet. Im Tick-Loop wird nur aus dem Speicher gelesen.
 */
final class AnalysisStore {
    private final PeakSoundPlugin plugin;
    private final SongStore songs;
    private final Map<String, Analysis> cache = new ConcurrentHashMap<>();
    private final Set<String> computing = ConcurrentHashMap.newKeySet();

    AnalysisStore(PeakSoundPlugin plugin, SongStore songs) {
        this.plugin = plugin;
        this.songs = songs;
    }

    private Path path(String hash) {
        return songs.audioPath(songs.get(hash).orElseThrow()).resolveSibling(hash + ".analysis");
    }

    /** Gibt die Analyse zurueck, wenn sie im Speicher liegt. Stoesst sonst (asynchron) Laden oder Berechnen an. */
    Optional<Analysis> peek(Song song) {
        Analysis a = cache.get(song.hash());
        if (a != null) return Optional.of(a);
        if (computing.add(song.hash())) {
            Bukkit.getAsyncScheduler().runNow(plugin, t -> {
                try {
                    loadOrCompute(song);
                } finally {
                    computing.remove(song.hash());
                }
            });
        }
        return Optional.empty();
    }

    /** Blockierend, nur fuer asynchrone Threads (Upload-Verarbeitung). */
    void store(Song song, byte[] audio, AudioFormat format) {
        try (PcmSource source = PcmSource.open(audio, format)) {
            Analysis analysis = Analyzer.analyze(source);
            Files.write(path(song.hash()), analysis.toBytes());
            cache.put(song.hash(), analysis);
        } catch (AudioException | IOException e) {
            plugin.getLogger().log(Level.WARNING, "Analyse fehlgeschlagen fuer " + song.hash(), e);
        }
    }

    private void loadOrCompute(Song song) {
        Path file = path(song.hash());
        try {
            if (Files.exists(file)) {
                cache.put(song.hash(), Analysis.fromBytes(Files.readAllBytes(file)));
                return;
            }
            AudioFormat format = AudioFormat.detect(Files.readAllBytes(songs.audioPath(song))).orElse(null);
            if (format != null) store(song, Files.readAllBytes(songs.audioPath(song)), format);
        } catch (AudioException | IOException e) {
            plugin.getLogger().log(Level.WARNING, "Analyse konnte nicht geladen werden: " + song.hash(), e);
        }
    }
}
