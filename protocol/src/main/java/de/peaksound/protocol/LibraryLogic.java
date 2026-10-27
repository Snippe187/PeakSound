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

    /** Sortierung der Trefferliste. */
    public enum Sort {
        TITLE, ARTIST, NEWEST, LONGEST, POPULAR;

        public static Sort byOrdinal(int o) {
            Sort[] v = values();
            if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannte Sortierung");
            return v[o];
        }

        public Sort next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** Welche Songs die Liste zeigt. */
    public enum Filter {
        ALL, FAVORITES, MINE;

        public static Filter byOrdinal(int o) {
            Filter[] v = values();
            if (o < 0 || o >= v.length) throw new MalformedPacketException("Unbekannter Filter");
            return v[o];
        }

        public Filter next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /** Die fuer Sortierung noetigen Felder eines Songs. */
    public record Item(String title, String artist, int durationMs, long uploadedAt, int plays) {}

    /** Sortierreihenfolge; bei Gleichstand immer nach Titel und dann nach Hochladezeit, damit die Liste stabil bleibt. */
    public static java.util.Comparator<Item> comparator(Sort sort) {
        java.util.Comparator<Item> byTitle = java.util.Comparator.comparing((Item i) -> i.title().toLowerCase(Locale.ROOT))
                .thenComparingLong(Item::uploadedAt);
        return switch (sort) {
            case TITLE -> byTitle;
            case ARTIST -> java.util.Comparator.comparing((Item i) -> i.artist().toLowerCase(Locale.ROOT)).thenComparing(byTitle);
            case NEWEST -> java.util.Comparator.comparingLong(Item::uploadedAt).reversed().thenComparing(byTitle);
            case LONGEST -> java.util.Comparator.comparingInt(Item::durationMs).reversed().thenComparing(byTitle);
            case POPULAR -> java.util.Comparator.comparingInt(Item::plays).reversed().thenComparing(byTitle);
        };
    }

    /** Darf dieser Spieler den Song umbenennen oder loeschen? Eigene Songs immer, fremde nur Operatoren. */
    public static boolean mayManage(boolean isUploader, boolean isAdmin) {
        return isUploader || isAdmin;
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