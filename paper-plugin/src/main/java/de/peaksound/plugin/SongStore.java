package de.peaksound.plugin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import de.peaksound.protocol.LimitLogic;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

/**
 * Song-Datenbank: Metadaten in {@code songs.json}, Audio in {@code songs/<hash>.<format>}, Cover in
 * {@code songs/<hash>.cover}. Alle Zugriffe sind synchronisiert; Datei-I/O laeuft nur in asynchronen Tasks.
 */
public final class SongStore {
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{64}");

    /** Ergebnis von {@link #add}. */
    public enum AddResult { ADDED, ALREADY_PRESENT, STORAGE_FULL, QUOTA_REACHED }

    private final PeakSoundPlugin plugin;
    private final Path dataDir;
    private final Path songsDir;
    private final Path indexFile;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Map<String, Song> songs = new LinkedHashMap<>();
    private long totalBytes;
    private boolean dirty;

    SongStore(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder().toPath();
        this.songsDir = dataDir.resolve("songs");
        this.indexFile = dataDir.resolve("songs.json");
    }

    /** Gueltiges Hash-Format (verhindert Pfad-Tricks wie "../" in Client-Anfragen). */
    public static boolean validHash(String hash) {
        return hash != null && HASH.matcher(hash).matches();
    }

    synchronized void load() {
        try {
            Files.createDirectories(songsDir);
            if (!Files.exists(indexFile)) return;
            try (Reader r = Files.newBufferedReader(indexFile)) {
                List<Song> list = gson.fromJson(r, new TypeToken<List<Song>>() {}.getType());
                if (list != null) {
                    for (Song s : list) {
                        if (validHash(s.hash()) && Files.exists(audioPath(s))) {
                            songs.put(s.hash(), s);
                            totalBytes += s.sizeBytes();
                        } else {
                            plugin.getLogger().warning("Song ohne Datei uebersprungen: " + s.hash());
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "songs.json konnte nicht gelesen werden", e);
        }
    }

    public synchronized Optional<Song> get(String hash) {
        return Optional.ofNullable(songs.get(hash));
    }

    public synchronized List<Song> all() {
        return new ArrayList<>(songs.values());
    }

    public synchronized long totalBytes() { return totalBytes; }

    public synchronized int countByUploader(UUID uploader) {
        int n = 0;
        for (Song s : songs.values()) if (s.uploader().equals(uploader)) n++;
        return n;
    }

    public Path audioPath(Song song) {
        return songsDir.resolve(song.hash() + "." + song.format());
    }

    public Path coverPath(String hash) {
        return songsDir.resolve(hash + ".cover");
    }

    /**
     * Legt einen Song an, wenn Quota und Speicher-Limit es erlauben. Gleiche Dateien (gleicher Hash)
     * werden nur einmal gespeichert und kosten weder Quota noch Speicher.
     */
    public synchronized AddResult add(Song song, byte[] audio, byte[] cover, int maxSongsPerPlayer, long maxBytes)
            throws IOException {
        if (songs.containsKey(song.hash())) return AddResult.ALREADY_PRESENT;
        if (!LimitLogic.mayUpload(countByUploader(song.uploader()), maxSongsPerPlayer)) return AddResult.QUOTA_REACHED;
        if (!LimitLogic.fitsStorage(totalBytes, audio.length, maxBytes)) return AddResult.STORAGE_FULL;
        Files.createDirectories(songsDir);
        Files.write(audioPath(song), audio);
        if (cover.length > 0) Files.write(coverPath(song.hash()), cover);
        songs.put(song.hash(), song);
        totalBytes += audio.length;
        dirty = true;
        return AddResult.ADDED;
    }

    /** Anzahl aller Songs. */
    public synchronized int count() { return songs.size(); }

    /** Setzt den Status (z. B. nach der Freigabe). @return der aktualisierte Song, oder leer, wenn es ihn nicht gibt */
    public synchronized Optional<Song> setStatus(String hash, Song.Status status) {
        Song old = songs.get(hash);
        if (old == null) return Optional.empty();
        Song updated = old.withStatus(status);
        songs.put(hash, updated);
        dirty = true;
        return Optional.of(updated);
    }

    /** Benennt einen Song um. @return der aktualisierte Song, oder leer, wenn es ihn nicht gibt */
    public synchronized Optional<Song> rename(String hash, String title) {
        Song old = songs.get(hash);
        if (old == null) return Optional.empty();
        Song updated = old.withTitle(title);
        songs.put(hash, updated);
        dirty = true;
        return Optional.of(updated);
    }

    /** Zaehlt eine Wiedergabe bzw. ein Beschreiben einer Platte. */
    public synchronized void countPlay(String hash) {
        Song old = songs.get(hash);
        if (old == null) return;
        songs.put(hash, old.withPlays(old.plays() + 1));
        dirty = true;
    }

    /** Loescht einen Song samt Dateien (Audio, Cover, Analyse). @return der geloeschte Song, oder leer */
    public synchronized Optional<Song> remove(String hash) {
        Song song = songs.remove(hash);
        if (song == null) return Optional.empty();
        totalBytes = Math.max(0, totalBytes - song.sizeBytes());
        dirty = true;
        for (Path p : new Path[] {audioPath(song), coverPath(hash), songsDir.resolve(hash + ".analysis")}) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Datei konnte nicht geloescht werden: " + p, e);
            }
        }
        return Optional.of(song);
    }

    /** Speichert den Index, falls er sich geaendert hat (aus einem asynchronen Task aufrufen). */
    synchronized void saveIfDirty() {
        if (!dirty) return;
        dirty = false;
        try {
            Files.createDirectories(dataDir);
            Path tmp = indexFile.resolveSibling("songs.json.tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                gson.toJson(new ArrayList<>(songs.values()), w);
            }
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            dirty = true;
            plugin.getLogger().log(Level.SEVERE, "songs.json konnte nicht gespeichert werden", e);
        }
    }
}
