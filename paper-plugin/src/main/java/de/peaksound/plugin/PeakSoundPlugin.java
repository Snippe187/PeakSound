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
    private SpeakerStore speakerStoreRef;
    private BlockSettingsStore<de.peaksound.protocol.LightSettings> lightStore;
    private BlockSettingsStore<de.peaksound.protocol.DjSettings> djStore;
    private LightService lightService;

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
        AdminLimits limits = new AdminLimits(this);
        limits.load();
        UploadService uploads = new UploadService(this, items, songs, analysis, transcoder, limits);
        SongStreamService streams = new SongStreamService(this, songs);
        PlaylistService playlist = new PlaylistService(items, songs);
        NetworkRegistry networks = new NetworkRegistry(this, blocks);
        EqualizerService equalizer = new EqualizerService(this, blocks, analysis, ComparatorDriver.create(getLogger()).orElse(null));
        getLogger().info(equalizer.available() ? "Comparator-Equalizer bereit." : "Comparator-Equalizer ausgeschaltet.");
        SpeakerStore speakerStore = new SpeakerStore(this);
        speakerStore.load();
        speakerStore.startAutosave();
        this.speakerStoreRef = speakerStore;
        SpeakerService speakers = new SpeakerService(this, blocks, networks, speakerStore);
        playback = new PlaybackManager(this, network, jukeboxSettings, analysis, playlist, networks, equalizer, speakers);
        playback.load();
        EditAccess access = new EditAccess(blocks);
        JukeboxMenuService menu = new JukeboxMenuService(this, blocks, jukeboxSettings, playback, playlist, access, speakers);
        SpeakerMenuService speakerMenu = new SpeakerMenuService(this, access, speakers, jukeboxSettings);
        AdminService admin = new AdminService(this, songs, limits, playback, analysis);
        lightStore = new BlockSettingsStore<>(this, "lights.json", de.peaksound.protocol.LightSettings.class,
                de.peaksound.protocol.LightSettings.defaults(), de.peaksound.protocol.LightSettings::sanitized);
        lightStore.load();
        lightStore.startAutosave();
        djStore = new BlockSettingsStore<>(this, "dj-desks.json", de.peaksound.protocol.DjSettings.class,
                de.peaksound.protocol.DjSettings.defaults(), de.peaksound.protocol.DjSettings::sanitized);
        djStore.load();
        djStore.startAutosave();
        lightService = new LightService(this, blocks, networks, lightStore, playback, analysis, songs, network, access);
        lightService.cleanupLeftovers();
        DjService dj = new DjService(this, blocks, networks, djStore, playback, playlist, jukeboxSettings, access);
        network.wire(uploads, streams, playback, menu, speakerMenu, access, admin, lightService, dj);

        getServer().getMessenger().registerIncomingPluginChannel(this, PeakIds.CHANNEL, network);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PeakIds.CHANNEL);

        SettingsClipboard clipboard = new SettingsClipboard(items, jukeboxSettings, playback, speakers, lightService, dj);
        getServer().getPluginManager().registerEvents(clipboard, this);
        getServer().getPluginManager().registerEvents(network, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks, playback, jukeboxSettings, menu, network, speakers, speakerMenu, clipboard, lightService, dj), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this, items, recipes), this);
        getServer().getPluginManager().registerEvents(new NetworkListener(networks, equalizer), this);
        PeakSoundCommand command = new PeakSoundCommand(equalizer, blocks, songs, playback, items, speakers, dj);
        getCommand("peaksound").setExecutor(command);
        getCommand("peaksound").setTabCompleter(command);
        getServer().getPluginManager().registerEvents(
                new RecordListener(this, items, blocks, songs, uploads, playback, network), this);

        // Wiedergabe-Zustand alle 10 Ticks pruefen (leichtgewichtig: nur ein Vergleich pro Jukebox und Spieler)
        Bukkit.getScheduler().runTaskTimer(this, playback::tick, 10L, 10L);
        // Partikel im Takt und Equalizer: feine Aufloesung, aber nur Nachschlagen in der vorberechneten Analyse
        Bukkit.getScheduler().runTaskTimer(this, playback::fastTick, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, lightService::tick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, dj::tick, 20L, 20L);
        // Lautsprecher-Nummern und Standard-Kanaele abgleichen (nutzt den Netz-Cache, rechnet nur bei Aenderungen)
        Bukkit.getScheduler().runTaskTimer(this, speakers::refresh, 20L, 20L);
        // Verbindungsanzeige fuer Spieler mit Peak-Tuner in der Hand
        ConnectionDisplay connections = new ConnectionDisplay(blocks, networks, items);
        Bukkit.getScheduler().runTaskTimer(this, connections::tick, 10L, 10L);
        // Operator-Status regelmaessig abgleichen (der Button der Song-Verwaltung folgt /op und /deop)
        Bukkit.getScheduler().runTaskTimer(this, network::checkOperators, 40L, 40L);
    }

    @Override
    public void onDisable() {
        if (recipes != null) recipes.unregister();
        if (blocks != null) blocks.saveNow();
        if (songs != null) songs.saveIfDirty();
        if (playback != null) playback.saveBlocking();
        if (settingsStore != null) settingsStore.saveNow();
        if (speakerStoreRef != null) speakerStoreRef.saveNow();
        if (lightStore != null) lightStore.saveNow();
        if (djStore != null) djStore.saveNow();
        if (lightService != null) lightService.shutdown();
    }
}
