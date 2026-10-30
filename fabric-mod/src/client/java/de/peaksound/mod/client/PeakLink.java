package de.peaksound.mod.client;

import de.peakverse.core.client.ClientSession;
import de.peakverse.core.mod.PeakCoreSession;
import de.peaksound.mod.PeakSoundMod;
import de.peaksound.mod.RawPayload;
import de.peaksound.mod.RouteDecision;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.PeakIds;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Die Verbindung der Mod zum Plugin: meldet PeakSound bei der Core-Mod als Modul an und waehlt nach dem Beitritt den Weg
 * (PeakCore oder alter Kanal), siehe {@link RouteDecision}. Das PeakSound-Hello geht erst raus, wenn der Weg feststeht.
 */
final class PeakLink {
    private static volatile RouteDecision.Route route = RouteDecision.Route.UNDECIDED;
    private static volatile long joinAt;
    private static volatile ClientSession.ModuleChannel channel;

    private PeakLink() {}

    /** Meldet das Modul "peaksound" bei der Core-Mod an (beim Start der Mod, damit es im Hello an PeakCore steht). */
    static void register() {
        // whenReady: die Core-Mod startet nicht zwingend vor uns; die Anmeldung wird sonst nachgeholt, sobald die Sitzung steht
        PeakCoreSession.whenReady(session -> channel = session.registerModule(PeakIds.NAMESPACE, PeakSoundMod.VERSION, ClientNetwork::onCorePayload));
    }

    static RouteDecision.Route route() {
        return route;
    }

    /** Beitritt: der Weg wird neu gewaehlt. */
    static void onJoin() {
        route = RouteDecision.Route.UNDECIDED;
        joinAt = System.currentTimeMillis();
    }

    static void onDisconnect() {
        route = RouteDecision.Route.UNDECIDED;
    }

    /** Einmal je Client-Tick: Weg waehlen, dann das Hello senden und bei ausbleibender Antwort wiederholen. */
    static void tick() {
        if (route == RouteDecision.Route.UNDECIDED) {
            ClientSession session = PeakCoreSession.get();
            RouteDecision.Route decided = RouteDecision.decide(session.state(), session.serverHasModule(PeakIds.NAMESPACE),
                    ClientPlayNetworking.canSend(de.peakverse.core.mod.RawPayload.TYPE), ClientPlayNetworking.canSend(RawPayload.TYPE),
                    System.currentTimeMillis() - joinAt);
            if (decided == RouteDecision.Route.UNDECIDED) return;
            route = decided;
            if (decided == RouteDecision.Route.NONE) return;
            sendHello();
        } else if ((route == RouteDecision.Route.CORE || route == RouteDecision.Route.LEGACY) && ServerState.shouldRetryHello()) {
            sendHello();
        }
    }

    private static void sendHello() {
        ServerState.onHelloSent();
        ClientNetwork.send(new Packet.Hello(PeakIds.PROTOCOL_VERSION, PeakSoundMod.VERSION));
    }

    /** Sendet Nutzdaten ueber PeakCore. {@code false}, wenn dort kein Handshake besteht oder das Paket zu gross ist. */
    static boolean sendViaCore(byte[] payload) {
        ClientSession.ModuleChannel c = channel;
        return c != null && payload.length <= c.maxPayload() && c.send(payload);
    }

    static boolean fitsCore(byte[] payload) {
        ClientSession.ModuleChannel c = channel;
        return c != null && payload.length <= c.maxPayload();
    }

    static String debug() {
        ClientSession s = PeakCoreSession.get();
        return "  Weg zum Plugin: " + route + ", PeakCore-Zustand: " + s.state() + ", Server hat Modul peaksound: " + s.serverHasModule(PeakIds.NAMESPACE)
                + ", Kanal PeakCore bekannt: " + ClientPlayNetworking.canSend(de.peakverse.core.mod.RawPayload.TYPE)
                + ", alter Kanal bekannt: " + ClientPlayNetworking.canSend(RawPayload.TYPE) + "\n";
    }
}
