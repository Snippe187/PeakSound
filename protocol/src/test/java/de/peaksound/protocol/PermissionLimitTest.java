package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionLimitTest {
    @Test
    void highestNumberNodeWins() {
        assertEquals(30, LimitLogic.permissionLimit(List.of("peaksound.limit.5", "peaksound.limit.30", "peaksound.upload")));
    }

    @Test
    void unlimitedBeatsNumbers() {
        assertEquals(0, LimitLogic.permissionLimit(List.of("peaksound.limit.100", "peaksound.limit.unlimited")));
    }

    @Test
    void noNodeMeansNone() {
        assertEquals(-1, LimitLogic.permissionLimit(List.of("peaksound.upload", "peaksound.limit.abc", "peaksound.limit.0")));
    }

    @Test
    void operatorOverrideBeatsNodeBeatsDefault() {
        assertEquals(7, LimitLogic.effectiveSongLimit(7, 30, 10));
        assertEquals(30, LimitLogic.effectiveSongLimit(null, 30, 10));
        assertEquals(10, LimitLogic.effectiveSongLimit(null, -1, 10));
        assertEquals(0, LimitLogic.effectiveSongLimit(null, 0, 10));
    }
}
