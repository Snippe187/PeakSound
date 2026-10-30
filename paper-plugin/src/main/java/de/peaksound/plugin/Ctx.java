package de.peaksound.plugin;

import de.peakverse.core.api.module.ModuleContext;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Zugriff der PeakSound-Klassen auf Datenordner, Config und Logger des Moduls. PeakSound ist kein eigenes Bukkit-Plugin mehr
 * (es laeuft im Jar von PeakCore), deshalb kommen diese Dinge aus dem {@link ModuleContext} und nicht aus dem Plugin.
 * Das Bukkit-Plugin PeakCore selbst wird weiter fuer Scheduler und Events uebergeben.
 */
final class Ctx {
    private static volatile ModuleContext context;
    private static volatile SongStore songs;

    private Ctx() {}

    static void init(ModuleContext c) {
        context = c;
    }

    static void clear() {
        context = null;
        songs = null;
    }

    private static ModuleContext c() {
        ModuleContext c = context;
        if (c == null) throw new IllegalStateException("PeakSound-Modul ist nicht gestartet");
        return c;
    }

    static File dataFolder() { return c().dataFolder(); }

    static FileConfiguration config() { return c().config(); }

    static Logger logger() { return c().logger(); }

    static String version() { return c().version(); }

    static void setSongs(SongStore store) { songs = store; }

    static SongStore songs() { return songs; }
}
