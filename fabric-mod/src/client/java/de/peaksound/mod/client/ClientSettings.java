package de.peaksound.mod.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Einstellungen des Spielers an der Mod, gespeichert in {@code .minecraft/config/peaksound-client.json}:
 * eigene PeakSound-Lautstaerke (zusaetzlich zur Minecraft-Lautstaerke "Plattenspieler") und, fuer die Lichtshow,
 * ob Strobo-Effekte erlaubt sind.
 */
public final class ClientSettings {
    private static final class Data {
        int volume = 100;
        boolean strobe = true;
        boolean hud = true;
        boolean lyrics = true;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Data data = new Data();
    private static boolean loaded;

    private ClientSettings() {}

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("peaksound-client.json");
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        Path f = file();
        if (!Files.exists(f)) return;
        try (Reader r = Files.newBufferedReader(f)) {
            Data read = GSON.fromJson(r, Data.class);
            if (read != null) data = read;
        } catch (IOException | RuntimeException ignored) { /* kaputte Datei: Standardwerte */ }
    }

    private static synchronized void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(data));
        } catch (IOException ignored) { /* Einstellung gilt trotzdem fuer diese Sitzung */ }
    }

    /** Eigene PeakSound-Lautstaerke in Prozent (0 = Musik fuer mich aus). */
    public static synchronized int volumePercent() {
        ensureLoaded();
        return Math.max(0, Math.min(100, data.volume));
    }

    public static synchronized void setVolumePercent(int percent) {
        ensureLoaded();
        data.volume = Math.max(0, Math.min(100, percent));
        save();
    }

    /** Now-Playing-Anzeige beim Songstart. */
    public static synchronized boolean hud() { ensureLoaded(); return data.hud; }

    public static synchronized void setHud(boolean on) { ensureLoaded(); data.hud = on; save(); }

    /** Songtext-Zeile am unteren Bildschirmrand, solange ein Song mit Text laeuft. */
    public static synchronized boolean lyrics() { ensureLoaded(); return data.lyrics; }

    public static synchronized void setLyrics(boolean on) { ensureLoaded(); data.lyrics = on; save(); }

    /** Faktor 0-1 fuer die Wiedergabe. */
    public static float volumeFactor() { return volumePercent() / 100f; }

    public static synchronized boolean strobeAllowed() {
        ensureLoaded();
        return data.strobe;
    }

    public static synchronized void setStrobeAllowed(boolean allowed) {
        ensureLoaded();
        data.strobe = allowed;
        save();
    }
}
