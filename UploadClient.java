package de.peaksound.mod.client;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Zustand eines laufenden Uploads auf dem Client. Der Server bestimmt Chunk-Groesse und Fenster; der Client
 * sendet nie mehr als {@code window} unbestaetigte Chunks und folgt den kumulativen Bestaetigungen.
 */
final class UploadClient {
    enum Phase { IDLE, WAITING_ACCEPT, SENDING, PROCESSING, DONE, FAILED }

    private static int ticket;
    private static byte[] data;
    private static int uploadId;
    private static int chunkSize;
    private static int window;
    private static int sent;
    private static int acked;
    private static Phase phase = Phase.IDLE;
    /** Das gerade offene Upload-Fenster (oder null). */
    private static UploadScreen active;
    private static Packet.UploadReject lastReject;
    private static Packet.UploadResult lastResult;

    private UploadClient() {}

    static void open(int newTicket) {
        ticket = newTicket;
        phase = Phase.IDLE;
        lastReject = null;
        lastResult = null;
        UploadScreen screen = new UploadScreen(newTicket);
        active = screen;
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    static Phase phase() { return phase; }

    static Packet.UploadReject reject() { return lastReject; }

    static Packet.UploadResult result() { return lastResult; }

    /** 0..1: Anteil der vom Server bestaetigten Daten. */
    static float progress() {
        if (data == null || phase == Phase.IDLE) return 0;
        if (phase == Phase.PROCESSING || phase == Phase.DONE) return 1;
        return Math.min(1f, (float) ((long) acked * chunkSize) / data.length);
    }

    static void begin(int ticketId, String fallbackTitle, byte[] file) {
        data = file;
        sent = 0;
        acked = 0;
        phase = Phase.WAITING_ACCEPT;
        lastReject = null;
        ClientNetwork.send(new Packet.UploadStart(ticketId, fallbackTitle, file.length));
    }

    static void onAccept(Packet.UploadAccept accept) {
        if (phase != Phase.WAITING_ACCEPT) return;
        uploadId = accept.uploadId();
        chunkSize = accept.chunkSize();
        window = accept.window();
        phase = Phase.SENDING;
        pump();
    }

    static void onAck(Packet.UploadAck ack) {
        if (phase != Phase.SENDING || ack.uploadId() != uploadId) return;
        acked = Math.max(acked, ack.nextIndex());
        if (acked >= totalChunks()) {
            phase = Phase.PROCESSING;
            return;
        }
        pump();
    }

    static void onReject(Packet.UploadReject reject) {
        lastReject = reject;
        phase = Phase.FAILED;
        data = null;
    }

    static void onResult(Packet.UploadResult result) {
        lastResult = result;
        phase = Phase.DONE;
        data = null;
        // Fenster schliesst sich; der Chat zeigt, was passiert ist
        Screen current = Minecraft.getInstance().screen;
        if (current instanceof UploadScreen) current.onClose();
    }

    static void screenClosed(UploadScreen screen) {
        if (active == screen) active = null;
    }

    static void cancel() {
        data = null;
        phase = Phase.IDLE;
    }

    private static int totalChunks() { return Limits.chunkCount(data.length, chunkSize); }

    private static void pump() {
        int total = totalChunks();
        while (sent < total && sent - acked < window) {
            int from = sent * chunkSize;
            int to = Math.min(data.length, from + chunkSize);
            ClientNetwork.send(new Packet.UploadChunk(uploadId, sent, Arrays.copyOfRange(data, from, to)));
            sent++;
        }
        if (sent >= total && acked >= total) phase = Phase.PROCESSING;
    }
}
