package de.peaksound.plugin;

import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.PeakBlockType;
import de.peaksound.protocol.Packet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * Oeffnet das Jukebox-Menue beim Client und uebernimmt dessen Aenderungen. Jede Aenderung wird serverseitig
 * geprueft: der Block muss eine PeakSound-Jukebox sein, der Spieler in Reichweite, die Werte im erlaubten Bereich.
 */
final class JukeboxMenuService {
    /** Wie weit der Spieler hoechstens von der Jukebox entfernt sein darf, um sie zu bearbeiten. */
    private static final double MAX_EDIT_DISTANCE = 8.0;
    /** Mindestabstand zwischen zwei Aenderungen eines Spielers (Schutz vor Paketfluten). */
    private static final long MIN_UPDATE_INTERVAL_MS = 50;

    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final JukeboxSettingsStore settings;
    private final PlaybackManager playback;
    private final Map<UUID, Long> lastUpdate = new ConcurrentHashMap<>();

    JukeboxMenuService(PeakSoundPlugin plugin, BlockRegistry registry, JukeboxSettingsStore settings, PlaybackManager playback) {
        this.plugin = plugin;
        this.registry = registry;
        this.settings = settings;
        this.playback = playback;
    }

    void open(Player player, Block block) {
        String title = "";
        String artist = "";
        boolean playing = false;
        var state = playback.state(block);
        if (state.isPresent()) {
            title = state.get().title.length() > 64 ? state.get().title.substring(0, 64) : state.get().title;
            artist = state.get().artist.length() > 64 ? state.get().artist.substring(0, 64) : state.get().artist;
            playing = state.get().playing;
        }
        Net.send(plugin, player, new Packet.OpenJukeboxMenu(block.getX(), block.getY(), block.getZ(),
                settings.get(block), playing, title, artist));
    }

    void onUpdate(Player player, Packet.JukeboxUpdate update) {
        long now = System.currentTimeMillis();
        Long last = lastUpdate.put(player.getUniqueId(), now);
        if (last != null && now - last < MIN_UPDATE_INTERVAL_MS) return;

        World world = player.getWorld();
        if (!world.isChunkLoaded(update.x() >> 4, update.z() >> 4)) return;
        Block block = world.getBlockAt(update.x(), update.y(), update.z());
        if (registry.get(block).filter(t -> t == PeakBlockType.JUKEBOX).isEmpty()) return;
        double dx = player.getX() - (block.getX() + 0.5);
        double dy = player.getY() - block.getY();
        double dz = player.getZ() - (block.getZ() + 0.5);
        if (dx * dx + dy * dy + dz * dz > MAX_EDIT_DISTANCE * MAX_EDIT_DISTANCE + 4) return;

        JukeboxSettings clean = update.settings().sanitized();
        playback.applySettings(block, clean);
    }

    void onQuit(Player player) {
        lastUpdate.remove(player.getUniqueId());
    }
}
