package de.peaksound.audio;

import static org.junit.jupiter.api.Assertions.*;

import de.peaksound.protocol.AudioFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class AudioCoreTest {
    /** Erzeugt eine Sinus-WAV (16 Bit, little endian) mit der gegebenen Kanalzahl. */
    static byte[] sineWav(int channels, int sampleRate, double seconds) throws IOException {
        int frames = (int) (sampleRate * seconds);
        byte[] pcm = new byte[frames * channels * 2];
        for (int i = 0; i < frames; i++) {
            short v = (short) (Math.sin(2 * Math.PI * 440 * i / sampleRate) * 12000);
            for (int c = 0; c < channels; c++) {
                int o = (i * channels + c) * 2;
                pcm[o] = (byte) v;
                pcm[o + 1] = (byte) (v >> 8);
            }
        }
        javax.sound.sampled.AudioFormat fmt =
                new javax.sound.sampled.AudioFormat(sampleRate, 16, channels, true, false);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AudioSystem.write(new AudioInputStream(new ByteArrayInputStream(pcm), fmt, frames), AudioFileFormat.Type.WAVE, out);
        return out.toByteArray();
    }

    @Test
    void inspectsWav() throws Exception {
        AudioInfo info = AudioInspector.inspect(sineWav(2, 44100, 2.0));
        assertEquals(AudioFormat.WAV, info.format());
        assertEquals(2, info.channels());
        assertEquals(44100, info.sampleRate());
        assertEquals(2000, info.durationMs(), 20);
        assertFalse(info.multichannel());
        assertEquals(0, info.cover().length);
    }

    @Test
    void rejectsGarbage() {
        assertThrows(AudioException.class, () -> AudioInspector.inspect(new byte[64]));
        assertThrows(AudioException.class, () -> AudioInspector.inspect("RIFFxxxxWAVEgarbagegarbage".getBytes()));
    }

    @Test
    void wavToFlacRoundTripKeepsAudio() throws Exception {
        byte[] wav = sineWav(2, 44100, 1.0);
        Transcoder.Result result = Transcoder.create("definitely-not-ffmpeg").transcode(wav, AudioFormat.WAV);
        assertEquals(AudioFormat.FLAC, result.format());
        assertTrue(result.data().length < wav.length, "FLAC muss kleiner als WAV sein");

        AudioInfo flacInfo = AudioInspector.inspect(result.data());
        assertEquals(AudioFormat.FLAC, flacInfo.format());
        assertEquals(1000, flacInfo.durationMs(), 20);

        // Decodieren und mit dem Original vergleichen
        try (PcmSource original = PcmSource.open(wav, AudioFormat.WAV);
             PcmSource decoded = PcmSource.open(result.data(), AudioFormat.FLAC)) {
            assertEquals(original.channels(), decoded.channels());
            short[] a = new short[4096 * 2];
            short[] b = new short[4096 * 2];
            long total = 0;
            int ra;
            while ((ra = original.read(a, 4096)) > 0) {
                int rb = 0;
                int filled = 0;
                while (filled < ra) { // FLAC liefert andere Blockgroessen: bis ra Frames sammeln
                    short[] part = new short[ra * 2];
                    rb = decoded.read(part, ra - filled);
                    assertTrue(rb > 0);
                    System.arraycopy(part, 0, b, filled * 2, rb * 2);
                    filled += rb;
                }
                for (int i = 0; i < ra * 2; i++) assertEquals(a[i], b[i], "Sample " + (total + i));
                total += ra * 2L;
            }
            assertEquals(44100 * 2L, total);
        }
    }

    @Test
    void multichannelWavIsDetectedAsMultichannel() throws Exception {
        // 6 Kanaele ueber javax.sound: nicht jede JDK-Version schreibt das, dann wird der Test uebersprungen
        byte[] wav;
        try {
            wav = sineWav(6, 48000, 1.0);
        } catch (Exception | Error e) {
            org.junit.jupiter.api.Assumptions.abort("JDK kann kein 6-Kanal-WAV schreiben");
            return;
        }
        AudioInfo info = AudioInspector.inspect(wav);
        assertEquals(6, info.channels());
        assertTrue(info.multichannel());
    }

    @Test
    void keepsAlreadyCompactFormats() throws Exception {
        byte[] ogg = new byte[64];
        System.arraycopy("OggS".getBytes(), 0, ogg, 0, 4);
        Transcoder t = Transcoder.create("definitely-not-ffmpeg");
        assertFalse(t.ffmpegAvailable());
        assertSame(ogg, t.transcode(ogg, AudioFormat.OGG).data());
    }
}
