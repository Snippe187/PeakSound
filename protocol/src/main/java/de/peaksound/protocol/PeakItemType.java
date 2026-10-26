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
    PEAK_TUNER("peak_tuner", "BREEZE_ROD", null),
    JUKEBOX("jukebox", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.JUKEBOX),
    SPEAKER("speaker", PeakBlockType.CARRIER_MATERIAL, PeakBlockType.SPEAKER);

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

    public static Optional<PeakItemType> byId(String id) {
        for (PeakItemType t : values()) {
            if (t.id.equals(id)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
