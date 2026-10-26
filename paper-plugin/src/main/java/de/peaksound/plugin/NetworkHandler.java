package de.peaksound.plugin;

import de.peaksound.protocol.MalformedPacketException;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PacketCodec;
import de.peaksound.protocol.PeakIds;
import de.peaksound.protocol.Limits;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Zentrale Anlaufstelle fuer alle Client-Pakete: Handshake (Versionspruefung, Operator-Status) und
 * Weiterleitung an Upload- und Streaming-Dienst. Spieler ohne Mod bekommen nur einen Chat-Hinweis.
 */
final class NetworkHandler implements PluginMessageListener, Listener {
    private final PeakSoundPlugin plugin;
    private final PeakRecipes recipes;
    private final Set<UUID> modded = ConcurrentHashMap.newKeySet();
    private UploadService uploads;
    private SongStreamService streams;
    private PlaybackManager playback;
    private JukeboxMenuService menu;
    private SpeakerMenuService speakerMenu;
    private EditAccess access;

    NetworkHandler(PeakSoundPlugin plugin, PeakRecipes recipes) {
        this.plugin = plugin;
        this.recipes = recipes;
    }

    /** Verbindet die Dienste (erst nach deren Erzeugung moeglich, da sie das NetworkHandler brauchen). */
    void wire(UploadService uploads, SongStreamService streams, PlaybackManager playback, JukeboxMenuService menu,
              SpeakerMenuService speakerMenu, EditAccess access) {
        this.uploads = uploads;
        this.streams = streams;
        this.playback = playback;
        this.menu = menu;
        this.speakerMenu = speakerMenu;
        this.access = access;
    }

    boolean hasMod(Player player) {
        return modded.contains(player.getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(PeakIds.CHANNEL)) return;
        Packet.Serverbound packet;
        try {
            packet = PacketCodec.decodeServerbound(message);
        } catch (MalformedPacketException e) {
            plugin.getLogger().fine("Ungueltiges Paket von " + player.getName() + ": " + e.getMessage());
            return; // manipulierte Pakete werden still ignoriert
        }
        if (packet instanceof Packet.Hello hello) {
            onHello(player, hello);
            return;
        }
        if (!hasMod(player)) return; // alles andere nur nach erfolgreichem Handshake
        switch (packet) {
            case Packet.UploadStart start -> uploads.onStart(player, start);
            case Packet.UploadChunk chunk -> uploads.onChunk(player, chunk);
            case Packet.SongRequest request -> streams.onRequest(player, request);
            case Packet.CoverRequest request -> streams.onCover(player, request);
            case Packet.JukeboxUpdate update -> menu.onUpdate(player, update);
            case Packet.PlaylistRequest request -> menu.onPlaylistRequest(player, request);
            case Packet.PlaylistAction action -> menu.onPlaylistAction(player, action);
            case Packet.SpeakerUpdate update -> speakerMenu.onUpdate(player, update);
            case Packet.SpeakerOverviewRequest request -> menu.onSpeakerOverviewRequest(player, request);
            case Packet.Hello ignored -> { /* oben behandelt */ }
        }
    }

    private void onHello(Player player, Packet.Hello hello) {
        boolean compatible = hello.protocolVersion() == PeakIds.PROTOCOL_VERSION;
        if (compatible) {
            modded.add(player.getUniqueId());
            playback.forget(player); // Client startet leer: laufende Jukeboxen erneut melden
        } else {
            player.sendMessage(Messages.versionMismatch(player, PeakIds.PROTOCOL_VERSION, hello.protocolVersion()));
        }
        Net.send(plugin, player, new Packet.HelloAck(PeakIds.PROTOCOL_VERSION, compatible,
                compatible && player.isOp(), System.currentTimeMillis(), Limits.MAX_SONG_SECONDS));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        player.discoverRecipes(recipes.keys()); // Rezepte im Rezeptbuch sichtbar machen
        if (!plugin.getConfig().getBoolean("non-mod-hint.enabled", true)) return;
        long delay = Math.max(1, plugin.getConfig().getLong("non-mod-hint.delay-ticks", 100));
        player.getScheduler().runDelayed(plugin, task -> {
            if (!hasMod(player)) player.sendMessage(Messages.nonModHint(player));
        }, null, delay);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        playback.forget(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        modded.remove(player.getUniqueId());
        uploads.onQuit(player);
        streams.onQuit(player);
        playback.onQuit(player);
        access.onQuit(player);
    }
}
