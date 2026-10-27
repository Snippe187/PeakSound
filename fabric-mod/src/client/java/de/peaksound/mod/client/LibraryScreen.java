package de.peaksound.mod.client;

import de.peaksound.protocol.LibraryLogic;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Song-Bibliothek: Suche und Liste aller Songs, die auf dem Server hochgeladen wurden, mit Cover, Sortierung, Filter
 * (alle / Favoriten / meine) und Beliebtheit. Ein Klick auf "Auf Platte" schreibt den Song auf eine leere Platte aus dem
 * Inventar; eigene Songs lassen sich umbenennen und loeschen (Operatoren auch fremde). Der Server prueft alles; die Liste
 * kommt seitenweise von dort, Suche, Sortierung und Filter laufen ebenfalls dort.
 */
final class LibraryScreen extends Screen {
    private static final int ROW_HEIGHT = 22;
    private static final int WIDTH = 380;
    private static final long SEARCH_DELAY_MS = 300;
    private static LibraryScreen active;

    private final int bx, by, bz;
    private EditBox search;
    private EditBox renameBox;
    private Button renameOk;
    private final Button[] listen = new Button[Limits.LIBRARY_PAGE_SIZE];
    private final Button[] write = new Button[Limits.LIBRARY_PAGE_SIZE];
    private final Button[] fav = new Button[Limits.LIBRARY_PAGE_SIZE];
    private final Button[] edit = new Button[Limits.LIBRARY_PAGE_SIZE];
    private final Button[] del = new Button[Limits.LIBRARY_PAGE_SIZE];
    private Button prev, next, sortButton, filterButton;

    private String query = "";
    private LibraryLogic.Sort sort = LibraryLogic.Sort.TITLE;
    private LibraryLogic.Filter filter = LibraryLogic.Filter.ALL;
    private int page;
    private int pages = 1;
    private int total;
    private int blank;
    private boolean loaded;
    private List<Packet.LibrarySong> songs = List.of();
    private long searchAt;
    private boolean searchDirty;
    private long requeryAt;
    /** Song, der gerade umbenannt wird, bzw. dessen Loeschen noch bestaetigt werden muss (je null = keiner). */
    private String renameHash;
    private String confirmDeleteHash;

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

    private int left() { return width / 2 - WIDTH / 2; }

    private int top() { return height / 2 - 118; }

    private int bottomY() { return top() + 42 + Limits.LIBRARY_PAGE_SIZE * ROW_HEIGHT + 16; }

    @Override
    protected void init() {
        active = this;
        int left = left(), top = top();
        search = new EditBox(font, left, top + 16, 196, 20, Component.translatable("peaksound.menu.library.search"));
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

        sortButton = addRenderableWidget(Button.builder(sortLabel(), b -> {
            sort = sort.next();
            sendQuery(query, 0);
            b.setMessage(sortLabel());
        }).bounds(left + 200, top + 16, 90, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.sort"))).build());
        filterButton = addRenderableWidget(Button.builder(filterLabel(), b -> {
            filter = filter.next();
            sendQuery(query, 0);
            b.setMessage(filterLabel());
        }).bounds(left + 294, top + 16, 86, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.filter"))).build());

        for (int i = 0; i < Limits.LIBRARY_PAGE_SIZE; i++) {
            int row = i;
            int y = top + 42 + i * ROW_HEIGHT;
            listen[i] = addRenderableWidget(Button.builder(Component.literal("▶"), b -> togglePreview(row))
                    .bounds(left + 232, y, 20, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.listen"))).build());
            fav[i] = addRenderableWidget(Button.builder(Component.literal("☆"), b -> manage(row, Packet.LibraryManage.Kind.FAVORITE))
                    .bounds(left + 254, y, 20, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.favorite"))).build());
            edit[i] = addRenderableWidget(Button.builder(Component.literal("✎"), b -> startRename(row))
                    .bounds(left + 276, y, 20, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.rename"))).build());
            del[i] = addRenderableWidget(Button.builder(Component.literal("✖"), b -> deleteClicked(row))
                    .bounds(left + 298, y, 20, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.library.delete"))).build());
            write[i] = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.library.write"), b -> write(row))
                    .bounds(left + 320, y, 60, 20).build());
        }
        int bottom = bottomY();
        prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> goPage(page - 1)).bounds(left, bottom, 28, 20).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> goPage(page + 1)).bounds(left + 30, bottom, 28, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + 300, bottom, 80, 20).build());
        if (remote()) {
            addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.library.upload"), b -> backToUpload())
                    .bounds(left + 124, bottom, 170, 20).build());
        }

        renameBox = new EditBox(font, left, bottom + 26, 310, 20, Component.translatable("peaksound.menu.library.rename"));
        renameBox.setMaxLength(Limits.MAX_TEXT);
        renameBox.visible = false;
        addRenderableWidget(renameBox);
        renameOk = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.library.rename.ok"), b -> commitRename())
                .bounds(left + 314, bottom + 26, 66, 20).build());
        renameOk.visible = false;
        refresh();
        sendQuery(query, page); // beim Oeffnen und nach Fenstergroessen-Aenderung neu laden
    }

    private Component sortLabel() {
        return Component.translatable("peaksound.menu.library.sort." + sort.name().toLowerCase(java.util.Locale.ROOT));
    }

    private Component filterLabel() {
        return Component.translatable("peaksound.menu.library.filter." + filter.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void backToUpload() {
        UploadClient.reopen();
    }

    private void sendQuery(String q, int p) {
        ClientNetwork.send(new Packet.LibraryQuery(bx, by, bz, q, Math.max(0, p), sort, filter));
    }

    private void goPage(int p) {
        sendQuery(query, Math.max(0, Math.min(pages - 1, p)));
    }

    private void write(int row) {
        if (row >= songs.size() || blank <= 0) return;
        ClientNetwork.send(new Packet.LibraryWrite(bx, by, bz, songs.get(row).hash()));
        requeryAt = System.currentTimeMillis() + 350; // danach Anzahl der leeren Platten neu holen (der Server begrenzt die Abfragerate)
    }

    private void manage(int row, Packet.LibraryManage.Kind kind) {
        if (row >= songs.size()) return;
        ClientNetwork.send(new Packet.LibraryManage(bx, by, bz, kind, songs.get(row).hash(), ""));
        requeryAt = System.currentTimeMillis() + (kind == Packet.LibraryManage.Kind.DELETE ? 800 : 350);
    }

    private void deleteClicked(int row) {
        if (row >= songs.size()) return;
        String hash = songs.get(row).hash();
        if (hash.equals(confirmDeleteHash)) {
            confirmDeleteHash = null;
            manage(row, Packet.LibraryManage.Kind.DELETE);
        } else {
            confirmDeleteHash = hash; // erster Klick fragt nach, zweiter loescht
        }
        refresh();
    }

    private void startRename(int row) {
        if (row >= songs.size()) return;
        Packet.LibrarySong s = songs.get(row);
        renameHash = s.hash();
        renameBox.setValue(s.title());
        renameBox.visible = true;
        renameOk.visible = true;
        setFocused(renameBox);
    }

    private void commitRename() {
        if (renameHash == null) return;
        String text = renameBox.getValue().trim();
        if (!text.isEmpty()) {
            ClientNetwork.send(new Packet.LibraryManage(bx, by, bz, Packet.LibraryManage.Kind.RENAME, renameHash, text));
            requeryAt = System.currentTimeMillis() + 350;
        }
        renameHash = null;
        renameBox.visible = false;
        renameOk.visible = false;
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
            fav[i].visible = has;
            write[i].active = has && blank > 0;
            boolean mine = has && songs.get(i).mine();
            edit[i].visible = mine;
            del[i].visible = mine;
            if (has) {
                Packet.LibrarySong s = songs.get(i);
                listen[i].setMessage(Component.literal(PreviewPlayer.isActive(s.hash()) ? "■" : "▶"));
                fav[i].setMessage(Component.literal(s.favorite() ? "★" : "☆"));
                del[i].setMessage(Component.literal(s.hash().equals(confirmDeleteHash) ? "?" : "✖"));
            }
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
        while (cut.length() > 1 && font.width(cut + "…") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "…";
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
            CoverTooltip.draw(g, s.hash(), s.hasCover(), left, y, 20);
            g.text(font, Component.literal(fit(s.title(), 204)), left + 24, y + 1, 0xFFFFFFFF);
            String sub = (s.artist().isBlank() ? "" : s.artist() + "  ·  ") + s.uploader() + "  ·  " + time(s.durationMs())
                    + (s.plays() > 0 ? "  ·  " + s.plays() + "×" : "");
            g.text(font, Component.literal(fit(sub, 204)), left + 24, y + 11, 0xFF9A9AA4);
        }
        int bottom = bottomY();
        Component info = Component.translatable("peaksound.menu.library.status", page + 1, pages, total, blank);
        g.text(font, info, left + 2, bottom - 11, blank > 0 ? 0xFFAAAAAA : 0xFFFFAA00);
        if (confirmDeleteHash != null) {
            Component ask = Component.translatable("peaksound.menu.library.delete.confirm");
            g.text(font, ask, left + WIDTH - font.width(ask), bottom - 11, 0xFFFF5555);
        }
        if (blank <= 0 && loaded && renameHash == null) {
            Component hint = Component.translatable("peaksound.menu.library.noblank");
            g.text(font, hint, width / 2 - font.width(hint) / 2, bottom + 28, 0xFFFFAA00);
        }
    }
}
