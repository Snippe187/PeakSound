package de.peaksound.protocol;

import static de.peaksound.protocol.SpeakerChannel.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChannelLayoutTest {
    private static float[] gains(int channels, boolean vorbis, SpeakerChannel target) {
        return ChannelLayout.mapping(channels, vorbis, target).gains();
    }

    /** Wendet das Mapping auf einen Frame an. */
    private static double apply(int channels, boolean vorbis, SpeakerChannel target, double... frame) {
        float[] g = gains(channels, vorbis, target);
        double sum = 0;
        for (int i = 0; i < channels; i++) sum += g[i] * frame[i];
        return sum;
    }

    @Test
    void sourceOrdersHaveTheRightSizeAndNoDuplicates() {
        for (int n = 1; n <= 8; n++) {
            for (boolean vorbis : new boolean[] {false, true}) {
                SpeakerChannel[] order = ChannelLayout.sourceOrder(n, vorbis);
                assertEquals(n, order.length);
                assertEquals(n, java.util.Arrays.stream(order).distinct().count(), "doppelt bei " + n + "/" + vorbis);
            }
        }
    }

    @Test
    void waveAndVorbisOrderDifferForFivePointOne() {
        // Wave: FL FR C LFE BL BR   Vorbis: FL C FR BL BR LFE
        assertEquals(1f, gains(6, false, FRONT_RIGHT)[1]);
        assertEquals(1f, gains(6, true, FRONT_RIGHT)[2]);
        assertEquals(1f, gains(6, false, SUBWOOFER)[3]);
        assertEquals(1f, gains(6, true, SUBWOOFER)[5]);
        assertEquals(1f, gains(6, false, CENTER)[2]);
        assertEquals(1f, gains(6, true, CENTER)[1]);
    }

    @Test
    void sevenPointOneMapsDirectly() {
        // Wave 8: FL FR C LFE BL BR SL SR
        assertEquals(1f, gains(8, false, SIDE_LEFT)[6]);
        assertEquals(1f, gains(8, false, REAR_RIGHT)[5]);
        // Vorbis 8: FL C FR SL SR BL BR LFE
        assertEquals(1f, gains(8, true, SIDE_RIGHT)[4]);
        assertEquals(1f, gains(8, true, REAR_LEFT)[5]);
    }

    @Test
    void directChannelIsPassedThroughAlone() {
        float[] g = gains(8, false, FRONT_LEFT);
        for (int i = 0; i < g.length; i++) assertEquals(i == 0 ? 1f : 0f, g[i]);
    }

    @Test
    void missingHeightsAreDerivedFromSurroundInSevenPointOne() {
        // 7.1: keine Deckenkanaele; TFL aus SL, TBL aus BL (Wave-Reihenfolge)
        assertEquals(0.5f, gains(8, false, TOP_FRONT_LEFT)[6]);
        assertEquals(0.5f, gains(8, false, TOP_REAR_LEFT)[4]);
    }

    @Test
    void stereoUpmixCenterIsTheMidSignal() {
        assertEquals(0.6, apply(2, false, CENTER, 0.6, 0.6), 1e-6);   // gleiche Signale -> Mitte = Signal
        assertEquals(0.0, apply(2, false, CENTER, 0.5, -0.5), 1e-6);  // Gegenphase -> keine Mitte
        assertEquals(0.5, apply(2, false, CENTER, 1.0, 0.0), 1e-6);
    }

    @Test
    void stereoUpmixKeepsFrontChannelsUntouched() {
        assertEquals(0.3, apply(2, false, FRONT_LEFT, 0.3, 0.9), 1e-6);
        assertEquals(0.9, apply(2, false, FRONT_RIGHT, 0.3, 0.9), 1e-6);
    }

    @Test
    void monoCompatibleMaterialStaysAudibleButQuieterInTheBack() {
        // L = R: der Raumanteil ist null, aber ein Teil des Frontsignals haelt die hinteren Lautsprecher hoerbar
        for (SpeakerChannel ch : new SpeakerChannel[] {REAR_LEFT, REAR_RIGHT}) {
            double v = apply(2, false, ch, 0.7, 0.7);
            assertTrue(v > 0.1 && v < 0.7, ch + ": " + v);
        }
        for (SpeakerChannel ch : new SpeakerChannel[] {TOP_REAR_LEFT, TOP_REAR_RIGHT}) {
            double v = apply(2, false, ch, 0.7, 0.7);
            assertTrue(v > 0.05 && v < 0.7, ch + ": " + v);
        }
    }

    @Test
    void ambienceGoesToRearInAntiPhase() {
        // Rein linkes Signal: hinten links bekommt es, hinten rechts in Gegenphase
        assertEquals(0.8, apply(2, false, REAR_LEFT, 1.0, 0.0), 1e-6);
        assertEquals(-0.5, apply(2, false, REAR_RIGHT, 1.0, 0.0), 1e-6);
        // Die Differenz (L-R) ist der Raumanteil: bei Gegenphase ist hinten lauter als vorne
        assertTrue(apply(2, false, REAR_LEFT, 0.5, -0.5) > 0.5);
    }
    @Test
    void stereoSidesAreMostlyFront() {
        assertEquals(0.75, apply(2, false, SIDE_LEFT, 1.0, 0.0), 1e-6);
        assertEquals(-0.25, apply(2, false, SIDE_RIGHT, 1.0, 0.0), 1e-6);
    }

    @Test
    void subwooferIsDerivedWithLowPassOnlyWhenMissing() {
        assertTrue(ChannelLayout.mapping(2, false, SUBWOOFER).lowPass());
        assertTrue(ChannelLayout.mapping(1, false, SUBWOOFER).lowPass());
        assertFalse(ChannelLayout.mapping(6, false, SUBWOOFER).lowPass()); // 5.1 hat einen echten LFE-Kanal
        assertFalse(ChannelLayout.mapping(2, false, CENTER).lowPass());
    }

    @Test
    void monoSourceFeedsEverySpeaker() {
        for (SpeakerChannel ch : SpeakerChannel.values()) {
            float[] g = gains(1, false, ch);
            assertEquals(1, g.length);
            assertTrue(g[0] > 0, ch.name());
        }
        assertEquals(1f, gains(1, false, FRONT_LEFT)[0]);
        assertEquals(1f, gains(1, false, CENTER)[0]);
    }

    @Test
    void everyChannelForEverySourceHasAFiniteBoundedMapping() {
        for (int n = 1; n <= 12; n++) {
            for (SpeakerChannel ch : SpeakerChannel.values()) {
                float[] g = gains(n, false, ch);
                assertEquals(n, g.length);
                float sumAbs = 0;
                for (float v : g) { assertTrue(Float.isFinite(v)); sumAbs += Math.abs(v); }
                assertTrue(sumAbs <= 2.01f, "zu laut: " + n + " " + ch + " " + sumAbs);
            }
        }
    }

    // ---- Mixer ----

    private static short[] stereoTone(double hz, int rate, int frames, double amplitude) {
        short[] pcm = new short[frames * 2];
        for (int i = 0; i < frames; i++) {
            short v = (short) (Math.sin(2 * Math.PI * hz * i / rate) * amplitude);
            pcm[2 * i] = v;
            pcm[2 * i + 1] = v;
        }
        return pcm;
    }

    private static double rms(short[] pcm, int from) {
        double sum = 0;
        for (int i = from; i < pcm.length; i++) sum += (double) pcm[i] * pcm[i];
        return Math.sqrt(sum / (pcm.length - from));
    }

    @Test
    void derivedSubwooferPassesBassAndCutsHighs() {
        int rate = 44100, frames = rate;
        ChannelMixer bass = new ChannelMixer(ChannelLayout.mapping(2, false, SUBWOOFER), rate);
        ChannelMixer treble = new ChannelMixer(ChannelLayout.mapping(2, false, SUBWOOFER), rate);
        double low = rms(bass.process(stereoTone(50, rate, frames, 10000), frames, 2), rate / 2);
        double high = rms(treble.process(stereoTone(3000, rate, frames, 10000), frames, 2), rate / 2);
        assertTrue(low > 5000, "Bass kommt durch: " + low);
        assertTrue(high < 100, "Hoehen werden gesperrt: " + high);
    }

    @Test
    void centerMixerMatchesTheMapping() {
        ChannelMixer center = new ChannelMixer(ChannelLayout.mapping(2, false, CENTER), 44100);
        short[] in = {1000, 3000, -2000, -2000};
        short[] out = center.process(in, 2, 2);
        assertEquals(2000, out[0]);
        assertEquals(-2000, out[1]);
    }

    @Test
    void mixerClipsInsteadOfOverflowing() {
        ChannelMixer m = new ChannelMixer(new ChannelLayout.Mapping(new float[] {2f}, false), 44100);
        assertEquals(Short.MAX_VALUE, m.process(new short[] {30000}, 1, 1)[0]);
        assertEquals(Short.MIN_VALUE, m.process(new short[] {-30000}, 1, 1)[0]);
    }

    @Test
    void mixerRejectsWrongChannelCount() {
        ChannelMixer m = new ChannelMixer(ChannelLayout.mapping(2, false, CENTER), 44100);
        assertThrows(IllegalArgumentException.class, () -> m.process(new short[6], 1, 6));
    }

    @Test
    void downmixAveragesChannels() {
        short[] mono = ChannelMixer.downmix(new short[] {100, 300, 200, 400}, 2, 2);
        assertEquals(200, mono[0]);
        assertEquals(300, mono[1]);
        assertEquals(5, ChannelMixer.downmix(new short[] {5}, 1, 1)[0]);
    }

    // ---- Einstellungen und Nummern ----

    @Test
    void speakerSettingsAreSanitized() {
        SpeakerSettings s = new SpeakerSettings("  \u0007Box" + "x".repeat(60), SpeakerChannel.CENTER, 250).sanitized();
        assertEquals(100, s.volume());
        assertTrue(s.name().length() <= SpeakerSettings.MAX_NAME);
        assertFalse(s.name().contains("\u0007"));
        assertEquals(0, new SpeakerSettings("", SpeakerChannel.CENTER, -9).sanitized().volume());
    }

    @Test
    void rejectsInvalidSpeakerPackets() {
        byte[] ok = new Packet.SpeakerUpdate(0, 0, 0, SpeakerSettings.defaults(FRONT_LEFT)).encode();
        // Name "" (2 Bytes Laenge) -> danach Kanal und Lautstaerke
        int channelIndex = 13 + 2;
        byte[] badChannel = ok.clone();
        badChannel[channelIndex] = 99;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(badChannel));
        byte[] badVolume = ok.clone();
        badVolume[channelIndex + 1] = (byte) 200;
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(badVolume));
    }

    @Test
    void speakerNumbersMapToUniqueStatesDifferentFromBaseBlocks() {
        java.util.Set<Integer> notes = new java.util.HashSet<>();
        for (int n = 1; n <= SpeakerNumbering.MAX_NUMBER; n++) {
            int note = SpeakerNumbering.noteFor(n);
            assertTrue(notes.add(note));
            assertEquals(n, SpeakerNumbering.numberOf(SpeakerNumbering.INSTRUMENT, note, true));
        }
        assertEquals(0, SpeakerNumbering.numberOf("harp", 3, true));
        assertEquals(0, SpeakerNumbering.numberOf(SpeakerNumbering.INSTRUMENT, 3, false));
        assertThrows(IllegalArgumentException.class, () -> SpeakerNumbering.noteFor(0));
        assertThrows(IllegalArgumentException.class, () -> SpeakerNumbering.noteFor(26));
        for (PeakBlockType t : PeakBlockType.values()) {
            assertNotEquals(SpeakerNumbering.INSTRUMENT, t.instrument(), "Basiszustand darf nicht mit Nummern kollidieren");
        }
    }

    @Test
    void withoutUpmixStereoStaysStereo() {
        for (SpeakerChannel ch : SpeakerChannel.values()) {
            boolean front = ch == FRONT_LEFT || ch == FRONT_RIGHT;
            assertEquals(front, ChannelLayout.playsWithoutUpmix(2, ch), "Stereo " + ch);
            assertEquals(front || ch == CENTER, ChannelLayout.playsWithoutUpmix(1, ch), "Mono " + ch);
            assertTrue(ChannelLayout.playsWithoutUpmix(6, ch), "5.1 " + ch); // Mehrkanal bleibt Mehrkanal
            assertTrue(ChannelLayout.playsWithoutUpmix(8, ch), "7.1 " + ch);
        }
    }

    @Test
    void defaultOrderCoversAllChannelsOnce() {
        assertEquals(SpeakerChannel.values().length, java.util.Arrays.stream(SpeakerChannel.DEFAULT_ORDER).distinct().count());
        assertEquals(FRONT_LEFT, SpeakerChannel.DEFAULT_ORDER[0]);
        assertEquals(FRONT_RIGHT, FRONT_LEFT.next());
        assertEquals(TOP_REAR_RIGHT, FRONT_LEFT.previous()); // springt am Anfang um
    }

    @Test
    void speakerKindsFilterTheirBand() {
        int rate = 44100;
        short[] low = new short[rate], high = new short[rate];
        for (int i = 0; i < rate; i++) {
            low[i] = (short) (8000 * Math.sin(2 * Math.PI * 80 * i / rate));
            high[i] = (short) (8000 * Math.sin(2 * Math.PI * 8000 * i / rate));
        }
        ChannelLayout.Mapping mono = new ChannelLayout.Mapping(new float[] {1f}, false);
        assertTrue(rms(new ChannelMixer(mono, rate, SpeakerKind.WOOFER).process(low, rate, 1)) > 4000);
        assertTrue(rms(new ChannelMixer(mono, rate, SpeakerKind.WOOFER).process(high, rate, 1)) < 400);
        assertTrue(rms(new ChannelMixer(mono, rate, SpeakerKind.TWEETER).process(high, rate, 1)) > 4000);
        assertTrue(rms(new ChannelMixer(mono, rate, SpeakerKind.TWEETER).process(low, rate, 1)) < 400);
        assertTrue(rms(new ChannelMixer(mono, rate, SpeakerKind.SUBWOOFER).process(high, rate, 1)) < 100);
        assertTrue(rms(new ChannelMixer(mono, rate, SpeakerKind.FULL_RANGE).process(high, rate, 1)) > 4000);
    }

    private static double rms(short[] s) {
        double sum = 0;
        for (int i = s.length / 2; i < s.length; i++) sum += (double) s[i] * s[i];
        return Math.sqrt(sum / (s.length - s.length / 2));
    }
}