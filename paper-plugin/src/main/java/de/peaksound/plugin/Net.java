package de.peaksound.plugin;

import de.peaksound.protocol.Packet;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Kleine Hilfe zum Senden von Paketen. Der {@link NetworkHandler} entscheidet je Spieler, ob das Paket ueber den Kanal von PeakCore
 * (neue Mod) oder ueber den alten Kanal {@code peaksound:main} (Alpha-Mod) geht.
 */
final class Net {
    private static volatile NetworkHandler handler;

    private Net() {}

    static void setHandler(NetworkHandler networkHandler) {
        handler = networkHandler;
    }

    /** Nur auf dem Haupt-Thread aufrufen. */
    static void send(Plugin plugin, Player player, Packet.Clientbound packet) {
        NetworkHandler h = handler;
        if (h == null) throw new IllegalStateException("Netzwerk noch nicht bereit");
        h.send(player, packet);
    }
}
