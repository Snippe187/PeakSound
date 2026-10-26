package de.peaksound.protocol;

/**
 * Regeln fuer Upload-Limits. Alle Werte, die ein Operator einstellt, werden hier begrenzt und ausgewertet,
 * damit Server und Tests dieselbe Rechnung benutzen. {@code 0} bedeutet bei allen Limits "unbegrenzt".
 */
public final class LimitLogic {
    private LimitLogic() {}

    /** Obergrenze fuer die Songzahl, die ein Operator einstellen kann. */
    public static final int MAX_SONG_LIMIT = 100_000;
    /** Obergrenze fuer das Speicher-Limit in Megabyte (1 Petabyte, also praktisch unbegrenzt). */
    public static final long MAX_STORAGE_MB = 1_000_000_000L;
    /** Marker "kein eigener Wert, es gilt der Standard". */
    public static final int USE_DEFAULT = -1;

    /** Das fuer einen Spieler geltende Songlimit: sein eigener Wert, sonst der Standard. */
    public static int effectiveSongLimit(Integer playerOverride, int defaultLimit) {
        return playerOverride != null && playerOverride >= 0 ? clampSongLimit(playerOverride) : clampSongLimit(defaultLimit);
    }

    /** Darf der Spieler noch einen Song hochladen? {@code limit} 0 = unbegrenzt. */
    public static boolean mayUpload(int currentSongs, int limit) {
        return limit <= 0 || currentSongs < limit;
    }

    /** Passt eine weitere Datei in den Speicher? {@code maxBytes} 0 = unbegrenzt. */
    public static boolean fitsStorage(long usedBytes, long addBytes, long maxBytes) {
        return maxBytes <= 0 || usedBytes + addBytes <= maxBytes;
    }

    /** Eingabe eines Operators fuer ein Songlimit in den erlaubten Bereich bringen (negativ = 0 = unbegrenzt). */
    public static int clampSongLimit(long value) {
        return (int) Math.max(0, Math.min(MAX_SONG_LIMIT, value));
    }

    /** Eingabe eines Operators fuer das Speicher-Limit (MB) begrenzen. */
    public static long clampStorageMb(long value) {
        return Math.max(0, Math.min(MAX_STORAGE_MB, value));
    }

    public static long mbToBytes(long mb) {
        return clampStorageMb(mb) * 1024L * 1024L;
    }
}
