package de.peaksound.plugin;

import de.peaksound.audio.Transcoder;
import de.peaksound.protocol.PeakIds;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Einstiegspunkt des PeakSound-Plugins. Die Logik liegt in den einzelnen Klassen dieses Pakets. */
public final class PeakSoundPlugin extends JavaPlugin {
    private BlockRegistry blocks;
    private PeakRecipes recipes;
    private SongStore songs;
    private PlaybackManager playback;
    private JukeboxSettingsStore settingsStore;

    SongStore songs() { return songs; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PeakItems items = new PeakItems();

        blocks = new BlockRegistry(this);
        blocks.load();
        blocks.startAutosave();

        songs = new SongStore(this);
        songs.load();
        Bukkit.getAsyncScheduler().runAtFixedRate(this, t -> songs.saveIfDirty(), 15, 15, TimeUnit.SECONDS);

        recipes = new PeakRecipes(this, items);
        recipes.register();

        NetworkHandler network = new NetworkHandler(this, recipes);
        Transcoder transcoder = Transcoder.create(getConfig().getString("upload.ffmpeg-path", ""));
        getLogger().info(transcoder.ffmpegAvailable()
                ? "ffmpeg gefunden: Uploads werden zu Ogg Vorbis komprimiert."
                : "ffmpeg nicht gefunden: OGG/FLAC/MP3 bleiben unveraendert, WAV wird zu FLAC.");
        AnalysisStore analysis = new AnalysisStore(this, songs);
        JukeboxSettingsStore jukeboxSettings = new JukeboxSettingsStore(this);
        jukeboxSettings.load();
        jukeboxSettings.startAutosave();
        this.settingsStore = jukeboxSettings;
        UploadService uploads = new UploadService(this, items, songs, analysis, transcoder);
        SongStreamService streams = new SongStreamService(this, songs);
        PlaylistService playlist = new PlaylistService(items, songs);
        NetworkRegistry networks = new NetworkRegistry(this, blocks);
        EqualizerService equalizer = new EqualizerService(this, blocks, analysis, ComparatorDriver.create(getLogger()).orElse(null));
        getLogger().info(equalizer.available() ? "Comparator-Equalizer bereit." : "Comparator-Equalizer ausgeschaltet.");
        playback = new PlaybackManager(this, network, jukeboxSettings, analysis, playlist, networks, equalizer);
        playback.load();
        JukeboxMenuService menu = new JukeboxMenuService(this, blocks, jukeboxSettings, playback, playlist);
        network.wire(uploads, streams, playback, menu);

        getServer().getMessenger().registerIncomingPluginChannel(this, PeakIds.CHANNEL, network);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PeakIds.CHANNEL);

        getServer().getPluginManager().registerEvents(network, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks, playback, jukeboxSettings, menu, network), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this, items, recipes), this);
        getServer().getPluginManager().registerEvents(new NetworkListener(networks, equalizer), this);
        PeakSoundCommand command = new PeakSoundCommand(equalizer, blocks, songs, playback, items);
        getCommand("peaksound").setExecutor(command);
        getCommand("peaksound").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(
                new RecordListener(this, items, blocks, songs, uploads, playback, network), this);

        // Wiedergabe-Zustand alle 10 Ticks pruefen (leichtgewichtig: nur ein Vergleich pro Jukebox und Spieler)
        Bukkit.getScheduler().runTaskTimer(this, playback::tick, 10L, 10L);
        // Partikel im Takt und Equalizer: feine Aufloesung, aber nur Nachschlagen in der vorberechneten Analyse
        Bukkit.getScheduler().runTaskTimer(this, playback::fastTick, 2L, 2L);
    }

    @Override
    public void onDisable() {
        if (recipes != null) recipes.unregister();
        if (blocks != null) blocks.saveNow();
        if (songs != null) songs.saveIfDirty();
        if (playback != null) playback.saveBlocking();
        if (settingsStore != null) settingsStore.saveNow();
    }
}
