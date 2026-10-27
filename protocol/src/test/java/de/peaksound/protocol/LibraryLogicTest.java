package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class LibraryLogicTest {
    private static List<String> sorted(LibraryLogic.Sort sort) {
        var items = new java.util.ArrayList<>(List.of(
                new LibraryLogic.Item("banane", "Zed", 100_000, 30, 5),
                new LibraryLogic.Item("Apfel", "Abe", 300_000, 10, 1),
                new LibraryLogic.Item("Citrus", "Mia", 200_000, 20, 9)));
        items.sort(LibraryLogic.comparator(sort));
        return items.stream().map(LibraryLogic.Item::title).toList();
    }

    @Test
    void sortOrdersByEveryCriterion() {
        assertEquals(List.of("Apfel", "banane", "Citrus"), sorted(LibraryLogic.Sort.TITLE));
        assertEquals(List.of("Apfel", "Citrus", "banane"), sorted(LibraryLogic.Sort.ARTIST));
        assertEquals(List.of("banane", "Citrus", "Apfel"), sorted(LibraryLogic.Sort.NEWEST));
        assertEquals(List.of("Apfel", "Citrus", "banane"), sorted(LibraryLogic.Sort.LONGEST));
        assertEquals(List.of("Citrus", "banane", "Apfel"), sorted(LibraryLogic.Sort.POPULAR));
    }

    @Test
    void onlyOwnerOrAdminMayManage() {
        assertTrue(LibraryLogic.mayManage(true, false));
        assertTrue(LibraryLogic.mayManage(false, true));
        assertFalse(LibraryLogic.mayManage(false, false));
    }

    @Test
    void sortAndFilterCycleThroughAllValues() {
        assertEquals(LibraryLogic.Sort.TITLE, LibraryLogic.Sort.POPULAR.next());
        assertEquals(LibraryLogic.Filter.ALL, LibraryLogic.Filter.MINE.next());
    }

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