package de.peaksound.protocol;

import static de.peaksound.protocol.SpeakerChannel.*;

/**
 * Verteilt die Kanaele einer Audiodatei auf die Lautsprecher eines 7.1.4-Systems.
 * <ul>
 *   <li>Mehrkanal-Dateien werden direkt den Kanaelen zugeordnet (5.1, 7.1 ...).</li>
 *   <li>Stereo- und Mono-Dateien werden hochgemischt: Center = Mitte, Subwoofer = Bass (Tiefpass),
 *       hinten und oben = Raumanteil (Differenz L-R, gegenphasig links/rechts) plus ein Teil des Frontsignals,
 *       damit auch Mono-Material hinten hoerbar bleibt (dann leiser als vorne).</li>
 *   <li>Fehlt in einer Mehrkanal-Datei ein Kanal (z. B. keine Deckenkanaele bei 7.1), wird er aus den
 *       vorhandenen abgeleitet.</li>
 * </ul>
 * Die Datei-Kanalreihenfolge ist je Format verschieden: WAV/FLAC folgen der Windows-Reihenfolge,
 * Ogg Vorbis seiner eigenen (Center in der Mitte, LFE zuletzt).
 */
public final class ChannelLayout {
    private ChannelLayout() {}

    /**
     * Gewichte fuer einen Ziel-Lautsprecher: {@code gains[i]} multipliziert Quellkanal i. {@code lowPass} = true,
     * wenn das Ergebnis noch tiefpassgefiltert werden muss (abgeleiteter Subwoofer).
     */
    public record Mapping(float[] gains, boolean lowPass) {}

    /** Welche Kanaele hat eine Datei mit {@code count} Kanaelen, in Datei-Reihenfolge? Unbekannte Plaetze sind null. */
    public static SpeakerChannel[] sourceOrder(int count, boolean vorbisOrder) {
        SpeakerChannel[] wave = {FRONT_LEFT, FRONT_RIGHT, CENTER, SUBWOOFER, REAR_LEFT, REAR_RIGHT, SIDE_LEFT, SIDE_RIGHT,
                TOP_FRONT_LEFT, TOP_FRONT_RIGHT, TOP_REAR_LEFT, TOP_REAR_RIGHT};
        SpeakerChannel[] result = switch (count) {
            case 1 -> new SpeakerChannel[] {CENTER};
            case 2 -> new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT};
            case 3 -> vorbisOrder ? new SpeakerChannel[] {FRONT_LEFT, CENTER, FRONT_RIGHT}
                    : new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT, CENTER};
            case 4 -> new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT};
            case 5 -> vorbisOrder ? new SpeakerChannel[] {FRONT_LEFT, CENTER, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT}
                    : new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT, CENTER, REAR_LEFT, REAR_RIGHT};
            case 6 -> vorbisOrder ? new SpeakerChannel[] {FRONT_LEFT, CENTER, FRONT_RIGHT, REAR_LEFT, REAR_RIGHT, SUBWOOFER}
                    : new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT, CENTER, SUBWOOFER, REAR_LEFT, REAR_RIGHT};
            case 7 -> vorbisOrder ? new SpeakerChannel[] {FRONT_LEFT, CENTER, FRONT_RIGHT, SIDE_LEFT, SIDE_RIGHT, REAR_LEFT, SUBWOOFER}
                    : new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT, CENTER, SUBWOOFER, REAR_LEFT, SIDE_LEFT, SIDE_RIGHT};
            case 8 -> vorbisOrder ? new SpeakerChannel[] {FRONT_LEFT, CENTER, FRONT_RIGHT, SIDE_LEFT, SIDE_RIGHT, REAR_LEFT, REAR_RIGHT, SUBWOOFER}
                    : new SpeakerChannel[] {FRONT_LEFT, FRONT_RIGHT, CENTER, SUBWOOFER, REAR_LEFT, REAR_RIGHT, SIDE_LEFT, SIDE_RIGHT};
            default -> {
                // Mehr als 8 Kanaele (z. B. 7.1.4 als 12-Kanal-Datei): Windows-Reihenfolge, Ueberzaehlige unbekannt
                SpeakerChannel[] r = new SpeakerChannel[count];
                for (int i = 0; i < count && i < wave.length; i++) r[i] = wave[i];
                yield r;
            }
        };
        return result;
    }

    /**
     * Spielt dieser Lautsprecher mit, wenn NICHT hochgemischt wird? Stereo-Songs laufen nur auf vorne links/rechts,
     * Mono-Songs auf vorne links/rechts und Center. Mehrkanal-Songs (mehr als 2 Kanaele) laufen immer auf allen Lautsprechern.
     */
    public static boolean playsWithoutUpmix(int sourceChannels, SpeakerChannel speaker) {
        if (sourceChannels > 2) return true;
        if (speaker == FRONT_LEFT || speaker == FRONT_RIGHT) return true;
        return sourceChannels == 1 && speaker == CENTER;
    }

    public static Mapping mapping(int sourceChannels, boolean vorbisOrder, SpeakerChannel target) {
        SpeakerChannel[] order = sourceOrder(sourceChannels, vorbisOrder);
        int n = sourceChannels;
        float[] g = new float[n];
        int fl = indexOf(order, FRONT_LEFT), fr = indexOf(order, FRONT_RIGHT), c = indexOf(order, CENTER);
        int sl = indexOf(order, SIDE_LEFT), sr = indexOf(order, SIDE_RIGHT);
        int bl = indexOf(order, REAR_LEFT), br = indexOf(order, REAR_RIGHT);
        int own = indexOf(order, target);
        // Ein Quellkanal fuer genau diese Position ist da: direkt durchreichen
        if (own >= 0) {
            g[own] = 1f;
            return new Mapping(g, false);
        }
        boolean stereoFront = fl >= 0 && fr >= 0;
        int mono = c >= 0 ? c : 0; // Quelle fuer Mono-Material
        boolean lowPass = false;
        switch (target) {
            case FRONT_LEFT, FRONT_RIGHT -> g[stereoFront ? (target == FRONT_LEFT ? fl : fr) : mono] = 1f;
            case CENTER -> {
                if (stereoFront) { g[fl] = 0.5f; g[fr] = 0.5f; } else g[mono] = 1f;
            }
            case SUBWOOFER -> {
                lowPass = true;
                if (stereoFront) { g[fl] = 0.5f; g[fr] = 0.5f; } else g[mono] = 1f;
            }
            case SIDE_LEFT -> side(g, stereoFront, fl, fr, bl, mono, true);
            case SIDE_RIGHT -> side(g, stereoFront, fl, fr, br, mono, false);
            case REAR_LEFT -> {
                if (sl >= 0) g[sl] = 1f;
                else if (stereoFront) { g[fl] = 0.8f; g[fr] = -0.5f; } // Raumanteil (L-R)/2 plus etwas Front, damit Mono-Material hinten nicht verstummt
                else g[mono] = 0.5f;
            }
            case REAR_RIGHT -> {
                if (sr >= 0) g[sr] = 1f;
                else if (stereoFront) { g[fr] = 0.8f; g[fl] = -0.5f; }
                else g[mono] = 0.5f;
            }
            case TOP_FRONT_LEFT -> {
                if (sl >= 0) g[sl] = 0.5f;
                else if (stereoFront) { g[fl] = 0.45f; g[fr] = -0.15f; }
                else g[mono] = 0.3f;
            }
            case TOP_FRONT_RIGHT -> {
                if (sr >= 0) g[sr] = 0.5f;
                else if (stereoFront) { g[fr] = 0.45f; g[fl] = -0.15f; }
                else g[mono] = 0.3f;
            }
            case TOP_REAR_LEFT -> {
                if (bl >= 0) g[bl] = 0.5f;
                else if (sl >= 0) g[sl] = 0.5f;
                else if (stereoFront) { g[fl] = 0.35f; g[fr] = -0.15f; }
                else g[mono] = 0.3f;
            }
            case TOP_REAR_RIGHT -> {
                if (br >= 0) g[br] = 0.5f;
                else if (sr >= 0) g[sr] = 0.5f;
                else if (stereoFront) { g[fr] = 0.35f; g[fl] = -0.15f; }
                else g[mono] = 0.3f;
            }
        }
        return new Mapping(g, lowPass);
    }

    /** Seitenkanal ohne eigene Quelle: Front plus Raumanteil, oder Front plus hinterer Kanal. */
    private static void side(float[] g, boolean stereoFront, int fl, int fr, int rear, int mono, boolean left) {
        int front = left ? fl : fr;
        int other = left ? fr : fl;
        if (stereoFront && rear >= 0) {
            g[front] = 0.5f;
            g[rear] = 0.5f;
        } else if (stereoFront) {
            g[front] = 0.75f;
            g[other] = -0.25f;
        } else {
            g[mono] = 0.5f;
        }
    }

    private static int indexOf(SpeakerChannel[] order, SpeakerChannel channel) {
        for (int i = 0; i < order.length; i++) if (order[i] == channel) return i;
        return -1;
    }
}
