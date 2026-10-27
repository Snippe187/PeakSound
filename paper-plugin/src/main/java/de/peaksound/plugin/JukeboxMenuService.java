package de.peaksound.plugin;

import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakBlockType;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Oeffnet das Jukebox-Menue beim Client und uebernimmt dessen Aenderungen und Playlist-Aktionen. Jede Aktion
 * wird serverseitig geprueft: der Block muss eine PeakSound-Jukebox sein, der Spieler in Reichweite, die Werte
 * im erlaubten Bereich.
 */
final class JukeboxMenuService {
    private final PeakSoundPlugin plugin;
    private final BlockRegistry registry;
    private final JukeboxSettingsStore settings;
    private final PlaybackManager playback;
    private final PlaylistService playlist;
    private final EditAccess access;
    private final SpeakerService speakers;

    JukeboxMenuService(PeakSoundPlugin plugin, BlockRegistry registry, JukeboxSettingsStore settings,
                       PlaybackManager playback, PlaylistService playlist, EditAccess access, SpeakerService speakers) {
        this.plugin = plugin;
        this.access = access;
        this.speakers = speakers;
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

    /** Platte aus dem eigenen Inventar ins Jukebox-Inventar legen oder zurueck nehmen. */
    void onInvAction(Player player, Packet.JukeboxInvAction action) {
        Block block = validated(player, action.x(), action.y(), action.z());
        if (block == null) return;
        if (action.kind() == Packet.JukeboxInvAction.Kind.PUT) {
            ItemStack stack = player.getInventory().getItem(action.slot());
            if (stack == null || stack.getType().isAir()) return;
            if (playlist.put(block, stack)) {
                stack.setAmount(stack.getAmount() - 1);
                player.getInventory().setItem(action.slot(), stack.getAmount() <= 0 ? null : stack);
            } else {
                player.sendActionBar(Messages.error(player, "Nur beschriebene Platten passen hinein, und das Inventar darf nicht voll sein.",
                        "Only written records fit, and the inventory must not be full."));
            }
        } else {
            ItemStack taken = playlist.take(block, action.slot());
            if (taken != null) player.getInventory().addItem(taken).values().forEach(rest -> player.getWorld().dropItem(player.getLocation(), rest));
        }
        sendPlaylist(player, block);
    }

    /** Lautsprecher-Uebersicht fuer den Reiter "Lautsprecher" im Jukebox-Menue. */
    void onSpeakerOverviewRequest(Player player, Packet.SpeakerOverviewRequest request) {
        Block block = validated(player, request.x(), request.y(), request.z());
        if (block == null) return;
        List<Packet.SpeakerEntry> entries = new ArrayList<>();
        for (SpeakerService.Speaker sp : speakers.speakersOfJukebox(BlockRegistry.Key.of(block))) {
            if (sp.number() > 0) entries.add(new Packet.SpeakerEntry(sp.number(), sp.settings().name(), sp.settings().channel(), sp.settings().volume()));
        }
        Net.send(plugin, player, new Packet.SpeakerOverview(block.getX(), block.getY(), block.getZ(), entries));
    }

    private void sendPlaylist(Player player, Block block) {
        List<PlaylistService.Track> tracks = playlist.tracks(block);
        List<Packet.PlaylistEntry> entries = new ArrayList<>(tracks.size());
        for (PlaylistService.Track t : tracks) {
            String title = cut(t.song() != null ? t.song().title() : t.written().title());
            String artist = cut(t.song() != null ? t.song().artist() : t.written().artist());
            entries.add(new Packet.PlaylistEntry(title, artist, t.song() != null ? t.song().durationMs() : 0, t.playable(), t.slot()));
        }
        var state = playback.state(block);
        Net.send(plugin, player, new Packet.PlaylistState(block.getX(), block.getY(), block.getZ(),
                playback.currentIndex(block, tracks), state.isPresent() && state.get().playing, entries));
    }

    private Block validated(Player player, int x, int y, int z) {
        return access.validated(player, x, y, z, PeakBlockType.JUKEBOX);
    }

    private static String cut(String s) {
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

}
