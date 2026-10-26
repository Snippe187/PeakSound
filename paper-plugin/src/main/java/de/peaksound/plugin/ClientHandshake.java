package de.peaksound.plugin;

import de.peaksound.protocol.Hello;
import de.peaksound.protocol.HelloAck;
import de.peaksound.protocol.MalformedPacketException;
import de.peaksound.protocol.PacketCodec;
import de.peaksound.protocol.PeakIds;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

/**
 * Handshake mit der Mod: prueft die Protokollversion, meldet den Operator-Status und
 * gibt Spielern ohne Mod nur einen Chat-Hinweis (kein Kick).
 */
final class ClientHandshake implements PluginMessageListener, Listener {
    private final PeakSoundPlugin plugin;
    private final PeakRecipes recipes;
    private final Set<UUID> modded = ConcurrentHashMap.newKeySet();

    ClientHandshake(PeakSoundPlugin plugin, PeakRecipes recipes) {
        this.plugin = plugin;
        this.recipes = recipes;
    }

    boolean hasMod(Player player) {
        return modded.contains(player.getUniqueId());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!channel.equals(PeakIds.CHANNEL)) return;
        Object packet;
        try {
            packet = PacketCodec.decode(message);
        } catch (MalformedPacketException e) {
            plugin.getLogger().fine("Ungueltiges Paket von " + player.getName() + ": " + e.getMessage());
            return; // manipulierte Pakete werden still ignoriert
        }
        if (packet instanceof Hello hello) {
            boolean compatible = hello.protocolVersion() == PeakIds.PROTOCOL_VERSION;
            if (compatible) modded.add(player.getUniqueId());
            else player.sendMessage(Messages.versionMismatch(player, PeakIds.PROTOCOL_VERSION, hello.protocolVersion()));
            player.sendPluginMessage(plugin, PeakIds.CHANNEL,
                    new HelloAck(PeakIds.PROTOCOL_VERSION, compatible, compatible && player.isOp()).encode());
        }
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
    public void onQuit(PlayerQuitEvent event) {
        modded.remove(event.getPlayer().getUniqueId());
    }
}
