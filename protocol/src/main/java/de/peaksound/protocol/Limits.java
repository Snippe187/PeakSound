package de.peaksound.protocol;

/** Feste Protokoll-Grenzen. Konfigurierbare Limits (Songzahl, Speicher, ...) liegen in der Plugin-Config. */
public final class Limits {
    private Limits() {}

    /** Maximale Songlaenge in Sekunden (4 Minuten). */
    public static final int MAX_SONG_SECONDS = 240;
    /** Groesse eines Upload-Chunks (Client -> Server darf je Payload 32 KiB). */
    public static final int UPLOAD_CHUNK_BYTES = 16 * 1024;
    /** Groesse eines Download-Chunks (Server -> Client darf je Payload ca. 1 MiB). */
    public static final int DOWNLOAD_CHUNK_BYTES = 64 * 1024;
    /** Wie viele Upload-Chunks der Client ohne Bestaetigung senden darf. */
    public static final int UPLOAD_WINDOW = 16;
    /** Obergrenze fuer die Dateigroesse eines Uploads (Rohdatei, vor der Kompression). */
    public static final int MAX_UPLOAD_BYTES = 64 * 1024 * 1024;
    /** Texte (Titel, Interpret, Namen). */
    public static final int MAX_TEXT = 64;
    /** Maximale Cover-Groesse in Bytes. */
    public static final int MAX_COVER_BYTES = 512 * 1024;
    /** Laengster Songtext (Zeichen); passt auch mit 3-Byte-Zeichen in ein Paket-String-Feld. */
    public static final int MAX_LYRICS_CHARS = 20_000;
    /** Laengster Statistik-Text fuer den Admin-Tab. */
    public static final int MAX_STATS_CHARS = 4_000;
    /** Maximale Groesse der Musikanalyse, die an Clients geht (Scheinwerfer-Effekte). */
    public static final int MAX_ANALYSIS_BYTES = 128 * 1024;
    /** Hoechste Groesse des Diagnosetexts (UTF-8) vom Server. */
    public static final int MAX_DEBUG_BYTES = 60_000;
    /** Song-Bibliothek: Eintraege pro Seite und laengster Suchtext. */
    public static final int LIBRARY_PAGE_SIZE = 6;
    /** Marker-Koordinate fuer Bibliotheks-Anfragen ohne Block (ueber ein Upload-Ticket, statt am Song-Library-Block). */
    public static final int LIBRARY_REMOTE = Integer.MIN_VALUE;
    /** Marker-Koordinate fuer Quellen, die dem Spieler folgen (Taschenradio): ausserhalb der Weltgrenze und ohne Kollision mit echten Bloecken. */
    public static final int POCKET_COORD = 33_554_432;
    public static final int LIBRARY_MAX_QUERY = 32;
    /** Laenge eines SHA-256-Hex-Strings. */
    public static final int HASH_LENGTH = 64;

    /** Jukebox-Reichweite in Bloecken. */
    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 30;

    public static int chunkCount(long totalBytes, int chunkSize) {
        if (totalBytes < 0 || chunkSize <= 0) throw new IllegalArgumentException();
        return (int) ((totalBytes + chunkSize - 1) / chunkSize);
    }
}
