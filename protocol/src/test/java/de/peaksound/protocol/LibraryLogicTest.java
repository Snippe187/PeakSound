package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LibraryLogicTest {
    @Test
    void searchNeedsEveryWordAndIgnoresCase() {
        assertTrue(LibraryLogic.matches("", "Testbeat", "PeakSound", "Neo"));
        assertTrue(LibraryLogic.matches("test", "Testbeat", "PeakSound", "Neo"));
        assertTrue(LibraryLogic.matches("PEAK beat", "Testbeat", "PeakSound", "Neo"));
        assertTrue(LibraryLogic.matches("neo", "Testbeat", "PeakSound", "Neo"));
        assertFalse(LibraryLogic.matches("test jazz", "Testbeat", "PeakSound", "Neo"));
        assertTrue(LibraryLogic.matches(null, "x", "y", "z"));
    }

    @Test
    void pagesAndSlices() {
        List<Integer> all = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13);
        assertEquals(3, LibraryLogic.pages(13, 6));
        assertEquals(1, LibraryLogic.pages(0, 6));
        assertEquals(List.of(7, 8, 9, 10, 11, 12), LibraryLogic.slice(all, 1, 6));
        assertEquals(List.of(13), LibraryLogic.slice(all, 99, 6)); // zu grosse Seite -> letzte
        assertEquals(List.of(1, 2, 3, 4, 5, 6), LibraryLogic.slice(all, -3, 6));
        assertEquals(List.of(), LibraryLogic.slice(List.<Integer>of(), 0, 6));
    }
}