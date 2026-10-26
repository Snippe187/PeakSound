package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class AudioFormatTest {
    private static byte[] head(String magic) {
        byte[] b = new byte[32];
        byte[] m = magic.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        System.arraycopy(m, 0, b, 0, m.length);
        return b;
    }

    @Test
    void detectsFormatsByMagicBytes() {
        assertEquals(AudioFormat.FLAC, AudioFormat.detect(head("fLaC")).orElseThrow());
        assertEquals(AudioFormat.OGG, AudioFormat.detect(head("OggS")).orElseThrow());
        assertEquals(AudioFormat.MP3, AudioFormat.detect(head("ID3")).orElseThrow());
        byte[] wav = head("RIFF");
        System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);
        assertEquals(AudioFormat.WAV, AudioFormat.detect(wav).orElseThrow());
        byte[] frame = new byte[32];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xFB;
        assertEquals(AudioFormat.MP3, AudioFormat.detect(frame).orElseThrow());
    }

    @Test
    void rejectsGarbageAndShortInput() {
        assertTrue(AudioFormat.detect(new byte[3]).isEmpty());
        assertTrue(AudioFormat.detect(head("MZ")).isEmpty()); // .exe
        byte[] riffNotWave = head("RIFF");
        System.arraycopy("AVI ".getBytes(), 0, riffNotWave, 8, 4);
        assertTrue(AudioFormat.detect(riffNotWave).isEmpty());
        assertTrue(AudioFormat.detect(Arrays.copyOf(new byte[] {(byte) 0xFF, 0x10}, 32)).isEmpty());
    }
}
