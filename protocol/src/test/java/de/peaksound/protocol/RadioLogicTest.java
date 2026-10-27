package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RadioLogicTest {
    @Test
    void frequenciesAreClampedAndFormatted() {
        assertEquals(RadioLogic.MIN_FREQUENCY, RadioLogic.clampFrequency(10));
        assertEquals(RadioLogic.MAX_FREQUENCY, RadioLogic.clampFrequency(5000));
        assertEquals("98.4 MHz", RadioLogic.format(984));
        assertEquals("108.0 MHz", RadioLogic.format(1080));
        assertEquals("87.5 MHz", RadioLogic.format(875));
    }

    @Test
    void staticFadesAsTheTunedFrequencyApproachesAStation() {
        assertEquals(RadioLogic.FULL_STATIC, RadioLogic.staticLevel(1000, List.of()));
        assertEquals(0, RadioLogic.staticLevel(984, List.of(984, 1020)));
        assertEquals(52, RadioLogic.staticLevel(985, List.of(984)));
        assertEquals(RadioLogic.FULL_STATIC, RadioLogic.staticLevel(1000, List.of(984)));
        // naeher dran = weniger Rauschen
        assertEquals(true, RadioLogic.staticLevel(986, List.of(984)) < RadioLogic.staticLevel(988, List.of(984)));
    }

    @Test
    void radioSettingsRoundTripAndRejectOutOfRange() {
        var packet = new Packet.OpenRadioMenu(1, 2, 3, new RadioSettings(true, 984, 20, 70), false, "Titel");
        assertEquals(packet, PacketCodec.decodeClientbound(packet.encode()));
        var update = new Packet.RadioUpdate(1, 2, 3, new RadioSettings(false, 875, 1, 0));
        assertEquals(update, PacketCodec.decodeServerbound(update.encode()));
        var bad = new Packet.RadioUpdate(1, 2, 3, new RadioSettings(true, 2000, 20, 70));
        assertThrows(MalformedPacketException.class, () -> PacketCodec.decodeServerbound(bad.encode()));
        var stat = new Packet.RadioStatic(1, 2, 3, 16, 80, 52);
        assertEquals(stat, PacketCodec.decodeClientbound(stat.encode()));
        var status = new Packet.RadioStatus(1, 2, 3, "Song");
        assertEquals(status, PacketCodec.decodeClientbound(status.encode()));
    }
}
