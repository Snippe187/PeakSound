package de.peaksound.plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Auf welchem Weg spricht die Mod eines Spielers mit dem Plugin? Die alte (Alpha-)Mod nutzt den eigenen Kanal {@code peaksound:main},
 * die neue Mod den Kanal von PeakCore. Der Weg steht fest, sobald das PeakSound-Hello eintrifft, und gilt, bis der Spieler den Server verlaesst.
 */
final class Transports {
    enum Kind { LEGACY, CORE }

    /** Was mit einem Paket einer bestimmten Groesse geschehen soll. */
    enum Route { LEGACY, CORE, TOO_BIG, NO_ROUTE }

    private final Map<UUID, Kind> kinds = new ConcurrentHashMap<>();

    void set(UUID player, Kind kind) {
        kinds.put(player, kind);
    }

    /** Der Weg des Spielers; {@code null}, solange er sich nicht gemeldet hat. */
    Kind get(UUID player) {
        return kinds.get(player);
    }

    void remove(UUID player) {
        kinds.remove(player);
    }

    /**
     * Entscheidet den Sendeweg. Spieler ohne bekannten Weg bekommen nichts ({@link Route#NO_ROUTE}); ist das Paket groesser als der Weg erlaubt,
     * ist es {@link Route#TOO_BIG} (der Core-Weg hat wegen des Umschlags etwas weniger Platz).
     */
    static Route route(Kind kind, int size, int legacyMax, int coreMax) {
        if (kind == null) return Route.NO_ROUTE;
        return switch (kind) {
            case LEGACY -> size > legacyMax ? Route.TOO_BIG : Route.LEGACY;
            case CORE -> size > coreMax ? Route.TOO_BIG : Route.CORE;
        };
    }
}
