package de.peaksound.protocol;

import java.util.Optional;

/** Alle PeakSound-Bloecke. Serverseitig sind es Vanilla-Traegerbloecke, markiert durch das Plugin. */
public enum PeakBlockType {
    JUKEBOX("jukebox", "JUKEBOX"),
    SPEAKER("speaker", "NOTE_BLOCK");

    private final String id;
    private final String carrier;

    PeakBlockType(String id, String carrier) {
        this.id = id;
        this.carrier = carrier;
    }

    public String id() { return id; }

    public String carrierMaterial() { return carrier; }

    public static Optional<PeakBlockType> byId(String id) {
        for (PeakBlockType t : values()) {
            if (t.id.equals(id)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
