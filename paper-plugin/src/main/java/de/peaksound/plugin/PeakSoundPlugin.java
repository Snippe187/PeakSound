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
    private FavoriteStore favorites;
    private JukeboxSettingsStore settingsStore;
    private SpeakerStore speakerStoreRef;
    private BlockSettingsStore<de.peaksound.protocol.LightSettings> lightStore;
    private BlockSettingsStore<de.peaksound.protocol.DjSettings> djStore;
    private LightService lightService;
    private BlockSettingsStore<de.peaksound.protocol.MicSettings> micStore;
    private MicService micService;
    private JukeboxStorage storageRef;
    private BlockSettingsStore<de.peaksound.protocol.RadioSettings> radioStoreRef;
    private BlockSettingsStore<de.peaksound.protocol.RemoteSettings> remoteStoreRef;

    SongStore songs() { return songs; }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        BackupService backup = new BackupService(this);
        backup.applyPendingRestore(); // eine bereitgestellte Sicherung wird VOR dem Laden der Daten eingespielt
        PeakItems items = new PeakItems();

        blocks = new BlockRegistry(this);
        blocks.load();
        blocks.startAutosave();

        songs = new SongStore(this);
        songs.load();
        favorites = new FavoriteStore(this);
        favorites.load();
        Bukkit.getAsyncScheduler().runAtFixedRate(this, t -> { songs.saveIfDirty(); favorites.saveIfDirty(); }, 15, 15, TimeUnit.SECONDS);

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
        JukeboxStorage storage = new JukeboxStorage(this);
        storage.load();
        storage.startAutosave();
        this.storageRef = storage;
        PlaylistService playlist = new PlaylistService(items, songs, storage);
        NetworkRegistry networks = new NetworkRegistry(this, blocks);
        EqualizerService equalizer = new EqualizerService(this, blocks, analysis, ComparatorDriver.create(getLogger()).orElse(null), jukeboxSettings);
        getLogger().info(equalizer.available() ? "Comparator-Equalizer bereit." : "Comparator-Equalizer ausgeschaltet.");
        SpeakerStore speakerStore = new SpeakerStore(this);
        speakerStore.load();
        speakerStore.startAutosave();
        this.speakerStoreRef = speakerStore;
        SpeakerService speakers = new SpeakerService(this, blocks, networks, speakerStore);
        playback = new PlaybackManager(this, network, jukeboxSettings, analysis, playlist, networks, equalizer, speakers);
        playback.setPlayCounter(songs::countPlay);
        playback.load();
        EditAccess access = new EditAccess(blocks);
        JukeboxMenuService menu = new JukeboxMenuService(this, blocks, jukeboxSettings, playback, playlist, access, speakers);
        SpeakerMenuService speakerMenu = new SpeakerMenuService(this, access, speakers, jukeboxSettings);
        AdminService admin = new AdminService(this, songs, limits, playback, analysis);
        StatsService statsService = new StatsService(songs, limits, blocks, playback, network);
        admin.setStats(statsService);
        backup.scheduleDaily();
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
        micStore = new BlockSettingsStore<>(this, "microphones.json", de.peaksound.protocol.MicSettings.class,
                de.peaksound.protocol.MicSettings.defaults(), de.peaksound.protocol.MicSettings::sanitized);
        micStore.load();
        micStore.startAutosave();
        micService = new MicService(this, blocks, networks, micStore, speakers, jukeboxSettings, playback, access);
        if (Bukkit.getPluginManager().getPlugin("voicechat") != null) {
            getLogger().info(VoiceChatHook.register(this, micService)
                    ? "Simple Voice Chat gefunden: Mikrofon-Bloecke sind aktiv."
                    : "Simple Voice Chat gefunden, aber die Anbindung ist fehlgeschlagen.");
        } else {
            getLogger().info("Simple Voice Chat nicht installiert: Mikrofon-Bloecke bleiben ohne Funktion.");
        }
        network.wire(uploads, streams, playback, menu, speakerMenu, access, admin, lightService, dj, micService);
        DebugService debugService = new DebugService(this, blocks, songs, playback, network, equalizer, micService);
        network.setDebug(debugService);
        LibraryService library = new LibraryService(this, songs, items, access, favorites, playback, analysis);
        network.setLibrary(library);
        library.setUploads(uploads);
        BlockSettingsStore<de.peaksound.protocol.RemoteSettings> remoteStore = new BlockSettingsStore<>(this, "remotes.json",
                de.peaksound.protocol.RemoteSettings.class, de.peaksound.protocol.RemoteSettings.defaults(),
                de.peaksound.protocol.RemoteSettings::sanitized);
        remoteStore.load();
        remoteStore.startAutosave();
        this.remoteStoreRef = remoteStore;
        RemoteService remote = new RemoteService(this, blocks, networks, remoteStore, playback, playlist, jukeboxSettings, access);
        network.setRemote(remote);
        BlockSettingsStore<de.peaksound.protocol.RadioSettings> radioStore = new BlockSettingsStore<>(this, "radio.json",
                de.peaksound.protocol.RadioSettings.class, de.peaksound.protocol.RadioSettings.defaults(),
                de.peaksound.protocol.RadioSettings::sanitized);
        radioStore.load();
        radioStore.startAutosave();
        this.radioStoreRef = radioStore;
        RadioService radio = new RadioService(this, blocks, networks, radioStore, access, network, items);
        getServer().getPluginManager().registerEvents(radio, this);
        radio.setPlayback(playback);
        debugService.setRadio(radio);
        playback.setRadio(radio);
        QueueService queueService = new QueueService(this, blocks, networks, songs, playback, jukeboxSettings, items);
        playback.setQueue(queueService);
        library.setQueue(queueService);
        getCommand("peaksoundvote").setExecutor(queueService);
        network.setRadio(radio);

        getServer().getMessenger().registerIncomingPluginChannel(this, PeakIds.CHANNEL, network);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PeakIds.CHANNEL);

        SettingsClipboard clipboard = new SettingsClipboard(items, jukeboxSettings, playback, speakers, lightService, dj, micService, remote);
        getServer().getPluginManager().registerEvents(clipboard, this);
        getServer().getPluginManager().registerEvents(network, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks, playback, jukeboxSettings, menu, network, speakers, speakerMenu, clipboard, lightService, dj, micService, library, remote, storage, radio), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this, items, recipes), this);
        getServer().getPluginManager().registerEvents(new NetworkListener(networks, equalizer), this);
        PeakSoundCommand command = new PeakSoundCommand(equalizer, blocks, songs, playback, items, speakers, dj, lightService, debugService, library, remote, jukeboxSettings, menu, radio, statsService, backup);
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
        Bukkit.getScheduler().runTaskTimer(this, micService::tick, 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(this, remote::tick, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(this, radio::tick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, new JukeboxItemIo(blocks, jukeboxSettings, playlist)::tick, 8L, 8L);
        Bukkit.getScheduler().runTaskTimer(this, dj::redstoneTick, 4L, 4L);
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
        if (favorites != null) favorites.saveIfDirty();
        if (playback != null) playback.saveBlocking();
        if (settingsStore != null) settingsStore.saveNow();
        if (speakerStoreRef != null) speakerStoreRef.saveNow();
        if (lightStore != null) lightStore.saveNow();
        if (djStore != null) djStore.saveNow();
        if (lightService != null) lightService.shutdown();
        if (micService != null) micService.shutdown();
        if (micStore != null) micStore.saveNow();
        if (remoteStoreRef != null) remoteStoreRef.saveNow();
        if (radioStoreRef != null) radioStoreRef.saveNow();
        if (storageRef != null) storageRef.saveNow();
    }
}
