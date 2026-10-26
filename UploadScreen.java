package de.peaksound.mod.client;

import de.peaksound.audio.AudioException;
import de.peaksound.audio.AudioInfo;
import de.peaksound.audio.AudioInspector;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet.UploadReject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

/**
 * Upload-Fenster: Datei per natives Dateifenster (TinyFileDialogs) oder Drag-and-Drop waehlen, pruefen
 * (Format, Laenge, Mehrkanal-Warnung) und hochladen.
 */
final class UploadScreen extends Screen {
    private final int ticket;
    private Path file;
    private volatile AudioInfo info;
    private volatile byte[] data;
    private volatile Component error;
    private volatile boolean dialogOpen;
    private Button chooseButton;
    private Button uploadButton;

    UploadScreen(int ticket) {
        super(Component.translatable("peaksound.upload.title"));
        this.ticket = ticket;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        chooseButton = addRenderableWidget(Button.builder(Component.translatable("peaksound.upload.choose"), b -> chooseFile())
                .bounds(cx - 100, height / 2 - 40, 200, 20).build());
        uploadButton = addRenderableWidget(Button.builder(Component.translatable("peaksound.upload.start"), b -> startUpload())
                .bounds(cx - 100, height / 2 + 70, 98, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(cx + 2, height / 2 + 70, 98, 20).build());
        updateButtons();
    }

    private void updateButtons() {
        UploadClient.Phase phase = UploadClient.phase();
        boolean busy = phase == UploadClient.Phase.WAITING_ACCEPT || phase == UploadClient.Phase.SENDING
                || phase == UploadClient.Phase.PROCESSING;
        if (chooseButton != null) chooseButton.active = !busy && !dialogOpen;
        if (uploadButton != null) uploadButton.active = !busy && info != null && data != null && error == null;
    }

    // ---- Dateiauswahl ----

    private void chooseFile() {
        if (dialogOpen) return;
        dialogOpen = true;
        // Der native Dialog blockiert: in eigenem Thread oeffnen, damit das Spiel weiterlaeuft
        Thread.ofPlatform().daemon(true).name("PeakSound-FileDialog").start(() -> {
            String picked;
            try (MemoryStack stack = MemoryStack.stackPush()) {
                PointerBuffer filters = stack.mallocPointer(4);
                filters.put(stack.UTF8("*.mp3")).put(stack.UTF8("*.flac")).put(stack.UTF8("*.wav")).put(stack.UTF8("*.ogg")).flip();
                picked = TinyFileDialogs.tinyfd_openFileDialog(
                        Component.translatable("peaksound.upload.dialog").getString(), "", filters,
                        "MP3, FLAC, WAV, OGG", false);
            }
            String result = picked;
            dialogOpen = false;
            if (result != null) minecraft.execute(() -> load(Path.of(result)));
            else minecraft.execute(this::updateButtons);
        });
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty() && !dialogOpen) load(paths.get(0));
    }

    private void load(Path path) {
        file = path;
        info = null;
        data = null;
        error = null;
        updateButtons();
        Thread.ofVirtual().start(() -> {
            try {
                long size = Files.size(path);
                if (size > Limits.MAX_UPLOAD_BYTES) {
                    fail(Component.translatable("peaksound.upload.error.large"));
                    return;
                }
                AudioInfo inspected = AudioInspector.inspect(path);
                if (inspected.durationMs() > (ServerState.maxSongSeconds() + 1) * 1000) {
                    fail(Component.translatable("peaksound.upload.error.long", ServerState.maxSongSeconds() / 60));
                    return;
                }
                byte[] bytes = Files.readAllBytes(path);
                info = inspected;
                data = bytes;
                minecraft.execute(this::updateButtons);
            } catch (AudioException | IOException e) {
                fail(Component.translatable("peaksound.upload.error.format"));
            }
        });
    }

    private void fail(Component message) {
        error = message;
        minecraft.execute(this::updateButtons);
    }

    private void startUpload() {
        if (info == null || data == null || file == null) return;
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String title = dot > 0 ? name.substring(0, dot) : name;
        if (title.length() > Limits.MAX_TEXT) title = title.substring(0, Limits.MAX_TEXT);
        UploadClient.begin(ticket, title, data);
        updateButtons();
    }

    // ---- Darstellung ----

    @Override
    public void tick() {
        super.tick();
        updateButtons();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int y = height / 2 - 90;
        centered(g, title, cx, y, 0xFFFFFFFF);
        centered(g, Component.translatable("peaksound.upload.hint"), cx, y + 14, 0xFFAAAAAA);

        int line = height / 2 - 12;
        if (file != null) {
            centered(g, Component.literal(file.getFileName().toString()), cx, line, 0xFFFFFFFF);
        }
        AudioInfo i = info;
        if (i != null) {
            String title = i.title().isBlank() ? "-" : i.title();
            String artist = i.artist().isBlank() ? "-" : i.artist();
            centered(g, Component.literal(title + " - " + artist), cx, line + 12, 0xFFFFFFFF);
            int seconds = i.durationMs() / 1000;
            centered(g, Component.translatable("peaksound.upload.details", seconds / 60, String.format("%02d", seconds % 60),
                    i.channels(), i.sampleRate() / 1000.0), cx, line + 24, 0xFFAAAAAA);
            if (i.multichannel()) {
                centered(g, Component.translatable("peaksound.upload.multichannel.1"), cx, line + 38, 0xFFFFAA00);
                centered(g, Component.translatable("peaksound.upload.multichannel.2"), cx, line + 50, 0xFFFFAA00);
            }
        }
        if (error != null) centered(g, error, cx, line + 12, 0xFFFF5555);

        UploadClient.Phase phase = UploadClient.phase();
        if (phase == UploadClient.Phase.FAILED && UploadClient.reject() != null) {
            centered(g, rejectText(UploadClient.reject()), cx, height / 2 + 52, 0xFFFF5555);
        } else if (phase == UploadClient.Phase.SENDING || phase == UploadClient.Phase.WAITING_ACCEPT
                || phase == UploadClient.Phase.PROCESSING) {
            int barW = 200;
            int x0 = cx - barW / 2;
            int y0 = height / 2 + 54;
            g.fill(x0 - 1, y0 - 1, x0 + barW + 1, y0 + 9, 0xFF000000);
            g.fill(x0, y0, x0 + (int) (barW * UploadClient.progress()), y0 + 8, 0xFF55FF55);
            if (phase == UploadClient.Phase.PROCESSING) {
                centered(g, Component.translatable("peaksound.upload.processing"), cx, y0 + 12, 0xFFAAAAAA);
            }
        }
    }

    private static Component rejectText(UploadReject reject) {
        return switch (reject.reason()) {
            case TOO_LONG -> Component.translatable("peaksound.upload.error.long", reject.arg() / 60);
            case TOO_LARGE -> Component.translatable("peaksound.upload.error.large");
            case UNSUPPORTED_FORMAT, CORRUPT -> Component.translatable("peaksound.upload.error.format");
            case RATE_LIMIT -> Component.translatable("peaksound.upload.error.rate", reject.arg());
            case QUOTA_REACHED -> Component.translatable("peaksound.upload.error.quota", reject.arg());
            case STORAGE_FULL -> Component.translatable("peaksound.upload.error.storage");
            case NO_TICKET -> Component.translatable("peaksound.upload.error.ticket");
            case BUSY -> Component.translatable("peaksound.upload.error.busy");
            case INTERNAL -> Component.translatable("peaksound.upload.error.internal");
        };
    }

    private void centered(GuiGraphicsExtractor g, Component text, int cx, int y, int color) {
        g.text(font, text, cx - font.width(text) / 2, y, color);
    }
}
