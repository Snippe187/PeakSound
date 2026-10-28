package de.peaksound.protocol;

import java.util.Optional;

/**
 * Alle PeakSound-Items. {@code carrier} ist das Vanilla-Item, das Spieler ohne Mod sehen;
 * {@code block} ist gesetzt, wenn das Item als Block platziert wird. Die Mod erkennt das Item
 * am custom_model_data-String {@link #id()} und zeigt das eigene Modell.
 */
public enum PeakItemType {
    BLANK_RECORD("blank_record", "DISC_FRAGMENT_5", null),
    WRITTEN_RECORD("written_record", "DISC_FRAGMENT_5", null),
    // Traegeritem ohne Crafting-Verwendung (kein Windladungs-Rezept wie beim Breeze-Stab)
    PEAK_TUNER("peak_tuner", "TRIAL_KEY", null),
    JUKEBOX("jukebox", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.JUKEBOX),
    SPEAKER("speaker", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.SPEAKER),
    DJ_DESK("dj_desk", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.DJ_DESK),
    SPOTLIGHT("spotlight", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.SPOTLIGHT),
    MICROPHONE("microphone", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.MICROPHONE),
    SONG_LIBRARY("song_library", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.SONG_LIBRARY),
    MUSIC_REMOTE("music_remote", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.MUSIC_REMOTE),
    // Taschenradio: tragbarer Empfaenger, weltweit empfangbar
    POCKET_RADIO("pocket_radio", "TRIAL_KEY", null),
    // Radios sind Spielerkoepfe als Traeger: ohne Mod sieht man einen Kopf, mit Mod das Radio-Modell
    RADIO_TRANSMITTER("radio_transmitter", "PLAYER_HEAD", PeakBlockType.RADIO_TRANSMITTER),
    RADIO_RECEIVER("radio_receiver", "PLAYER_HEAD", PeakBlockType.RADIO_RECEIVER),
    DISPLAY("display", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.DISPLAY);

    private final String id;
    private final String carrier;
    private final PeakBlockType block;

    PeakItemType(String id, String carrier, PeakBlockType block) {
        this.id = id;
        this.carrier = carrier;
        this.block = block;
    }

    public String id() { return id; }

    /** Name des Vanilla-Materials (Enum-Name), damit das Modul ohne Minecraft auskommt. */
    public String carrierMaterial() { return carrier; }

    public Optional<PeakBlockType> block() { return Optional.ofNullable(block); }

    /** Das Item, das zu einem Block gehoert (was beim Abbauen faellt). */
    public static PeakItemType forBlock(PeakBlockType type) {
        for (PeakItemType t : values()) {
            if (t.block == type) return t;
        }
        throw new IllegalArgumentException(type.name());
    }

    public static Optional<PeakItemType> byId(String id) {
        for (PeakItemType t : values()) {
            if (t.id.equals(id)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
