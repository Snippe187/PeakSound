package de.peaksound.plugin;

import org.bukkit.plugin.java.JavaPlugin;
import de.peakverse.core.api.ModuleInfo;
import de.peakverse.core.api.ModuleRegistration;
import de.peakverse.core.api.PeakCoreApi;
import org.bukkit.entity.Player;

/**
 * Die Verbindung zu PeakCore: PeakSound meldet sich dort als Modul "peaksound" an und bekommt darueber die Pakete der neuen Mod
 * (Nutzdaten sind dieselben PeakSound-Pakete wie auf dem alten Kanal). Die alte Mod nutzt weiter den eigenen Kanal {@code peaksound:main}.
 */
final class CoreLink {
    static final String MODULE_ID = "peaksound";

    private final ModuleRegistration registration;

    private CoreLink(ModuleRegistration registration) {
        this.registration = registration;
    }

    /** Meldet PeakSound als Modul an; Pakete der neuen Mod gehen an {@code network}. */
    static CoreLink register(JavaPlugin plugin, NetworkHandler network) {
        PeakCoreApi core = PeakCoreApi.get().orElseThrow(() -> new IllegalStateException("PeakCore nicht gefunden (Plugin PeakCore muss installiert sein)"));
        ModuleRegistration registration = core.registerModule(plugin, new ModuleInfo(MODULE_ID, "PeakSound", Ctx.version(), 1));
        registration.setMessageHandler(network::onCoreMessage);
        return new CoreLink(registration);
    }

    /** Sendet ein PeakSound-Paket ueber den Core-Kanal. {@code false}, wenn der Spieler keinen Handshake mit PeakCore hat. */
    boolean send(Player player, byte[] data) {
        return registration.send(player, data);
    }

    /** Groesste Paketgroesse zum Client auf diesem Weg (etwas kleiner als beim alten Kanal wegen des Umschlags). */
    int maxPayloadToClient() {
        return registration.maxPayloadToClient();
    }

    /** Meldet PeakCore, dass dieser Spieler die alte Mod nutzt (kein Hinweis "keine Mod gefunden"). */
    void markLegacyClient(Player player) {
        registration.markCompatibleClient(player);
    }

    void unregister() {
        registration.unregister();
    }
}
