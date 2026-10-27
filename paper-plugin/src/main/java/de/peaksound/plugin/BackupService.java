package de.peaksound.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Stream;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Sicherungen: ein Zip aller Plugin-Daten (Songs, Bloecke, Einstellungen) in {@code backups/}. Taeglich automatisch (mit
 * Rotation) und per Befehl. Wiederherstellen laeuft in zwei Schritten: Der Befehl legt das Zip nur bereit; beim naechsten
 * Serverstart wird es VOR dem Laden eingespielt (vorher entsteht eine Sicherung des Ist-Zustands), damit laufende
 * Daten im Speicher nichts zurueckueberschreiben.
 */
final class BackupService {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PENDING = "restore-pending.zip";

    private final PeakSoundPlugin plugin;
    private final Path dataDir;
    private final Path backupDir;
    private final AtomicBoolean running = new AtomicBoolean();

    BackupService(PeakSoundPlugin plugin) {
        this.plugin = plugin;
        this.dataDir = plugin.getDataFolder().toPath();
        this.backupDir = dataDir.resolve("backups");
    }

    // ---- Sichern ----

    /** Erstellt eine Sicherung (blockierend, in einem Async-Task aufrufen). @return Name der Zip-Datei */
    String backupNow(String prefix) throws IOException {
        if (!running.compareAndSet(false, true)) throw new IOException("Es laeuft schon eine Sicherung.");
        try {
            Files.createDirectories(backupDir);
            String name = prefix + "-" + LocalDateTime.now().format(STAMP) + ".zip";
            Path tmp = backupDir.resolve(name + ".tmp");
            boolean songs = plugin.getConfig().getBoolean("backup.include-songs", true);
            try (OutputStream out = Files.newOutputStream(tmp); ZipOutputStream zip = new ZipOutputStream(out)) {
                zip.setLevel(Deflater.BEST_SPEED);
                try (Stream<Path> files = Files.walk(dataDir)) {
                    for (Path file : (Iterable<Path>) files::iterator) {
                        if (!Files.isRegularFile(file)) continue;
                        Path rel = dataDir.relativize(file);
                        String first = rel.getName(0).toString();
                        if (first.equals("backups") || first.equals(PENDING)) continue;
                        if (!songs && first.equals("songs")) continue;
                        zip.putNextEntry(new ZipEntry(rel.toString().replace('\\', '/')));
                        Files.copy(file, zip);
                        zip.closeEntry();
                    }
                }
            }
            Files.move(tmp, backupDir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            rotate();
            return name;
        } finally {
            running.set(false);
        }
    }

    /** Alte automatische Sicherungen loeschen (config backup.keep, Standard 7). Manuelle und "vor-Wiederherstellung"-Sicherungen bleiben. */
    private void rotate() throws IOException {
        int keep = Math.max(1, plugin.getConfig().getInt("backup.keep", 7));
        List<Path> auto = new ArrayList<>();
        try (DirectoryStream<Path> s = Files.newDirectoryStream(backupDir, "auto-*.zip")) {
            s.forEach(auto::add);
        }
        auto.sort(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed());
        for (int i = keep; i < auto.size(); i++) Files.deleteIfExists(auto.get(i));
    }

    /** Vorhandene Sicherungen, neueste zuerst. */
    List<String> list() {
        List<String> out = new ArrayList<>();
        if (!Files.isDirectory(backupDir)) return out;
        try (DirectoryStream<Path> s = Files.newDirectoryStream(backupDir, "*.zip")) {
            for (Path p : s) out.add(p.getFileName().toString());
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Sicherungen konnten nicht gelesen werden", e);
        }
        out.sort(Comparator.reverseOrder());
        return out;
    }

    long sizeOf(String name) {
        try { return Files.size(backupDir.resolve(name)); } catch (IOException e) { return -1; }
    }

    /** Der Name ist eine bekannte Sicherung (verhindert "../" in Befehlen). */
    boolean exists(String name) {
        return list().contains(name);
    }

    /** Jede Stunde pruefen: Ist die letzte automatische Sicherung aelter als 24 h, eine neue anlegen. */
    void scheduleDaily() {
        org.bukkit.Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> {
            if (!plugin.getConfig().getBoolean("backup.daily", true)) return;
            try {
                long newest = 0;
                for (String n : list()) {
                    if (n.startsWith("auto-")) {
                        newest = Math.max(newest, Files.getLastModifiedTime(backupDir.resolve(n)).toMillis());
                    }
                }
                if (System.currentTimeMillis() - newest >= 24L * 3600 * 1000) {
                    String name = backupNow("auto");
                    plugin.getLogger().info("Automatische Sicherung erstellt: " + name);
                }
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().log(Level.WARNING, "Automatische Sicherung fehlgeschlagen", e);
            }
        }, 2, 60, java.util.concurrent.TimeUnit.MINUTES);
    }

    // ---- Wiederherstellen (greift beim naechsten Start) ----

    /** Legt die Sicherung zum Einspielen beim naechsten Serverstart bereit. */
    void stageRestore(String name) throws IOException {
        if (!exists(name)) throw new IOException("Unbekannte Sicherung: " + name);
        Files.copy(backupDir.resolve(name), dataDir.resolve(PENDING), StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * Wird ganz zu Beginn von onEnable aufgerufen, BEVOR die Daten geladen werden: spielt eine bereitgestellte Sicherung ein.
     * Vorher entsteht "vor-restore"-Sicherung des aktuellen Zustands. Fehlerhafte Zips lassen den Ist-Zustand unberuehrt.
     */
    void applyPendingRestore() {
        Path pending = dataDir.resolve(PENDING);
        if (!Files.exists(pending)) return;
        try {
            backupNow("vor-restore");
            int files = 0;
            Path root = dataDir.toAbsolutePath().normalize();
            try (InputStream in = Files.newInputStream(pending); ZipInputStream zip = new ZipInputStream(in)) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    Path target = root.resolve(entry.getName()).normalize();
                    if (!target.startsWith(root) || target.startsWith(root.resolve("backups"))) continue; // nie ausserhalb schreiben
                    Files.createDirectories(target.getParent());
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
                    files++;
                }
            }
            plugin.getLogger().info("Sicherung eingespielt (" + files + " Dateien). Der vorherige Stand liegt in backups/vor-restore-*.zip.");
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Wiederherstellung fehlgeschlagen. Der vorherige Stand liegt in backups/vor-restore-*.zip", e);
        } finally {
            try { Files.deleteIfExists(pending); } catch (IOException ignored) { /* naechster Start versucht es nicht erneut */ }
        }
    }
}