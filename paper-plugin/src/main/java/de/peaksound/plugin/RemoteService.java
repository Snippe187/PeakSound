package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.LoopMode;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.RemoteLogic;
import de.peaksound.protocol.RemoteSettings;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Musik-Fernbedienung: Ein Redstone-Signal an diesem Block fuehrt die im Menue gewaehlte Aktion an allen Jukeboxen des
 * Redstone-Netzes aus, an dem die Fernbedienung per Staub haengt (Play/Stopp, Pause, Skip, Zurueck, Shuffle, Wiederholen,
 * Song nach Signalstaerke, Lautstaerke nach Signalstaerke). Die Auswertung laeuft auf dem Hauptthread alle 2 Ticks.
 */
final class RemoteService {
    private final JavaPlugin plugin;
    private final BlockRegistry registry;
    private final NetworkRegistry networks;
    private final BlockSettingsStore<RemoteSettings> store;
    private final PlaybackManager playback;
    private final PlaylistService playlist;
    private final JukeboxSettingsStore jukeboxSettings;
    private final EditAccess access;
    /** Letzte Signalstaerke je Fernbedienung (fehlt = noch nicht gemessen: der erste Wert loest nichts aus). */
    private final Map<BlockRegistry.Key, Integer> last = new HashMap<>();

    RemoteService(JavaPlugin plugin, BlockRegistry registry, NetworkRegistry networks, BlockSettingsStore<RemoteSettings> store,
                  PlaybackManager playback, PlaylistService playlist, JukeboxSettingsStore jukeboxSettings, EditAccess access) {
        this.plugin = plugin;
        this.registry = registry;
        this.networks = networks;
        this.store = store;
        this.playback = playback;
        this.playlist = playlist;
        this.jukeboxSettings = jukeboxSettings;
        this.access = access;
    }

    void open(Player player, Block block) {
        BlockRegistry.Key key = BlockRegistry.Key.of(block);
        NetworkRegistry.Net net = networks.networkOf(key);
        boolean connected = net != null && !net.jukeboxes().isEmpty();
        Net.send(plugin, player, new Packet.OpenRemoteMenu(block.getX(), block.getY(), block.getZ(), store.get(key), connected));
    }

    void onUpdate(Player player, Packet.RemoteUpdate update) {
        Block block = access.validated(player, update.x(), update.y(), update.z(), PeakBlockType.MUSIC_REMOTE);
        if (block == null) return;
        store.set(BlockRegistry.Key.of(block), update.settings().sanitized());
        last.remove(BlockRegistry.Key.of(block)); // neue Aktion: Signal neu einmessen, nichts ausloesen
    }

    RemoteSettings settingsOf(Block block) { return store.get(BlockRegistry.Key.of(block)); }

    void put(Block block, RemoteSettings settings) {
        store.set(BlockRegistry.Key.of(block), settings);
        last.remove(BlockRegistry.Key.of(block));
    }

    void remove(Block block) {
        store.remove(block);
        last.remove(BlockRegistry.Key.of(block));
    }

    /** Alle 2 Ticks: Signale messen und Aktionen ausloesen. */
    void tick() {
        List<BlockRegistry.Key> remotes = registry.keysOf(PeakBlockType.MUSIC_REMOTE);
        last.keySet().removeIf(k -> !remotes.contains(k));
        for (BlockRegistry.Key key : remotes) {
            Block block = blockOf(key);
            if (block == null) continue;
            int strength = block.getBlockPower();
            Integer before = last.put(key, strength);
            if (before == null || before == strength) continue;
            RemoteSettings settings = store.get(key);
            RemoteLogic.Trigger trigger = RemoteLogic.evaluate(settings, before, strength);
            if (trigger.fire()) run(key, settings, trigger.strength());
        }
    }

    private Block blockOf(BlockRegistry.Key k) {
        World w = Bukkit.getWorld(k.world());
        return w == null || !w.isChunkLoaded(k.x() >> 4, k.z() >> 4) ? null : w.getBlockAt(k.x(), k.y(), k.z());
    }

    private void run(BlockRegistry.Key remote, RemoteSettings settings, int strength) {
        NetworkRegistry.Net net = networks.networkOf(remote);
        if (net == null) return;
        for (BlockRegistry.Key j : net.jukeboxes()) {
            Block jukebox = blockOf(j);
            if (jukebox != null) act(jukebox, settings, strength);
        }
    }

    private void act(Block jukebox, RemoteSettings settings, int strength) {
        var state = playback.state(jukebox);
        boolean manualRecord = state.isPresent() && !state.get().fromPlaylist;
        JukeboxSettings cfg = jukeboxSettings.get(jukebox);
        switch (settings.action()) {
            case PLAY_LEVEL -> {
                if (strength > 0) {
                    if (state.isPresent() && !state.get().playing) playback.resume(jukebox);
                    else if (state.isEmpty()) playback.playNext(jukebox, null, false);
                } else {
                    playback.stopPlayback(jukebox);
                }
            }
            case PAUSE_TOGGLE -> playback.togglePause(jukebox);
            case SKIP -> {
                if (!manualRecord) playback.playNext(jukebox, state.orElse(null), true);
            }
            case PREVIOUS -> playback.previous(jukebox);
            case SHUFFLE -> playback.applySettings(jukebox, cfg.withShuffle(!cfg.shuffle()));
            case REPEAT -> playback.applySettings(jukebox, cfg.withLoop(cfg.loop().next()));
            case SONG_BY_STRENGTH -> {
                int index = RemoteLogic.songIndexFor(strength, playlist.tracks(jukebox).size());
                if (index >= 0 && !manualRecord) playback.playIndex(jukebox, index);
            }
            case VOLUME_BY_STRENGTH -> playback.applySettings(jukebox, cfg.withRangeAndVolume(cfg.range(), RemoteLogic.volumeFor(strength)));
        }
    }
}