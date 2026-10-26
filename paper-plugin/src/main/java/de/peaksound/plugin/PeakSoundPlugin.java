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
        UploadService uploads = new UploadService(this, items, songs, transcoder);
        SongStreamService streams = new SongStreamService(this, songs);
        playback = new PlaybackManager(this, network);
        playback.load();
        network.wire(uploads, streams, playback);

        getServer().getMessenger().registerIncomingPluginChannel(this, PeakIds.CHANNEL, network);
        getServer().getMessenger().registerOutgoingPluginChannel(this, PeakIds.CHANNEL);

        getServer().getPluginManager().registerEvents(network, this);
        getServer().getPluginManager().registerEvents(new BlockListener(items, blocks, playback), this);
        getServer().getPluginManager().registerEvents(new CraftListener(this, items, recipes), this);
        getServer().getPluginManager().registerEvents(
                new RecordListener(this, items, blocks, songs, uploads, playback, network), this);

        // Wiedergabe-Zustand alle 10 Ticks pruefen (leichtgewichtig: nur ein Vergleich pro Jukebox und Spieler)
        Bukkit.getScheduler().runTaskTimer(this, playback::tick, 10L, 10L);
    }

    @Override
    public void onDisable() {
        if (recipes != null) recipes.unregister();
        if (blocks != null) blocks.saveNow();
        if (songs != null) songs.saveIfDirty();
        if (playback != null) playback.saveBlocking();
    }
}
