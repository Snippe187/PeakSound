package de.peaksound.protocol;

import java.util.List;
import java.util.Locale;

/** Suche und Seitenaufteilung der Song-Bibliothek (ohne Minecraft, mit Unit-Tests). */
public final class LibraryLogic {
    private LibraryLogic() {}

    /** Passt der Eintrag zur Suche? Jedes Wort der Suche muss in Titel, Interpret oder Hochlader vorkommen (Gross/Klein egal). */
    public static boolean matches(String query, String title, String artist, String uploader) {
        if (query == null || query.isBlank()) return true;
        String haystack = (title + " " + artist + " " + uploader).toLowerCase(Locale.ROOT);
        for (String term : query.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
            if (!haystack.contains(term)) return false;
        }
        return true;
    }

    /** Anzahl der Seiten (mindestens 1). */
    public static int pages(int total, int pageSize) {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /** Die Seite {@code page} (0-basiert, wird in den gueltigen Bereich geschoben). */
    public static <T> List<T> slice(List<T> all, int page, int pageSize) {
        int p = clampPage(page, all.size(), pageSize);
        int from = p * pageSize;
        return all.subList(Math.min(from, all.size()), Math.min(from + pageSize, all.size()));
    }

    public static int clampPage(int page, int total, int pageSize) {
        return Math.max(0, Math.min(page, pages(total, pageSize) - 1));
    }
}