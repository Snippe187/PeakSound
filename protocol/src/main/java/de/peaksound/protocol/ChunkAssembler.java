package de.peaksound.protocol;

/**
 * Setzt eine Datei aus Chunks zusammen und erzwingt dabei Reihenfolge und Groessen.
 * Die Gesamtgroesse steht beim Start fest (und wurde gegen die Limits geprueft); ein Client kann
 * deshalb weder mehr Speicher belegen als angekuendigt noch Luecken oder Duplikate einschleusen.
 */
public final class ChunkAssembler {
    private final byte[] buffer;
    private final int chunkSize;
    private final int totalChunks;
    private int nextIndex;

    public ChunkAssembler(int totalBytes, int chunkSize) {
        if (totalBytes <= 0 || totalBytes > Limits.MAX_UPLOAD_BYTES) throw new IllegalArgumentException("Dateigroesse");
        if (chunkSize <= 0) throw new IllegalArgumentException("chunkSize");
        this.buffer = new byte[totalBytes];
        this.chunkSize = chunkSize;
        this.totalChunks = Limits.chunkCount(totalBytes, chunkSize);
    }

    /** @return Index des naechsten erwarteten Chunks (kumulative Bestaetigung) */
    public int nextIndex() { return nextIndex; }

    public int totalChunks() { return totalChunks; }

    public boolean complete() { return nextIndex == totalChunks; }

    /**
     * @throws MalformedPacketException bei falschem Index oder falscher Chunk-Groesse
     */
    public void accept(int index, byte[] data) {
        if (index != nextIndex || index >= totalChunks) throw new MalformedPacketException("Chunk ausser der Reihe");
        int expected = index == totalChunks - 1 ? buffer.length - index * chunkSize : chunkSize;
        if (data.length != expected) throw new MalformedPacketException("Falsche Chunk-Groesse");
        System.arraycopy(data, 0, buffer, index * chunkSize, data.length);
        nextIndex++;
    }

    /** Die fertige Datei. Nur aufrufen, wenn {@link #complete()}. */
    public byte[] result() {
        if (!complete()) throw new IllegalStateException("Upload unvollstaendig");
        return buffer;
    }
}
