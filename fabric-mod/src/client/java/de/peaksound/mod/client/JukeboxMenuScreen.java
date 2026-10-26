package de.peaksound.mod.client;

import de.peaksound.protocol.ChainMath;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.LoopMode;
import de.peaksound.protocol.Packet;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Das Jukebox-Menue in einem Fenster mit zwei Reitern: "Einstellungen" (Name, Reichweite, Lautstaerke, Kette,
 * Partikel, Jetzt-laeuft) und "Playlist" (CD-Wechsler: Auswahl, Skip, Reihenfolge, Loop, Shuffle).
 * Aenderungen werden gebuendelt an den Server geschickt; der Server prueft jeden Wert erneut.
 */
final class JukeboxMenuScreen extends Screen {
    private enum Tab { SETTINGS, PLAYLIST }

    /** Ganzzahliger Schieberegler mit Beschriftung. */
    private static final class IntSlider extends AbstractSliderButton {
        private final String key;
        private final int min;
        private final int max;
        private final IntConsumer onChange;
        private boolean silent;

        IntSlider(int x, int y, int w, String key, int min, int max, int value, IntConsumer onChange) {
            super(x, y, w, 20, Component.empty(), (double) (value - min) / (max - min));
            this.key = key;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
            updateMessage();
        }

        int intValue() { return min + (int) Math.round(value * (max - min)); }

        /** Wert von aussen setzen (z. B. durch den Ketten-Schalter), ohne erneut zu melden. */
        void set(int v) {
            silent = true;
            this.value = (double) (Math.max(min, Math.min(max, v)) - min) / (max - min);
            updateMessage();
            silent = false;
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable(key, intValue()));
        }

        @Override
        protected void applyValue() {
            if (!silent) onChange.accept(intValue());
        }
    }

    private static final long SEND_DELAY_MS = 250;
    private static final int PAGE_SIZE = 6;
    private static final int POLL_TICKS = 20;

    private static JukeboxMenuScreen active;

    private final int bx, by, bz;
    private JukeboxSettings cur;
    private boolean dirty;
    private long lastChange;
    private Tab tab = Tab.SETTINGS;

    // Zustand der Playlist (kommt vom Server)
    private Packet.PlaylistState playlist = new Packet.PlaylistState(0, 0, 0, -1, false, List.of());
    private String nowTitle;
    private String nowArtist;
    private boolean nowPlaying;
    private int page;
    private int pollTicks;

    private EditBox nameBox;
    private IntSlider range;
    private IntSlider volume;
    private IntSlider amount;
    private IntSlider density;
    private IntSlider heightSlider;
    private Button chainButton;
    private Button particlesButton;
    private Button rainbowButton;
    private Button nowPlayingButton;
    private Button loopButton;
    private Button shuffleButton;

    private JukeboxMenuScreen(Packet.OpenJukeboxMenu menu) {
        super(Component.translatable("peaksound.menu.jukebox.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.nowPlaying = menu.playing();
        this.nowTitle = menu.title();
        this.nowArtist = menu.artist();
    }

    static void open(Packet.OpenJukeboxMenu menu) {
        JukeboxMenuScreen screen = new JukeboxMenuScreen(menu);
        active = screen;
        Minecraft.getInstance().setScreenAndShow(screen);
    }

    /** Playlist-Zustand vom Server (nur fuer das gerade offene Menue dieser Jukebox). */
    static void onPlaylist(Packet.PlaylistState state) {
        JukeboxMenuScreen s = active;
        if (s == null || s.bx != state.x() || s.by != state.y() || s.bz != state.z()) return;
        boolean changed = !s.playlist.equals(state);
        s.playlist = state;
        s.nowPlaying = state.playing() && state.current() >= 0;
        if (state.current() >= 0 && state.current() < state.entries().size()) {
            s.nowTitle = state.entries().get(state.current()).title();
            s.nowArtist = state.entries().get(state.current()).artist();
        }
        int pages = Math.max(1, (state.entries().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        s.page = Math.min(s.page, pages - 1);
        if (changed && s.tab == Tab.PLAYLIST) s.rebuildWidgets();
    }

    // ---- Aufbau ----

    @Override
    protected void init() {
        int top = height / 2 - 100;
        int tabW = 120;
        Button settingsTab = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.tab.settings"), b -> switchTab(Tab.SETTINGS))
                .bounds(width / 2 - tabW - 2, top, tabW, 20).build());
        Button playlistTab = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.tab.playlist"), b -> switchTab(Tab.PLAYLIST))
                .bounds(width / 2 + 2, top, tabW, 20).build());
        settingsTab.active = tab != Tab.SETTINGS;
        playlistTab.active = tab != Tab.PLAYLIST;
        if (tab == Tab.SETTINGS) initSettings(top + 28);
        else initPlaylist(top + 28);
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, top + 196, 200, 20).build());
    }

    private void switchTab(Tab next) {
        tab = next;
        pollTicks = POLL_TICKS; // beim Wechsel sofort aktualisieren
        rebuildWidgets();
    }

    private void initSettings(int top) {
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int w = 150;

        nameBox = new EditBox(font, left, top, 310, 20, Component.translatable("peaksound.menu.name"));
        nameBox.setMaxLength(JukeboxSettings.MAX_NAME);
        nameBox.setHint(Component.translatable("peaksound.menu.name.hint"));
        nameBox.setValue(cur.name());
        nameBox.setResponder(text -> change(cur.withName(text)));
        addRenderableWidget(nameBox);

        range = addRenderableWidget(new IntSlider(left, top + 28, w, "peaksound.menu.range", Limits.MIN_RANGE,
                Limits.MAX_RANGE, cur.range(), this::onRange));
        volume = addRenderableWidget(new IntSlider(left, top + 52, w, "peaksound.menu.volume", 0, 100,
                cur.volume(), this::onVolume));
        chainButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withChain(!cur.chain()));
            refreshLabels();
        }).bounds(left, top + 76, w, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.chain.tooltip"))).build());
        nowPlayingButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withShowNowPlaying(!cur.showNowPlaying()));
            refreshLabels();
        }).bounds(left, top + 100, w, 20).build());

        particlesButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withParticles(!cur.particles()));
            refreshLabels();
        }).bounds(right, top + 28, w, 20).build());
        amount = addRenderableWidget(new IntSlider(right, top + 52, w, "peaksound.menu.amount", 1, 10,
                cur.particleAmount(), v -> change(cur.withParticleAmount(v))));
        density = addRenderableWidget(new IntSlider(right, top + 76, w, "peaksound.menu.density", 1, 10,
                cur.particleDensity(), v -> change(cur.withParticleDensity(v))));
        heightSlider = addRenderableWidget(new IntSlider(right, top + 100, w, "peaksound.menu.height", 0, 5,
                cur.particleHeight(), v -> change(cur.withParticleHeight(v))));
        rainbowButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withRainbow(!cur.rainbow()));
            refreshLabels();
        }).bounds(right, top + 124, w, 20).build());
        refreshLabels();
    }

    private void initPlaylist(int top) {
        int left = width / 2 - 155;
        List<Packet.PlaylistEntry> entries = playlist.entries();
        int first = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE && first + row < entries.size(); row++) {
            int index = first + row;
            int y = top + row * 20;
            Packet.PlaylistEntry e = entries.get(index);
            Button play = addRenderableWidget(Button.builder(Component.literal("▶"), b -> send(Packet.PlaylistAction.Action.PLAY, index))
                    .bounds(left, y, 20, 18).tooltip(Tooltip.create(Component.translatable("peaksound.menu.playlist.play"))).build());
            play.active = e.available();
            Button up = addRenderableWidget(Button.builder(Component.literal("▲"), b -> send(Packet.PlaylistAction.Action.MOVE_UP, index))
                    .bounds(left + 268, y, 20, 18).build());
            up.active = index > 0;
            Button down = addRenderableWidget(Button.builder(Component.literal("▼"), b -> send(Packet.PlaylistAction.Action.MOVE_DOWN, index))
                    .bounds(left + 290, y, 20, 18).build());
            down.active = index < entries.size() - 1;
        }
        int controls = top + PAGE_SIZE * 20 + 6;
        addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.skip"), b -> send(Packet.PlaylistAction.Action.SKIP, 0))
                .bounds(left, controls, 74, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.stop"), b -> send(Packet.PlaylistAction.Action.STOP, 0))
                .bounds(left + 78, controls, 74, 20).build());
        loopButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withLoop(cur.loop().next()));
            refreshLabels();
        }).bounds(left + 156, controls, 154, 20).build());
        shuffleButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withShuffle(!cur.shuffle()));
            refreshLabels();
        }).bounds(left, controls + 24, 154, 20).build());
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            page = Math.max(0, page - 1);
            rebuildWidgets();
        }).bounds(left + 156, controls + 24, 40, 20).build());
        prev.active = page > 0;
        Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            page = Math.min(pages - 1, page + 1);
            rebuildWidgets();
        }).bounds(left + 270, controls + 24, 40, 20).build());
        next.active = page < pages - 1;
        refreshLabels();
    }

    private void send(Packet.PlaylistAction.Action action, int index) {
        ClientNetwork.send(new Packet.PlaylistAction(bx, by, bz, action, index));
    }

    // ---- Logik ----

    private void onRange(int value) {
        if (cur.chain()) {
            ChainMath.Pair p = ChainMath.adjustRange(cur.range(), cur.volume(), value);
            change(cur.withRangeAndVolume(p.range(), p.volume()));
            range.set(p.range());
            volume.set(p.volume());
        } else {
            change(cur.withRangeAndVolume(value, cur.volume()));
        }
    }

    private void onVolume(int value) {
        if (cur.chain()) {
            ChainMath.Pair p = ChainMath.adjustVolume(cur.range(), cur.volume(), value);
            change(cur.withRangeAndVolume(p.range(), p.volume()));
            range.set(p.range());
            volume.set(p.volume());
        } else {
            change(cur.withRangeAndVolume(cur.range(), value));
        }
    }

    private void refreshLabels() {
        if (tab == Tab.SETTINGS) {
            chainButton.setMessage(toggle("peaksound.menu.chain", cur.chain()));
            nowPlayingButton.setMessage(toggle("peaksound.menu.nowplaying", cur.showNowPlaying()));
            particlesButton.setMessage(toggle("peaksound.menu.particles", cur.particles()));
            rainbowButton.setMessage(toggle("peaksound.menu.rainbow", cur.rainbow()));
            boolean p = cur.particles();
            amount.active = p;
            density.active = p;
            heightSlider.active = p;
            rainbowButton.active = p;
        } else {
            loopButton.setMessage(Component.translatable("peaksound.menu.loop",
                    Component.translatable("peaksound.menu.loop." + cur.loop().name().toLowerCase())));
            shuffleButton.setMessage(toggle("peaksound.menu.shuffle", cur.shuffle()));
        }
    }

    private static Component toggle(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
    }

    private void change(JukeboxSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.JukeboxUpdate(bx, by, bz, cur));
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty && System.currentTimeMillis() - lastChange > SEND_DELAY_MS) flush();
        if (tab == Tab.PLAYLIST && ++pollTicks >= POLL_TICKS) {
            pollTicks = 0;
            ClientNetwork.send(new Packet.PlaylistRequest(bx, by, bz));
        }
    }

    @Override
    public void removed() {
        flush();
        if (active == this) active = null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ---- Darstellung ----

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 100;
        Component title = cur.name().isBlank() ? Component.translatable("peaksound.menu.jukebox.title") : Component.literal(cur.name());
        g.text(font, title, width / 2 - font.width(title) / 2, top - 22, 0xFFFFFFFF);
        Component now = nowPlaying
                ? Component.translatable("peaksound.menu.playing", nowTitle, nowArtist.isBlank() ? "-" : nowArtist)
                : Component.translatable("peaksound.menu.nothing");
        g.text(font, now, width / 2 - font.width(now) / 2, top - 10, 0xFFAAAAAA);

        if (tab == Tab.PLAYLIST) extractPlaylist(g, top + 28);
    }

    private void extractPlaylist(GuiGraphicsExtractor g, int top) {
        int left = width / 2 - 155;
        List<Packet.PlaylistEntry> entries = playlist.entries();
        if (entries.isEmpty()) {
            Component hint = Component.translatable("peaksound.menu.playlist.empty");
            g.text(font, hint, width / 2 - font.width(hint) / 2, top + 20, 0xFFAAAAAA);
            Component hint2 = Component.translatable("peaksound.menu.playlist.empty2");
            g.text(font, hint2, width / 2 - font.width(hint2) / 2, top + 34, 0xFF777777);
            return;
        }
        int first = page * PAGE_SIZE;
        for (int row = 0; row < PAGE_SIZE && first + row < entries.size(); row++) {
            int index = first + row;
            Packet.PlaylistEntry e = entries.get(index);
            int seconds = e.durationMs() / 1000;
            String text = (index + 1) + ". " + e.title() + (e.artist().isBlank() ? "" : " - " + e.artist())
                    + String.format(" (%d:%02d)", seconds / 60, seconds % 60);
            // Zu lange Zeilen kuerzen, damit sie nicht in die Knoepfe laufen
            while (font.width(text) > 236 && text.length() > 4) text = text.substring(0, text.length() - 4) + "...";
            int color = index == playlist.current() ? 0xFFFFAA00 : e.available() ? 0xFFFFFFFF : 0xFF777777;
            g.text(font, text, left + 26, top + row * 20 + 5, color);
        }
        int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        Component pageText = Component.translatable("peaksound.menu.playlist.page", page + 1, pages);
        g.text(font, pageText, left + 233 - font.width(pageText) / 2, top + PAGE_SIZE * 20 + 6 + 30, 0xFFAAAAAA);
    }
}
