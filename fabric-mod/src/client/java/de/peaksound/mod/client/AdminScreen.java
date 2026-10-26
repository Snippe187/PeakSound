package de.peaksound.mod.client;

import de.peaksound.protocol.LimitLogic;
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
 * Song-Verwaltung fuer Operatoren (Escape-Menue): alle Songs ansehen, vorhoeren und loeschen, gesperrte
 * Mehrkanal-Dateien freigeben oder ablehnen sowie Upload- und Speicher-Limits einstellen. Der Server prueft bei
 * jeder Aktion erneut, ob der Spieler Operator ist.
 */
final class AdminScreen extends Screen {
    private enum Tab { SONGS, APPROVAL, LIMITS }

    private static final int ROWS = 8;
    private static final int PLAYER_ROWS = 4;
    private static final int POLL_TICKS = 100;
    private static final long CONFIRM_MS = 3000;

    private static AdminScreen active;
    private static Packet.AdminSongs data = new Packet.AdminSongs(List.of(), 0, 0, 0, 0, List.of());

    private final Screen parent;
    private Tab tab = Tab.SONGS;
    private int page;
    private int pollTicks;
    /** Zweiklick-Bestaetigung fuer Loeschen/Ablehnen: Hash der Zeile und Ablaufzeit. */
    private String confirmHash;
    private long confirmUntil;
    private EditBox storageBox;
    private EditBox defaultBox;

    private AdminScreen(Screen parent) {
        super(Component.translatable("peaksound.admin.title"));
        this.parent = parent;
    }

    static void open(Screen parent) {
        AdminScreen screen = new AdminScreen(parent);
        active = screen;
        Minecraft.getInstance().setScreenAndShow(screen);
        ClientNetwork.send(new Packet.AdminListRequest());
    }

    static void onData(Packet.AdminSongs songs) {
        data = songs;
        AdminScreen s = active;
        if (s != null) s.rebuildWidgets();
    }

    // ---- Aufbau ----

    private List<Packet.AdminSong> visibleSongs() {
        if (tab == Tab.APPROVAL) {
            List<Packet.AdminSong> pending = new ArrayList<>();
            for (Packet.AdminSong s : data.songs()) if (s.pending()) pending.add(s);
            return pending;
        }
        return data.songs();
    }

    private int pendingCount() {
        int n = 0;
        for (Packet.AdminSong s : data.songs()) if (s.pending()) n++;
        return n;
    }

    @Override
    protected void init() {
        int top = height / 2 - 105;
        int left = width / 2 - 160;
        addTab(Tab.SONGS, Component.translatable("peaksound.admin.tab.songs"), left, top);
        addTab(Tab.APPROVAL, Component.translatable("peaksound.admin.tab.approval", pendingCount()), left + 108, top);
        addTab(Tab.LIMITS, Component.translatable("peaksound.admin.tab.limits"), left + 216, top);
        if (tab == Tab.LIMITS) initLimits(top + 28);
        else initSongs(top + 28);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, top + 224, 200, 20).build());
    }

    private void addTab(Tab target, Component label, int x, int y) {
        Button b = addRenderableWidget(Button.builder(label, btn -> {
            tab = target;
            page = 0;
            confirmHash = null;
            rebuildWidgets();
        }).bounds(x, y, 104, 20).build());
        b.active = tab != target;
    }

    private void initSongs(int top) {
        int left = width / 2 - 160;
        List<Packet.AdminSong> list = visibleSongs();
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        page = Math.min(page, pages - 1);
        for (int row = 0; row < ROWS && page * ROWS + row < list.size(); row++) {
            Packet.AdminSong song = list.get(page * ROWS + row);
            int y = top + row * 20;
            boolean playing = PreviewPlayer.isActive(song.hash());
            addRenderableWidget(Button.builder(Component.literal(playing ? "■" : "▶"), b -> {
                if (PreviewPlayer.isActive(song.hash())) PreviewPlayer.stop();
                else PreviewPlayer.play(song.hash());
                rebuildWidgets();
            }).bounds(left + 232, y, 22, 18).tooltip(Tooltip.create(Component.translatable("peaksound.admin.listen"))).build());
            int x = left + 258;
            if (song.pending()) {
                addRenderableWidget(Button.builder(Component.literal("✓"), b -> action(Packet.AdminAction.Action.APPROVE_SONG, song.hash()))
                        .bounds(x, y, 22, 18).tooltip(Tooltip.create(Component.translatable("peaksound.admin.approve"))).build());
                x += 24;
            }
            boolean confirming = song.hash().equals(confirmHash) && System.currentTimeMillis() < confirmUntil;
            Packet.AdminAction.Action removal = song.pending() ? Packet.AdminAction.Action.REJECT_SONG : Packet.AdminAction.Action.DELETE_SONG;
            addRenderableWidget(Button.builder(Component.literal(confirming ? "?" : "✗"), b -> {
                if (song.hash().equals(confirmHash) && System.currentTimeMillis() < confirmUntil) {
                    confirmHash = null;
                    action(removal, song.hash());
                } else {
                    confirmHash = song.hash();
                    confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
                    rebuildWidgets();
                }
            }).bounds(x, y, 22, 18).tooltip(Tooltip.create(Component.translatable(
                    confirming ? "peaksound.admin.confirm" : song.pending() ? "peaksound.admin.reject" : "peaksound.admin.delete"))).build());
        }
        navigation(top + ROWS * 20 + 4, pages);
    }

    private void navigation(int y, int pages) {
        int left = width / 2 - 160;
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; rebuildWidgets(); })
                .bounds(left, y, 40, 18).build());
        prev.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; rebuildWidgets(); })
                .bounds(left + 280, y, 40, 18).build());
        next.active = page < pages - 1;
    }

    private void initLimits(int top) {
        int left = width / 2 - 160;
        // Speicher-Limit in MB (0 = unbegrenzt)
        storageBox = new EditBox(font, left + 120, top + 14, 90, 18, Component.translatable("peaksound.admin.limits.storage"));
        storageBox.setValue(String.valueOf(data.maxBytes() / (1024 * 1024)));
        storageBox.setMaxLength(12);
        addRenderableWidget(storageBox);
        addRenderableWidget(Button.builder(Component.translatable("peaksound.admin.set"), b -> sendNumber(Packet.AdminAction.Action.SET_STORAGE_MB, storageBox))
                .bounds(left + 214, top + 13, 60, 20).build());
        // Standard-Songlimit pro Spieler (0 = unbegrenzt)
        defaultBox = new EditBox(font, left + 120, top + 44, 90, 18, Component.translatable("peaksound.admin.limits.default"));
        defaultBox.setValue(String.valueOf(data.defaultLimit()));
        defaultBox.setMaxLength(6);
        addRenderableWidget(defaultBox);
        addRenderableWidget(Button.builder(Component.translatable("peaksound.admin.set"), b -> sendNumber(Packet.AdminAction.Action.SET_DEFAULT_LIMIT, defaultBox))
                .bounds(left + 214, top + 43, 60, 20).build());

        // Spieler-Limits
        List<Packet.AdminPlayer> players = data.players();
        int pages = Math.max(1, (players.size() + PLAYER_ROWS - 1) / PLAYER_ROWS);
        page = Math.min(page, pages - 1);
        int listTop = top + 90;
        for (int row = 0; row < PLAYER_ROWS && page * PLAYER_ROWS + row < players.size(); row++) {
            Packet.AdminPlayer p = players.get(page * PLAYER_ROWS + row);
            int y = listTop + row * 20;
            int current = p.max() == LimitLogic.USE_DEFAULT ? data.defaultLimit() : p.max();
            addRenderableWidget(Button.builder(Component.literal("-"), b -> playerLimit(p, Math.max(0, current - 1)))
                    .bounds(left + 192, y, 20, 18).build());
            addRenderableWidget(Button.builder(Component.literal("+"), b -> playerLimit(p, current + 1))
                    .bounds(left + 214, y, 20, 18).build());
            Button reset = addRenderableWidget(Button.builder(Component.translatable("peaksound.admin.default"), b -> playerLimit(p, LimitLogic.USE_DEFAULT))
                    .bounds(left + 238, y, 82, 18).build());
            reset.active = p.max() != LimitLogic.USE_DEFAULT;
        }
        navigation(listTop + PLAYER_ROWS * 20 + 2, pages);
    }

    private void playerLimit(Packet.AdminPlayer p, long value) {
        ClientNetwork.send(new Packet.AdminAction(Packet.AdminAction.Action.SET_PLAYER_LIMIT, "", p.uuidHigh(), p.uuidLow(), value));
    }

    private void sendNumber(Packet.AdminAction.Action action, EditBox box) {
        try {
            long value = Long.parseLong(box.getValue().trim());
            if (value >= 0) ClientNetwork.send(new Packet.AdminAction(action, "", 0, 0, Math.min(value, LimitLogic.MAX_STORAGE_MB)));
        } catch (NumberFormatException ignored) { /* ungueltige Eingabe: nichts senden */ }
    }

    private void action(Packet.AdminAction.Action action, String hash) {
        ClientNetwork.send(new Packet.AdminAction(action, hash, 0, 0, 0));
    }

    // ---- Ablauf ----

    @Override
    public void tick() {
        super.tick();
        if (++pollTicks >= POLL_TICKS) {
            pollTicks = 0;
            ClientNetwork.send(new Packet.AdminListRequest());
        }
        if (confirmHash != null && System.currentTimeMillis() >= confirmUntil) {
            confirmHash = null;
            rebuildWidgets();
        }
    }

    @Override
    public void removed() {
        PreviewPlayer.stop();
        if (active == this) active = null;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- Darstellung ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 105;
        g.text(font, title, width / 2 - font.width(title) / 2, top - 14, 0xFFFFFFFF);
        if (tab == Tab.LIMITS) extractLimits(g, top + 28);
        else extractSongs(g, top + 28);
    }

    private void extractSongs(GuiGraphicsExtractor g, int top) {
        int left = width / 2 - 160;
        List<Packet.AdminSong> list = visibleSongs();
        if (list.isEmpty()) {
            Component hint = Component.translatable(tab == Tab.APPROVAL ? "peaksound.admin.empty.approval" : "peaksound.admin.empty.songs");
            g.text(font, hint, width / 2 - font.width(hint) / 2, top + 40, 0xFFAAAAAA);
            return;
        }
        for (int row = 0; row < ROWS && page * ROWS + row < list.size(); row++) {
            Packet.AdminSong s = list.get(page * ROWS + row);
            int y = top + row * 20;
            int seconds = s.durationMs() / 1000;
            String artist = s.artist().isBlank() ? "" : " - " + s.artist();
            String line1 = s.title() + artist;
            String line2 = s.uploader() + " · " + seconds / 60 + ":" + String.format("%02d", seconds % 60) + " · "
                    + s.channels() + "ch · " + String.format("%.1f MB", s.sizeBytes() / 1048576.0);
            g.text(font, trim(line1, 226), left, y, s.pending() ? 0xFFFFAA00 : 0xFFFFFFFF);
            g.text(font, trim(line2, 226), left, y + 9, 0xFF888888);
        }
        int pages = Math.max(1, (list.size() + ROWS - 1) / ROWS);
        Component info = Component.translatable("peaksound.admin.page", page + 1, pages, data.totalSongs());
        g.text(font, info, width / 2 - font.width(info) / 2, top + ROWS * 20 + 9, 0xFFAAAAAA);
    }

    private void extractLimits(GuiGraphicsExtractor g, int top) {
        int left = width / 2 - 160;
        long usedMb = data.usedBytes() / (1024 * 1024);
        long maxMb = data.maxBytes() / (1024 * 1024);
        // Speicher-Balken
        g.text(font, Component.translatable("peaksound.admin.limits.storage"), left, top + 18, 0xFFFFFFFF);
        g.text(font, Component.translatable("peaksound.admin.limits.used", usedMb, maxMb == 0 ? "∞" : String.valueOf(maxMb)), left, top, 0xFFAAAAAA);
        g.text(font, Component.translatable("peaksound.admin.limits.default"), left, top + 48, 0xFFFFFFFF);
        g.text(font, Component.translatable("peaksound.admin.limits.zero"), left + 120, top + 66, 0xFF777777);
        g.text(font, Component.translatable("peaksound.admin.limits.players"), left, top + 76, 0xFFAAAAAA);
        int listTop = top + 90;
        List<Packet.AdminPlayer> players = data.players();
        for (int row = 0; row < PLAYER_ROWS && page * PLAYER_ROWS + row < players.size(); row++) {
            Packet.AdminPlayer p = players.get(page * PLAYER_ROWS + row);
            int y = listTop + row * 20 + 5;
            int limit = p.max() == LimitLogic.USE_DEFAULT ? data.defaultLimit() : p.max();
            String limitText = limit == 0 ? "∞" : String.valueOf(limit);
            g.text(font, trim(p.name(), 110), left, y, 0xFFFFFFFF);
            g.text(font, p.songs() + " / " + limitText, left + 130, y, p.max() == LimitLogic.USE_DEFAULT ? 0xFFAAAAAA : 0xFFFFAA00);
        }
    }

    private String trim(String text, int maxWidth) {
        String t = text;
        while (font.width(t) > maxWidth && t.length() > 4) t = t.substring(0, t.length() - 4) + "...";
        return t;
    }
}
