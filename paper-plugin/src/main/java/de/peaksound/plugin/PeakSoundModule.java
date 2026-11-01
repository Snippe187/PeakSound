package de.peaksound.plugin;

import de.peakverse.core.api.module.ModuleContext;
import de.peakverse.core.api.module.PeakModule;
import de.peaksound.audio.Transcoder;
import de.peaksound.protocol.PeakIds;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Einstiegspunkt des PeakSound-Moduls. PeakSound ist ein eingebautes Modul von PeakCore (kein eigenes Bukkit-Plugin mehr); Datenordner plugins/PeakSound,
 * Befehle, Rechte und Kanaele sind dieselben wie in der Alpha-Version. Die Logik liegt in den einzelnen Klassen dieses Pakets.
 */
public final class PeakSoundModule implements PeakModule {
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
    private BlockSettingsStore<DisplaySettings> displayStoreRef;
    private DisplayService displayRef;
    private BlockSettingsStore<de.peaksound.protocol.RemoteSettings> remoteStoreRef;
    private CoreLink coreLink;

    @Override
    public String id() { return CoreLink.MODULE_ID; }

    @Override
    public String displayName() { return "PeakSound"; }

    @Override
    public void enable(ModuleContext ctx) {
        Ctx.init(ctx);
        JavaPlugin plugin = ctx.host();
        ctx.saveDefaultConfig();
        BackupService backup = new BackupService(plugin);
        backup.applyPendingRestore(); // eine bereitgestellte Sicherung wird VOR dem Laden der Daten eingespielt
        PeakItems items = new PeakItems();

        blocks = new BlockRegistry(plugin);
        blocks.load();
        blocks.startAutosave();

        songs = new SongStore(plugin);
        songs.load();
        Ctx.setSongs(songs);
        favorites = new FavoriteStore(plugin);
        favorites.load();
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, t -> { songs.saveIfDirty(); favorites.saveIfDirty(); }, 15, 15, TimeUnit.SECONDS);

        recipes = new PeakRecipes(plugin, items);
        recipes.register();

        NetworkHandler network = new NetworkHandler(plugin, recipes);
        Net.setHandler(network);
        Transcoder transcoder = Transcoder.create(ctx.config().getString("upload.ffmpeg-path", ""));
        ctx.logger().info(transcoder.ffmpegAvailable()
                ? "ffmpeg gefunden: Uploads werden zu Ogg Vorbis komprimiert."
                : "ffmpeg nicht gefunden: OGG/FLAC/MP3 bleiben unveraendert, WAV wird zu FLAC.");
        AnalysisStore analysis = new AnalysisStore(plugin, songs);
        JukeboxSettingsStore jukeboxSettings = new JukeboxSettingsStore(plugin);
        jukeboxSettings.load();
        jukeboxSettings.startAutosave();
        this.settingsStore = jukeboxSettings;
        AdminLimits limits = new AdminLimits(plugin);
        limits.load();
        UploadService uploads = new UploadService(plugin, items, songs, analysis, transcoder, limits);
        SongStreamService streams = new SongStreamService(plugin, songs);
        JukeboxStorage storage = new JukeboxStorage(plugin);
        storage.load();
        storage.startAutosave();
        this.storageRef = storage;
        PlaylistService playlist = new PlaylistService(items, songs, storage);
        NetworkRegistry networks = new NetworkRegistry(plugin, blocks);
        EqualizerService equalizer = new EqualizerService(plugin, blocks, analysis, ComparatorDriver.create(ctx.logger()).orElse(null), jukeboxSettings);
        ctx.logger().info(equalizer.available() ? "Comparator-Equalizer bereit." : "Comparator-Equalizer ausgeschaltet.");
        SpeakerStore speakerStore = new SpeakerStore(plugin);
        speakerStore.load();
        speakerStore.startAutosave();
        this.speakerStoreRef = speakerStore;
        SpeakerService speakers = new SpeakerService(plugin, blocks, networks, speakerStore);
        playback = new PlaybackManager(plugin, network, jukeboxSettings, analysis, playlist, networks, equalizer, speakers);
        playback.setPlayCounter(songs::countPlay);
        playback.load();
        EditAccess access = new EditAccess(blocks);
        JukeboxMenuService menu = new JukeboxMenuService(plugin, blocks, jukeboxSettings, playback, playlist, access, speakers);
        SpeakerMenuService speakerMenu = new SpeakerMenuService(plugin, access, speakers, jukeboxSettings);
        AdminService admin = new AdminService(plugin, songs, limits, playback, analysis);
        StatsService statsService = new StatsService(songs, limits, blocks, playback, network);
        admin.setStats(statsService);
        backup.scheduleDaily();
        lightStore = new BlockSettingsStore<>(plugin, "lights.json", de.peaksound.protocol.LightSettings.class,
                de.peaksound.protocol.LightSettings.defaults(), de.peaksound.protocol.LightSettings::sanitized);
        lightStore.load();
        lightStore.startAutosave();
        djStore = new BlockSettingsStore<>(plugin, "dj-desks.json", de.peaksound.protocol.DjSettings.class,
                de.peaksound.protocol.DjSettings.defaults(), de.peaksound.protocol.DjSettings::sanitized);
        djStore.load();
        djStore.startAutosave();
        lightService = new LightService(plugin, blocks, networks, lightStore, playback, analysis, songs, network, access);
        lightService.cleanupLeftovers();
        DjService dj = new DjService(plugin, blocks, networks, djStore, playback, playlist, jukeboxSettings, access);
        micStore = new BlockSettingsStore<>(plugin, "microphones.json", de.peaksound.protocol.MicSettings.class,
                de.peaksound.protocol.MicSettings.defaults(), de.peaksound.protocol.MicSettings::sanitized);
        micStore.load();
        micStore.startAutosave();
        micService = new MicService(plugin, blocks, networks, micStore, speakers, jukeboxSettings, playback, access);
        if (Bukkit.getPluginManager().getPlugin("voicechat") != null) {
            ctx.logger().info(VoiceChatHook.register(plugin, micService)
                    ? "Simple Voice Chat gefunden: Mikrofon-Bloecke sind aktiv."
                    : "Simple Voice Chat gefunden, aber die Anbindung ist fehlgeschlagen.");
        } else {
            ctx.logger().info("Simple Voice Chat nicht installiert: Mikrofon-Bloecke bleiben ohne Funktion.");
        }
        network.wire(uploads, streams, playback, menu, speakerMenu, access, admin, lightService, dj, micService);
        DebugService debugService = new DebugService(plugin, blocks, songs, playback, network, equalizer, micService);
        network.setDebug(debugService);
        LibraryService library = new LibraryService(plugin, songs, items, access, favorites, playback, analysis);
        network.setLibrary(library);
        library.setUploads(uploads);
        BlockSettingsStore<de.peaksound.protocol.RemoteSettings> remoteStore = new BlockSettingsStore<>(plugin, "remotes.json",
                de.peaksound.protocol.RemoteSettings.class, de.peaksound.protocol.RemoteSettings.defaults(),
                de.peaksound.protocol.RemoteSettings::sanitized);
        remoteStore.load();
        remoteStore.startAutosave();
        this.remoteStoreRef = remoteStore;
        RemoteService remote = new RemoteService(plugin, blocks, networks, remoteStore, playback, playlist, jukeboxSettings, access);
        network.setRemote(remote);
        BlockSettingsStore<de.peaksound.protocol.RadioSettings> radioStore = new BlockSettingsStore<>(plugin, "radio.json",
                de.peaksound.protocol.RadioSettings.class, de.peaksound.protocol.RadioSettings.defaults(),
                de.peaksound.protocol.RadioSettings::sanitized);
        radioStore.load();
        radioStore.startAutosave();
        this.radioStoreRef = radioStore;
        RadioService radio = new RadioService(plugin, blocks, networks, radioStore, access, network, items);
        plugin.getServer().getPluginManager().registerEvents(radio, plugin);
        radio.setPlayback(playback);
        debugService.setRadio(radio);
        playback.setRadio(radio);
        BlockSettingsStore<DisplaySettings> displayStore = new BlockSettingsStore<>(plugin, "displays.json", DisplaySettings.class,
                DisplaySettings.defaults(), DisplaySettings::sanitized);
        displayStore.load();
        displayStore.startAutosave();
        this.displayStoreRef = displayStore;
        DisplayService displayService = new DisplayService(plugin, blocks, networks, playback, songs, displayStore);
        this.displayRef = displayService;
        plugin.getServer().getPluginManager().registerEvents(displayService, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, displayService::tick, 40L, 10L);
        debugService.setDisplay(displayService);
        QueueService queueService = new QueueService(plugin, blocks, networks, songs, playback, jukeboxSettings, items);
        playback.setQueue(queueService);
        library.setQueue(queueService);
        plugin.getCommand("peaksoundvote").setExecutor(queueService);
        network.setRadio(radio);

        // Alter Kanal fuer die Alpha-Mod (bleibt, damit bestehende Spieler nach dem Update weiterspielen koennen) ...
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, PeakIds.CHANNEL, network);
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, PeakIds.CHANNEL);
        // ... und Anmeldung bei PeakCore als Modul "peaksound" fuer die neue Mod
        coreLink = CoreLink.register(plugin, network);
        network.setCore(coreLink);

        // Song-Verwaltung in der Web-Oberflaeche von PeakCore (nur wenn sie eingeschaltet ist)
        if (ctx.web().enabled()) {
            try (java.io.InputStream in = PeakSoundModule.class.getResourceAsStream("/modules/peaksound/panel.js")) {
                if (in == null) throw new java.io.IOException("panel.js fehlt im Jar");
                new SongWeb(ctx.web(), songs, limits, analysis, favorites, playback).register(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                ctx.logger().warning("Web-Seite von PeakSound nicht verfuegbar: " + e.getMessage());
            }
        }

        SettingsClipboard clipboard = new SettingsClipboard(items, jukeboxSettings, playback, speakers, lightService, dj, micService, remote);
        plugin.getServer().getPluginManager().registerEvents(clipboard, plugin);
        plugin.getServer().getPluginManager().registerEvents(network, plugin);
        BlockListener blockListener = new BlockListener(items, blocks, playback, jukeboxSettings, menu, network, speakers, speakerMenu, clipboard, lightService, dj, micService, library, remote, storage, radio, displayService);
        plugin.getServer().getPluginManager().registerEvents(blockListener, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, blockListener::purgeStale, 600L, 600L); // verschwundene Bloecke aufraeumen
        plugin.getServer().getPluginManager().registerEvents(new CraftListener(plugin, items, recipes), plugin);
        plugin.getServer().getPluginManager().registerEvents(new NetworkListener(networks, equalizer), plugin);
        PeakSoundCommand command = new PeakSoundCommand(equalizer, blocks, songs, playback, items, speakers, dj, lightService, debugService, library, remote, jukeboxSettings, menu, radio, statsService, backup, displayService);
        plugin.getCommand("peaksound").setExecutor(command);
        plugin.getCommand("peaksound").setTabCompleter(command);
        plugin.getServer().getPluginManager().registerEvents(
                new RecordListener(plugin, items, blocks, songs, uploads, playback, network), plugin);

        // Wiedergabe-Zustand alle 10 Ticks pruefen (leichtgewichtig: nur ein Vergleich pro Jukebox und Spieler)
        Bukkit.getScheduler().runTaskTimer(plugin, playback::tick, 10L, 10L);
        // Partikel im Takt und Equalizer: feine Aufloesung, aber nur Nachschlagen in der vorberechneten Analyse
        Bukkit.getScheduler().runTaskTimer(plugin, playback::fastTick, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(plugin, lightService::tick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, dj::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, micService::tick, 5L, 5L);
        Bukkit.getScheduler().runTaskTimer(plugin, remote::tick, 2L, 2L);
        Bukkit.getScheduler().runTaskTimer(plugin, radio::tick, 10L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, new JukeboxItemIo(blocks, jukeboxSettings, playlist)::tick, 8L, 8L);
        Bukkit.getScheduler().runTaskTimer(plugin, dj::redstoneTick, 4L, 4L);
        // Lautsprecher-Nummern und Standard-Kanaele abgleichen (nutzt den Netz-Cache, rechnet nur bei Aenderungen)
        Bukkit.getScheduler().runTaskTimer(plugin, speakers::refresh, 20L, 20L);
        // Verbindungsanzeige fuer Spieler mit Peak-Tuner in der Hand
        ConnectionDisplay connections = new ConnectionDisplay(blocks, networks, items);
        Bukkit.getScheduler().runTaskTimer(plugin, connections::tick, 10L, 10L);
        // Operator-Status regelmaessig abgleichen (der Button der Song-Verwaltung folgt /op und /deop)
        Bukkit.getScheduler().runTaskTimer(plugin, network::checkOperators, 40L, 40L);
    }

    @Override
    public void disable() {
        if (coreLink != null) coreLink.unregister();
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
        if (displayRef != null) displayRef.shutdown();
        if (displayStoreRef != null) displayStoreRef.saveNow();
        if (storageRef != null) storageRef.saveNow();
        Ctx.clear();
    }
}
