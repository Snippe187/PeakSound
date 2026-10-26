package de.peaksound.protocol;

import java.util.Optional;

/**
 * Alle PeakSound-Bloecke. Serverseitig sind es Notenbloecke in einem festen, im normalen Spiel
 * praktisch nie vorkommenden Zustand (Instrument + Ton + powered=true). Das Plugin setzt und
 * schuetzt diesen Zustand, die Mod ordnet ihm ein eigenes Modell zu. Spieler ohne Mod sehen
 * einen normalen Notenblock.
 */
public enum PeakBlockType {
    JUKEBOX("jukebox", "bit", 23),
    SPEAKER("speaker", "bit", 24),
    DJ_DESK("dj_desk", "bit", 21),
    SPOTLIGHT("spotlight", "bit", 22),
    MICROPHONE("microphone", "bit", 20),
    SONG_LIBRARY("song_library", "bit", 19),
    MUSIC_REMOTE("music_remote", "bit", 18);

    /** Vanilla-Block, der als Traeger dient. */
    public static final String CARRIER_MATERIAL = "NOTE_BLOCK";

    private final String id;
    private final String instrument;
    private final int note;

    PeakBlockType(String id, String instrument, int note) {
        this.id = id;
        this.instrument = instrument;
        this.note = note;
    }

    public String id() { return id; }

    /** Name des Notenblock-Instruments (Blockstate-Wert, z. B. "bit"). */
    public String instrument() { return instrument; }

    /** Tonhoehe 0-24 (Blockstate-Wert). */
    public int note() { return note; }

    /**
     * Verbindet dieser Block mehrere Redstone-Netze zu einem? Alle ausser dem DJ-Pult: Es haengt zwei getrennte
     * Netze (Deck A und Deck B) an sich, ohne sie zusammenzulegen, damit beide Decks unterschiedliche Songs spielen.
     */
    public boolean bridgesNetworks() { return this != DJ_DESK && this != SONG_LIBRARY; }

    public static Optional<PeakBlockType> byId(String id) {
        for (PeakBlockType t : values()) {
            if (t.id.equals(id)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
