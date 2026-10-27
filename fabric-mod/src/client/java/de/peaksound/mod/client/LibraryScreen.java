package de.peaksound.mod.client;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Song-Bibliothek: Suche und Liste aller Songs, die auf dem Server hochgeladen wurden. Ein Klick auf "Auf Platte"
 * schreibt den Song auf eine leere Platte aus dem Inventar (der Server prueft und fuehrt das aus). Die Liste kommt
 * seitenweise vom Server, die Suche laeuft ebenfalls dort.
 */
final class LibraryScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final long SEARCH_DELAY_MS = 300;
    private static LibraryScreen active;

    private final int bx, by, bz;
    private EditBox search;
    private final Button[] listen = new Button[Limits.LIBRARY_PAGE_SIZE];
    private final Button[] write = new Button[Limits.LIBRARY_PAGE_SIZE];
    private Button prev, next;

    private String query = "";
    private int page;
    private int pages = 1;
    private int total;
    private int blank;
    private boolean loaded;
    private List<Packet.LibrarySong> songs = List.of();
    private long searchAt;
    private boolean searchDirty;
    private long requeryAt;

    private LibraryScreen(Packet.OpenLibraryMenu menu) {
        super(Component.translatable("peaksound.menu.library.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
    }

    static void open(Packet.OpenLibraryMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new LibraryScreen(menu));
    }

    /** Song-Browser aus dem Upload-Fenster: ohne Block, der Server erlaubt es nur mit gueltigem Upload-Ticket. */
    static void openFromUpload() {
        int r = Limits.LIBRARY_REMOTE;
        Minecraft.getInstance().setScreenAndShow(new LibraryScreen(new Packet.OpenLibraryMenu(r, r, r)));
    }

    private boolean remote() { return bx == Limits.LIBRARY_REMOTE; }

    static void onResults(Packet.LibraryResults r) {
        LibraryScreen s = active;
        if (s == null || s.bx != r.x() || s.by != r.y() || s.bz != r.z()) return;
        s.page = r.page();
        s.pages = r.pages();
        s.total = r.total();
        s.blank = r.blank();
        s.songs = r.songs();
        s.loaded = true;
        s.refresh();
    }

    private int left() { return width / 2 - 160; }

    private int top() { return height / 2 - 110; }

    @Override
    protected void init() {
        active = this;
        int left = left(), top = top();
        search = new EditBox(font, left, top + 16, 320, 20, Component.translatable("peaksound.menu.library.search"));
        search.setMaxLength(Limits.LIBRARY_MAX_QUERY);
        search.setHint(Component.translatable("peaksound.menu.library.search.hint"));
        search.setValue(query);
        search.setResponder(text -> {
            query = text;
            searchDirty = true;
            searchAt = System.currentTimeMillis();
        });
        addRenderableWidget(search);
        setInitialFocus(search);

        for (int i = 0; i < Limits.LIBRARY_PAGE_SIZE; i++) {
            int row = i;
            int y = top + 42 + i * ROW_HEIGHT;
            listen[i] = addRenderableWidget(Button.builder(Component.literal("\u25B6"), b -> togglePreview(row))
                    .bounds(left + 236, y, 20, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.listen"))).build());
            write[i] = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.library.write"), b -> write(row))
                    .bounds(left + 260, y, 60, 20).build());
        }
        int bottom = top + 42 + Limits.LIBRARY_PAGE_SIZE * ROW_HEIGHT + 16;
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> goPage(page - 1)).bounds(left, bottom, 28, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> goPage(page + 1)).bounds(left + 30, bottom, 28, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 240, bottom, 80, 20).build());
        if (remote()) {
            addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.library.upload"), b -> backToUpload())
                    .bounds(left + 64, bottom, 170, 20).build());
        }
        refresh();
        sendQuery(query, page); // beim Oeffnen und nach Fenstergroessen-Aenderung neu laden
    }

    private void backToUpload() {
        UploadClient.reopen();
    }

    private void sendQuery(String q, int p) {
        ClientNetwork.send(new Packet.LibraryQuery(bx, by, bz, q, Math.max(0, p)));
    }

    private void goPage(int p) {
        sendQuery(query, Math.max(0, Math.min(pages - 1, p)));
    }

    private void write(int row) {
        if (row >= songs.size() || blank <= 0) return;
        ClientNetwork.send(new Packet.LibraryWrite(bx, by, bz, songs.get(row).hash()));
        requeryAt = System.currentTimeMillis() + 350; // danach Anzahl der leeren Platten neu holen (der Server begrenzt die Abfragerate)
    }

    private void togglePreview(int row) {
        if (row >= songs.size()) return;
        String hash = songs.get(row).hash();
        if (PreviewPlayer.isActive(hash)) PreviewPlayer.stop();
        else PreviewPlayer.play(hash);
        refresh();
    }

    private void refresh() {
        if (prev == null) return;
        for (int i = 0; i < Limits.LIBRARY_PAGE_SIZE; i++) {
            boolean has = i < songs.size();
            listen[i].visible = has;
            write[i].visible = has;
            write[i].active = has && blank > 0;
            if (has) listen[i].setMessage(Component.literal(PreviewPlayer.isActive(songs.get(i).hash()) ? "\u25A0" : "\u25B6"));
        }
        prev.active = page > 0;
        next.active = page < pages - 1;
    }

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();
        if (searchDirty && now - searchAt > SEARCH_DELAY_MS) {
            searchDirty = false;
            sendQuery(query, 0);
        }
        if (requeryAt != 0 && now >= requeryAt) {
            requeryAt = 0;
            sendQuery(query, page);
        }
        refresh();
    }

    @Override
    public void removed() {
        if (active == this) active = null;
        PreviewPlayer.stop();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private String fit(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String cut = s;
        while (cut.length() > 1 && font.width(cut + "\u2026") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "\u2026";
    }

    private static String time(int ms) {
        int s = ms / 1000;
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int left = left(), top = top();
        g.text(font, title, width / 2 - font.width(title) / 2, top, 0xFFFFFFFF);
        if (!loaded) {
            Component loading = Component.translatable("peaksound.menu.library.loading");
            g.text(font, loading, width / 2 - font.width(loading) / 2, top + 80, 0xFFAAAAAA);
        } else if (songs.isEmpty()) {
            Component none = Component.translatable("peaksound.menu.library.empty");
            g.text(font, none, width / 2 - font.width(none) / 2, top + 80, 0xFFAAAAAA);
        }
        for (int i = 0; i < songs.size(); i++) {
            Packet.LibrarySong s = songs.get(i);
            int y = top + 42 + i * ROW_HEIGHT;
            g.text(font, Component.literal(fit(s.title(), 228)), left + 2, y + 1, 0xFFFFFFFF);
            String sub = (s.artist().isBlank() ? "" : s.artist() + "  \u00B7  ") + s.uploader() + "  \u00B7  " + time(s.durationMs());
            g.text(font, Component.literal(fit(sub, 228)), left + 2, y + 11, 0xFF9A9AA4);
        }
        int bottom = top + 42 + Limits.LIBRARY_PAGE_SIZE * ROW_HEIGHT + 16;
        Component info = Component.translatable("peaksound.menu.library.status", page + 1, pages, total, blank);
        g.text(font, info, left + 2, bottom - 11, blank > 0 ? 0xFFAAAAAA : 0xFFFFAA00);
        if (blank <= 0 && loaded) {
            Component hint = Component.translatable("peaksound.menu.library.noblank");
            g.text(font, hint, width / 2 - font.width(hint) / 2, bottom + 24, 0xFFFFAA00);
        }
    }
}