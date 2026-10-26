package de.peaksound.mod.client;

/** Was der Client ueber den aktuellen Server weiss. Wird beim Verbinden zurueckgesetzt. */
public final class ServerState {
    private static volatile boolean pluginPresent;
    private static volatile boolean compatible;
    private static volatile boolean operator;
    private static volatile long clockOffsetMs;
    private static volatile int maxSongSeconds = 240;
    private static volatile long helloSentAt;

    private ServerState() {}

    public static void reset() {
        pluginPresent = false;
        compatible = false;
        operator = false;
        clockOffsetMs = 0;
        helloSentAt = 0;
    }

    /** Merkt sich den Sendezeitpunkt des Hello, um die Uhrzeit des Servers abzuschaetzen. */
    public static void onHelloSent() { helloSentAt = System.currentTimeMillis(); }

    public static void onAck(boolean isCompatible, boolean isOperator, long serverTimeMs, int songSeconds) {
        pluginPresent = true;
        compatible = isCompatible;
        operator = isCompatible && isOperator;
        maxSongSeconds = songSeconds;
        // Der Server hat serverTimeMs etwa in der Mitte der Rundreise gestempelt
        long now = System.currentTimeMillis();
        long midpoint = helloSentAt > 0 ? (helloSentAt + now) / 2 : now;
        clockOffsetMs = serverTimeMs - midpoint;
    }

    /** Der Server hat das PeakSound-Plugin und die Protokollversion passt. */
    public static boolean active() { return pluginPresent && compatible; }

    /** Der Server hat das Plugin, aber Mod und Plugin passen nicht zusammen. */
    public static boolean versionMismatch() { return pluginPresent && !compatible; }

    /** Server hat den Spieler als Operator gemeldet (Anzeige des Song-Verwaltungs-Buttons). */
    public static boolean operator() { return operator; }

    public static int maxSongSeconds() { return maxSongSeconds; }

    /** Geschaetzte aktuelle Serverzeit in Millisekunden (fuer die synchrone Wiedergabe). */
    public static long serverNow() { return System.currentTimeMillis() + clockOffsetMs; }
}
