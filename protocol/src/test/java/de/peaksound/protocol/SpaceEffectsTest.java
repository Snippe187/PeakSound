package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SpaceEffectsTest {
    @Test
    void speakerDelayAndReverbAreClampedAndRoundTrip() {
        SpeakerSettings s = new SpeakerSettings("Box", SpeakerChannel.CENTER, 80, SpeakerKind.WOOFER, false, 9999, ReverbPreset.HALL).sanitized();
        assertEquals(SpeakerSettings.MAX_DELAY_MS, s.delayMs());
        assertEquals(0, new SpeakerSettings("Box", SpeakerChannel.CENTER, 80, SpeakerKind.WOOFER, false, -5, null).sanitized().delayMs());
        assertEquals(ReverbPreset.OFF, new SpeakerSettings("Box", SpeakerChannel.CENTER, 80, SpeakerKind.WOOFER, false, 0, null).sanitized().reverb());
        var packet = new Packet.SpeakerUpdate(1, 2, 3, new SpeakerSettings("Box", SpeakerChannel.CENTER, 80, SpeakerKind.WOOFER, false, 300, ReverbPreset.BATH));
        assertEquals(packet, PacketCodec.decodeServerbound(packet.encode()));
        var tooLong = new Packet.SpeakerUpdate(1, 2, 3, new SpeakerSettings("Box", SpeakerChannel.CENTER, 80, SpeakerKind.WOOFER, false, 501, ReverbPreset.OFF));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(tooLong.encode()));
    }

    @Test
    void withMethodsKeepDelayAndReverb() {
        SpeakerSettings s = new SpeakerSettings("", SpeakerChannel.CENTER, 50, SpeakerKind.FULL_RANGE, false, 120, ReverbPreset.CAVE);
        assertEquals(120, s.withVolume(10).withName("x").withRedstone(true).withKind(SpeakerKind.TWEETER).withChannel(SpeakerChannel.FRONT_LEFT).delayMs());
        assertEquals(ReverbPreset.CAVE, s.withVolume(10).reverb());
    }

    @Test
    void jukeboxCrossfadeIsClampedKeptAndSent() {
        JukeboxSettings s = JukeboxSettings.defaults(16, 80).withCrossfade(99);
        assertEquals(JukeboxSettings.MAX_CROSSFADE, s.sanitized().crossfade());
        JukeboxSettings kept = JukeboxSettings.defaults(16, 80).withCrossfade(5);
        assertEquals(5, kept.withShuffle(true).withLoop(LoopMode.ALL).withName("a").withPort(0, PortFunction.OFF).crossfade());
        var packet = new Packet.JukeboxUpdate(1, 2, 3, kept);
        assertEquals(packet, PacketCodec.decodeServerbound(packet.encode()));
    }

    @Test
    void playbackStartCarriesDelayAndReverb() {
        var start = new Packet.PlaybackStart(1, 2, 3, "a".repeat(64), 0, 1L, 1000, 2, 16, 80, 3, 0, 200, ReverbPreset.ROOM.ordinal());
        assertEquals(start, PacketCodec.decodeClientbound(start.encode()));
        assertEquals(0, new Packet.PlaybackStart(1, 2, 3, "a".repeat(64), 0, 1L, 1000, 2, 16, 80, 3, 0).delayMs());
    }
}