package de.peaksound.plugin;

import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Oeffnet das Jukebox-Menue beim Client und uebernimmt dessen Aenderungen und Playlist-Aktionen. Jede Aktion
 * wird serverseitig geprueft: der Block muss eine PeakSound-Jukebox sein, der Spieler in Reichweite, die Werte
 * im erlaubten Bereich.
 */
final class JukeboxMenuService {
    /** Wie weit der Spieler hoechstens von der Jukebox entfernt sein darf, um sie zu bearbeiten. */
    private static final double MAX_EDIT_DISTANCE = 8.0;
    /** Mindestabstand zwischen zwei Anfragen eines Spielers (Schutz vor Paketfluten). */
    private static final long MIN_INTERVAL_MS = 50;

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final JukeboxSettingsStore settings;
    private final PlaybackManager playback;
    private final PlaylistService playlist;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    JukeboxMenuService(PeakSoundPlugin plugin, BlockRegistry registry, JukeboxSettingsStore settings,
                       PlaybackManager playback, PlaylistService playlist) {
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
        this.playback = playback;
        this.playlist = playlist;
    }

    void open(Player player, Block block) {
        String title = "";
        String artist = "";
        boolean playing = false;
        var state = playback.state(block);
        if (state.isPresent()) {
            title = cut(state.get().title);
            artist = cut(state.get().artist);
            playing = state.get().playing;
        }
        Net.send(plugin, player, new Packet.OpenJukeboxMenu(block.getX(), block.getY(), block.getZ(),
                settings.get(block), playing, title, artist));
        sendPlaylist(player, block);
    }

    void onUpdate(Player player, Packet.JukeboxUpdate update) {
        Block block = validated(player, update.x(), update.y(), update.z());
        if (block == null) return;
        playback.applySettings(block, update.settings().sanitized());
    }

    void onPlaylistRequest(Player player, Packet.PlaylistRequest request) {
        Block block = validated(player, request.x(), request.y(), request.z());
        if (block != null) sendPlaylist(player, block);
    }

    void onPlaylistAction(Player player, Packet.PlaylistAction action) {
        Block block = validated(player, action.x(), action.y(), action.z());
        if (block == null) return;
        var state = playback.state(block);
        // Eine von Hand eingelegte Platte hat Vorrang: Playlist und Platte duerfen sich nicht gegenseitig ueberschreiben
        boolean manualRecord = state.isPresent() && !state.get().fromPlaylist;
        switch (action.action()) {
            case PLAY -> {
                if (manualRecord) player.sendMessage(Messages.error(player,
                        "Entnimm zuerst die eingelegte Platte, dann kann die Playlist spielen.",
                        "Remove the inserted record first so the playlist can play."));
                else if (!playback.playIndex(block, action.index())) player.sendMessage(Messages.error(player,
                        "Dieser Song ist nicht verfuegbar.", "This song is not available."));
            }
            case SKIP -> {
                if (manualRecord) break;
                playback.playNext(block, state.orElse(null), true);
            }
            case STOP -> playback.stopPlayback(block);
            case MOVE_UP -> playback.moveTrack(block, action.index(), true);
            case MOVE_DOWN -> playback.moveTrack(block, action.index(), false);
        }
        sendPlaylist(player, block);
    }

    private void sendPlaylist(Player player, Block block) {
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        List<Packet.PlaylistEntry> entries = new ArrayList<>(tracks.size());
        for (PlaylistService.Track t : tracks) {
            String title = cut(t.song() != null ? t.song().title() : t.written().title());
            String artist = cut(t.song() != null ? t.song().artist() : t.written().artist());
            entries.add(new Packet.PlaylistEntry(title, artist, t.song() != null ? t.song().durationMs() : 0, t.playable()));
        }
        var state = playback.state(block);
        Net.send(plugin, player, new Packet.PlaylistState(block.getX(), block.getY(), block.getZ(),
                playback.currentIndex(block, tracks), state.isPresent() && state.get().playing, entries));
    }

    /**
     * Prueft eine Client-Anfrage: Rate-Limit, geladener Chunk, PeakSound-Jukebox, Spieler in Reichweite.
     *
     * @return der Block, oder null wenn die Anfrage abgelehnt wird
     */
    private Block validated(Player player, int x, int y, int z) {
        long now = System.currentTimeMillis();
        Long last = lastRequest.put(player.getUniqueId(), now);
        if (last != null && now - last < MIN_INTERVAL_MS) return null;

        World world = player.getWorld();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        Block block = world.getBlockAt(x, y, z);
        if (registry.get(block).filter(t -> t == PeakBlockType.JUKEBOX).isEmpty()) return null;
        double dx = player.getX() - (block.getX() + 0.5);
        double dy = player.getY() - block.getY();
        double dz = player.getZ() - (block.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > MAX_EDIT_DISTANCE * MAX_EDIT_DISTANCE + 4) return null;
        return block;
    }

    private static String cut(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    void onQuit(Player player) {
        lastRequest.remove(player.getUniqueId());
    }
}
