package de.peaksound.mod.client;

import de.peaksound.protocol.SyncMath;

/** Was der Client ueber den aktuellen Server weiss. Wird beim Verbinden zurueckgesetzt. */
public final class ServerState {
    private static volatile boolean pluginPresent;
    private static volatile boolean compatible;
    private static volatile boolean operator;
    private static volatile long clockOffsetMs;
    private static volatile int maxSongSeconds = 240;
    private static volatile long helloSentAt;
    private static volatile long lastAckAt;
    private static volatile int ackCount;

    private ServerState() {}

    public static void reset() {
        pluginPresent = false;
        compatible = false;
        operator = false;
        clockOffsetMs = 0;
        helloSentAt = 0;
        helloAttempts = 0;
    }

    /** Merkt sich den Sendezeitpunkt des Hello, um die Uhrzeit des Servers abzuschaetzen. */
    public static void onHelloSent() {
        helloSentAt = System.currentTimeMillis();
        helloAttempts++;
    }

    private static volatile int helloAttempts;

    /** Das Hello wiederholen, solange keine Antwort da ist (hoechstens 10 Versuche im Abstand von 2 s). */
    static boolean shouldRetryHello() {
        return !pluginPresent && helloAttempts > 0 && helloAttempts < 10 && System.currentTimeMillis() - helloSentAt > 2000;
    }

    /** Der Server meldet eine Aenderung des Operator-Status (z. B. nach /op). */
    public static void setOperator(boolean value) { operator = pluginPresent && compatible && value; }

    public static void onAck(boolean isCompatible, boolean isOperator, long serverTimeMs, int songSeconds) {
        pluginPresent = true;
        lastAckAt = System.currentTimeMillis();
        ackCount++;
        compatible = isCompatible;
        operator = isCompatible && isOperator;
        maxSongSeconds = songSeconds;
        // Der Server hat serverTimeMs etwa in der Mitte der Rundreise gestempelt
        clockOffsetMs = SyncMath.clockOffsetMs(helloSentAt, System.currentTimeMillis(), serverTimeMs);
    }

    /** Der Server hat das PeakSound-Plugin und die Protokollversion passt. */
    public static boolean active() { return pluginPresent && compatible; }

    /** Der Server hat das Plugin, aber Mod und Plugin passen nicht zusammen. */
    public static boolean versionMismatch() { return pluginPresent && !compatible; }

    /** Server hat den Spieler als Operator gemeldet (Anzeige des Song-Verwaltungs-Buttons). */
    public static boolean operator() { return operator; }

    /** Diagnose: alle Felder in einer Zeile. */
    public static String debug() {
        return "  pluginPresent=" + pluginPresent + " compatible=" + compatible + " operator=" + operator + " Uhrversatz=" + clockOffsetMs + " ms maxSongSekunden=" + maxSongSeconds
                + " HelloGesendetVor=" + (helloSentAt == 0 ? "nie" : (System.currentTimeMillis() - helloSentAt) / 1000 + " s")
                + " HelloAckVor=" + (lastAckAt == 0 ? "nie" : (System.currentTimeMillis() - lastAckAt) / 1000 + " s") + " HelloVersuche=" + helloAttempts + " Acks=" + ackCount + "\n";
    }

    public static int maxSongSeconds() { return maxSongSeconds; }

    /** Geschaetzte aktuelle Serverzeit in Millisekunden (fuer die synchrone Wiedergabe). */
    public static long serverNow() { return System.currentTimeMillis() + clockOffsetMs; }
}
