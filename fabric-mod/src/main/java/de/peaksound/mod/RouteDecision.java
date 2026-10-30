package de.peaksound.mod;

import de.peakverse.core.client.ClientSession;

/**
 * Entscheidet, auf welchem Weg die Mod mit dem Plugin spricht: ueber PeakCore (neues Plugin) oder ueber den alten Kanal {@code peaksound:main}
 * (Server, auf dem noch das Alpha-Plugin laeuft, oder PeakCore-Handshake fehlgeschlagen). Reine Logik ohne Minecraft, damit sie sich testen laesst.
 *
 * <p>Der Server meldet dem Client alle seine Kanaele auf einmal kurz nach dem Beitritt. Kennt der Client bis dahin weder den Kanal von PeakCore
 * noch den alten, wartet er (hoechstens {@link #WAIT_MS}).
 */
public final class RouteDecision {
    /** Der Weg zum Plugin. */
    public enum Route {
        /** Noch nicht entschieden. */
        UNDECIDED,
        /** Ueber PeakCore (Modul "peaksound"). */
        CORE,
        /** Ueber den alten Kanal {@code peaksound:main}. */
        LEGACY,
        /** Kein Weg: der Server hat kein PeakSound. */
        NONE
    }

    /** So lange wartet die Mod nach dem Beitritt auf die Kanalliste des Servers, bevor sie aufgibt. */
    public static final long WAIT_MS = 12_000;

    private RouteDecision() {}

    /**
     * @param coreState             Stand des Handshakes mit PeakCore
     * @param serverHasPeakSound    meldet PeakCore das Modul "peaksound" auf dem Server? (nur bei {@code ACTIVE} aussagekraeftig)
     * @param canSendCoreChannel    kennt der Server den Kanal von PeakCore?
     * @param canSendLegacyChannel  kennt der Server den alten Kanal {@code peaksound:main}?
     * @param waitedMs              Zeit seit dem Beitritt
     */
    public static Route decide(ClientSession.State coreState, boolean serverHasPeakSound, boolean canSendCoreChannel, boolean canSendLegacyChannel, long waitedMs) {
        switch (coreState) {
            case ACTIVE -> {
                if (serverHasPeakSound) return Route.CORE;
                return canSendLegacyChannel ? Route.LEGACY : Route.NONE; // PeakCore ohne PeakSound-Modul
            }
            case INCOMPATIBLE, NO_SERVER_SUPPORT -> {
                // PeakCore passt nicht oder fehlt: der alte Kanal geht, wenn das Plugin ihn noch bedient
                return canSendLegacyChannel ? Route.LEGACY : Route.NONE;
            }
            case WAITING -> {
                // Nur der alte Kanal bekannt: altes Plugin, sofort dorthin. Beide bekannt: erst den PeakCore-Handshake abwarten.
                if (!canSendCoreChannel && canSendLegacyChannel) return Route.LEGACY;
                if (!canSendCoreChannel && waitedMs >= WAIT_MS) return Route.NONE;
                return Route.UNDECIDED;
            }
            case OFFLINE -> {
                return Route.UNDECIDED;
            }
        }
        return Route.UNDECIDED;
    }
}
