package de.peaksound.plugin;

import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PacketCodec;
import de.peaksound.protocol.PeakIds;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Kleine Hilfe zum Senden von Paketen ueber den Plugin-Messaging-Kanal. */
final class Net {
    private Net() {}

    /** Nur auf dem Haupt-Thread aufrufen. */
    static void send(Plugin plugin, Player player, Packet.Clientbound packet) {
        byte[] data = packet.encode();
        if (data.length > PacketCodec.MAX_CLIENTBOUND_SIZE) throw new IllegalStateException("Paket zu gross");
        player.sendPluginMessage(plugin, PeakIds.CHANNEL, data);
    }
}
