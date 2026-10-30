package de.peaksound.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransportsTest {
    private static final int LEGACY_MAX = 1024 * 1024 - 1;
    private static final int CORE_MAX = LEGACY_MAX - 11; // Umschlag von PeakCore bei der ID "peaksound"

    @Test
    void remembersThePathPerPlayerUntilTheyLeave() {
        Transports t = new Transports();
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        assertNull(t.get(alice));
        t.set(alice, Transports.Kind.LEGACY);
        t.set(bob, Transports.Kind.CORE);
        assertEquals(Transports.Kind.LEGACY, t.get(alice));
        assertEquals(Transports.Kind.CORE, t.get(bob));
        t.remove(alice);
        assertNull(t.get(alice));
        assertEquals(Transports.Kind.CORE, t.get(bob));
    }

    @Test
    void aNewHelloMayChangeThePath() {
        Transports t = new Transports();
        UUID id = UUID.randomUUID();
        t.set(id, Transports.Kind.LEGACY);
        t.set(id, Transports.Kind.CORE); // Spieler hat die Mod gewechselt und sich neu verbunden
        assertEquals(Transports.Kind.CORE, t.get(id));
    }

    @Test
    void routesBySenderPath() {
        assertEquals(Transports.Route.LEGACY, Transports.route(Transports.Kind.LEGACY, 100, LEGACY_MAX, CORE_MAX));
        assertEquals(Transports.Route.CORE, Transports.route(Transports.Kind.CORE, 100, LEGACY_MAX, CORE_MAX));
        assertEquals(Transports.Route.NO_ROUTE, Transports.route(null, 100, LEGACY_MAX, CORE_MAX), "ohne Hello keine Mod, nichts senden");
    }

    @Test
    void honoursTheSizeLimitOfEachPath() {
        assertEquals(Transports.Route.LEGACY, Transports.route(Transports.Kind.LEGACY, LEGACY_MAX, LEGACY_MAX, CORE_MAX));
        assertEquals(Transports.Route.TOO_BIG, Transports.route(Transports.Kind.LEGACY, LEGACY_MAX + 1, LEGACY_MAX, CORE_MAX));
        assertEquals(Transports.Route.CORE, Transports.route(Transports.Kind.CORE, CORE_MAX, LEGACY_MAX, CORE_MAX));
        assertEquals(Transports.Route.TOO_BIG, Transports.route(Transports.Kind.CORE, CORE_MAX + 1, LEGACY_MAX, CORE_MAX),
                "ein Paket, das alt noch passt, kann ueber den Core wegen des Umschlags zu gross sein");
        assertEquals(Transports.Route.LEGACY, Transports.route(Transports.Kind.LEGACY, CORE_MAX + 1, LEGACY_MAX, CORE_MAX), "der alte Weg bleibt unbeeintraechtigt");
    }

    @Test
    void withoutPeakCoreOnlyTheLegacyPathWorks() {
        assertEquals(Transports.Route.TOO_BIG, Transports.route(Transports.Kind.CORE, 1, LEGACY_MAX, 0));
        assertEquals(Transports.Route.LEGACY, Transports.route(Transports.Kind.LEGACY, 1, LEGACY_MAX, 0));
    }
}
