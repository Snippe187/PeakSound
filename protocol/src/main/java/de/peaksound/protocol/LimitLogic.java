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

    /**
     * Songlimit aus Rechte-Nodes {@code peaksound.limit.<n>} (hoechster Wert gewinnt) und {@code peaksound.limit.unlimited}
     * (0 = unbegrenzt, gewinnt immer). -1, wenn der Spieler keinen solchen Node hat.
     */
    public static int permissionLimit(Iterable<String> grantedNodes) {
        int best = -1;
        for (String node : grantedNodes) {
            if (node == null || !node.startsWith("peaksound.limit.")) continue;
            String rest = node.substring("peaksound.limit.".length());
            if (rest.equals("unlimited")) return 0;
            try {
                int n = Integer.parseInt(rest);
                if (n > 0) best = Math.max(best, Math.min(n, MAX_SONG_LIMIT));
            } catch (NumberFormatException ignored) { /* kein Zahlen-Node */ }
        }
        return best;
    }

    /** Das geltende Songlimit: Wert des Operators, sonst Rechte-Node, sonst Standard. */
    public static int effectiveSongLimit(Integer playerOverride, int permissionLimit, int defaultLimit) {
        if (playerOverride != null && playerOverride >= 0) return clampSongLimit(playerOverride);
        return permissionLimit >= 0 ? clampSongLimit(permissionLimit) : clampSongLimit(defaultLimit);
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
