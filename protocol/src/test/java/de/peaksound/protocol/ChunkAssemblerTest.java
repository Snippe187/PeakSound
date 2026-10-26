package de.peaksound.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class ChunkAssemblerTest {
    private static byte[] slice(byte[] data, int chunkSize, int index) {
        return Arrays.copyOfRange(data, index * chunkSize, Math.min(data.length, (index + 1) * chunkSize));
    }

    @Test
    void assemblesInOrder() {
        byte[] file = new byte[10_000];
        for (int i = 0; i < file.length; i++) file[i] = (byte) (i * 31);
        ChunkAssembler a = new ChunkAssembler(file.length, 4096);
        assertEquals(3, a.totalChunks());
        for (int i = 0; i < 3; i++) {
            assertFalse(a.complete());
            a.accept(i, slice(file, 4096, i));
            assertEquals(i + 1, a.nextIndex());
        }
        assertTrue(a.complete());
        assertArrayEquals(file, a.result());
    }

    @Test
    void rejectsOutOfOrderDuplicateAndExtraChunks() {
        byte[] file = new byte[8192];
        ChunkAssembler a = new ChunkAssembler(file.length, 4096);
        assertThrows(MalformedPacketException.class, () -> a.accept(1, new byte[4096])); // Luecke
        a.accept(0, new byte[4096]);
        assertThrows(MalformedPacketException.class, () -> a.accept(0, new byte[4096])); // Duplikat
        a.accept(1, new byte[4096]);
        assertThrows(MalformedPacketException.class, () -> a.accept(2, new byte[1])); // zu viel
    }

    @Test
    void rejectsWrongChunkSizes() {
        ChunkAssembler a = new ChunkAssembler(5000, 4096);
        assertThrows(MalformedPacketException.class, () -> a.accept(0, new byte[100]));
        a.accept(0, new byte[4096]);
        assertThrows(MalformedPacketException.class, () -> a.accept(1, new byte[4096])); // letzter Chunk ist 904 Byte
        a.accept(1, new byte[904]);
        assertTrue(a.complete());
    }

    @Test
    void resultBeforeCompleteFails() {
        assertThrows(IllegalStateException.class, () -> new ChunkAssembler(10, 4).result());
    }

    @Test
    void rejectsInvalidSizes() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkAssembler(0, 4));
        assertThrows(IllegalArgumentException.class, () -> new ChunkAssembler(Limits.MAX_UPLOAD_BYTES + 1, 4));
    }

    @Test
    void chunkCountRoundsUp() {
        assertEquals(0, Limits.chunkCount(0, 10));
        assertEquals(1, Limits.chunkCount(1, 10));
        assertEquals(1, Limits.chunkCount(10, 10));
        assertEquals(2, Limits.chunkCount(11, 10));
    }
}
