package de.peaksound.mod.client;

/** Was der Client ueber den aktuellen Server weiss. Wird beim Verbinden zurueckgesetzt. */
public final class ServerState {
    private static volatile boolean pluginPresent;
    private static volatile boolean compatible;
    private static volatile boolean operator;

    private ServerState() {}

    public static void reset() {
        pluginPresent = false;
        compatible = false;
        operator = false;
    }

    public static void onAck(boolean isCompatible, boolean isOperator) {
        pluginPresent = true;
        compatible = isCompatible;
        operator = isCompatible && isOperator;
    }

    /** Der Server hat das PeakSound-Plugin und die Protokollversion passt. */
    public static boolean active() { return pluginPresent && compatible; }

    /** Der Server hat das Plugin, aber Mod und Plugin passen nicht zusammen. */
    public static boolean versionMismatch() { return pluginPresent && !compatible; }

    /** Server hat den Spieler als Operator gemeldet (Anzeige des Song-Verwaltungs-Buttons). */
    public static boolean operator() { return operator; }
}
