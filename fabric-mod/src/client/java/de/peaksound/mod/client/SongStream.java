package de.peaksound.mod.client;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Strom ueber einen Song, der gerade noch heruntergeladen wird. Der Decoder liest, was schon da ist, und wartet auf
 * den Rest. So beginnt die Wiedergabe nach den ersten Chunks, nicht erst am Ende des Downloads. Bricht der Download
 * ab, endet das Lesen mit einem Fehler.
 */
final class SongStream extends InputStream {
    private final Deque<byte[]> queue = new ArrayDeque<>();
    private byte[] current = new byte[0];
    private int pos;
    private boolean done;
    private boolean failed;

    /** Haengt den naechsten Teil des Songs an (in Reihenfolge, ohne Luecken). */
    synchronized void append(byte[] data) {
        queue.add(data);
        notifyAll();
    }

    /** Der ganze Song ist da. */
    synchronized void finish() {
        done = true;
        notifyAll();
    }

    /** Der Download ist gescheitert (Server meldet "nicht verfuegbar" oder Stillstand). */
    synchronized void fail() {
        failed = true;
        notifyAll();
    }

    private void awaitData() throws IOException {
        while (pos >= current.length) {
            byte[] next = queue.poll();
            if (next != null) {
                current = next;
                pos = 0;
                continue;
            }
            if (failed) throw new IOException("Download abgebrochen");
            if (done) return; // Ende des Songs
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Unterbrochen", e);
            }
        }
    }

    @Override
    public synchronized int read() throws IOException {
        awaitData();
        return pos < current.length ? current[pos++] & 0xFF : -1;
    }

    @Override
    public synchronized int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        awaitData();
        if (pos >= current.length) return -1;
        int n = Math.min(len, current.length - pos);
        System.arraycopy(current, pos, b, off, n);
        pos += n;
        return n;
    }

    @Override
    public synchronized int available() {
        return current.length - pos;
    }
}
