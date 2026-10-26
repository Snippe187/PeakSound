package de.peaksound.protocol;

/** Zentrale Namen, die Plugin und Mod gemeinsam benutzen. Keine Minecraft-Abhaengigkeit. */
public final class PeakIds {
    private PeakIds() {}

    public static final String NAMESPACE = "peaksound";
    /** Plugin-Messaging-Kanal fuer alle PeakSound-Pakete. */
    public static final String CHANNEL = NAMESPACE + ":main";
    /** Version des Netzwerkprotokolls. Bei inkompatiblen Aenderungen erhoehen. */
    public static final int PROTOCOL_VERSION = 1;
    /** Schluessel (PDC / custom_data) der den PeakSound-Typ eines Items oder Blocks speichert. */
    public static final String TYPE_KEY = "type";
}
