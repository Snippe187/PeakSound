package de.peaksound.mod.client;

import de.peaksound.protocol.ChainMath;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.LoopMode;
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
 * Das Jukebox-Menue in einem Fenster mit zwei Reitern: "Einstellungen" (Name, Reichweite, Lautstaerke, Kette,
 * Partikel, Jetzt-laeuft) und "Playlist" (CD-Wechsler: Auswahl, Skip, Reihenfolge, Loop, Shuffle).
 * Aenderungen werden gebuendelt an den Server geschickt; der Server prueft jeden Wert erneut.
 */
final class JukeboxMenuScreen extends Screen {
    private enum Tab { SETTINGS, PLAYLIST, SPEAKERS, SIDES }

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
    private List<Packet.SpeakerEntry> speakerEntries = List.of();

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
    private Button upmixButton;

    // Reiter "Seiten": drehbarer Wuerfel und Auswahl der Ausgangsfunktion je Comparator-Seite
    private final CubeView cube = new CubeView();
    private boolean draggingCube;

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

    /** Lautsprecher-Uebersicht vom Server (Reiter "Lautsprecher"). */
    static void onSpeakers(Packet.SpeakerOverview overview) {
        JukeboxMenuScreen s = active;
        if (s == null || s.bx != overview.x() || s.by != overview.y() || s.bz != overview.z()) return;
        s.speakerEntries = overview.speakers();
    }

    // ---- Aufbau ----

    @Override
    protected void init() {
        int top = height / 2 - 100;
        int tabW = 75;
        int tabLeft = width / 2 - 155;
        Button settingsTab = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.tab.settings"), b -> switchTab(Tab.SETTINGS))
                .bounds(tabLeft, top, tabW, 20).build());
        Button playlistTab = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.tab.playlist"), b -> switchTab(Tab.PLAYLIST))
                .bounds(tabLeft + 78, top, tabW, 20).build());
        Button speakersTab = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.tab.speakers"), b -> switchTab(Tab.SPEAKERS))
                .bounds(tabLeft + 156, top, tabW, 20).build());
        Button sidesTab = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.tab.sides"), b -> switchTab(Tab.SIDES))
                .bounds(tabLeft + 234, top, tabW, 20).build());
        settingsTab.active = tab != Tab.SETTINGS;
        playlistTab.active = tab != Tab.PLAYLIST;
        speakersTab.active = tab != Tab.SPEAKERS;
        sidesTab.active = tab != Tab.SIDES;        if (tab == Tab.SETTINGS) initSettings(top + 28);
        else if (tab == Tab.PLAYLIST) initPlaylist(top + 28);
        else if (tab == Tab.SIDES) initSides(top + 28);
        else initSpeakersTab(top + 28);
        // Im Lautsprecher-Reiter teilen sich Upmix-Schalter und Fertig-Knopf die Zeile
        boolean shared = tab == Tab.SPEAKERS;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(shared ? width / 2 + 57 : width / 2 - 100, top + 196, shared ? 98 : 200, 20).build());
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

    /** Reiter "Lautsprecher": Schalter fuer den Stereo-Upmix (Anzeige der Lautsprecher zeichnet extractSpeakers). */
    private void initSpeakersTab(int top) {
        upmixButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withUpmix(!cur.upmix()));
            refreshLabels();
        }).bounds(width / 2 - 155, top + 168, 208, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.upmix.tooltip"))).build());
        refreshLabels();
    }

    /** Reiter "Seiten": Der Wuerfel zeigt die vier Comparator-Seiten der Jukebox; rechts waehlst du die Funktion der gewaehlten Seite. */
    private void initSides(int top) {
        int right = width / 2 + 20;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            change(cur.withPort(cube.selected, cur.ports().get(cube.selected).previous(cube.selected)));
        }).bounds(right, top + 50, 22, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            change(cur.withPort(cube.selected, cur.ports().get(cube.selected).next(cube.selected)));
        }).bounds(right + 118, top + 50, 22, 20).build());
    }

    private static int portColor(de.peaksound.protocol.PortFunction f) {
        return switch (f) {
            case BASS -> 0xE04848;
            case MID -> 0x48C860;
            case HIGH -> 0x4890E8;
            case OVERALL -> 0xE0C040;
            case RUNNING -> 0x40D0D0;
            case SONG_END -> 0xE08A30;
            case BEAT -> 0xC050D0;
            case OFF -> 0x55555F;
            case ITEM_IN -> 0x4F9EFF;
            case ITEM_OUT -> 0xFF7A4F;
        };
    }

    private static String portShort(de.peaksound.protocol.PortFunction f) {
        return switch (f) {
            case BASS -> "BAS";
            case MID -> "MID";
            case HIGH -> "HI";
            case OVERALL -> "ALL";
            case RUNNING -> "RUN";
            case SONG_END -> "END";
            case BEAT -> "BEAT";
            case OFF -> "-";
            case ITEM_IN -> "IN";
            case ITEM_OUT -> "OUT";
        };
    }

    private void extractSides(GuiGraphicsExtractor g, int top) {
        int left = width / 2 - 155;
        int[] fill = new int[6];
        String[] l1 = new String[6], l2 = new String[6];
        for (int f = 0; f < 6; f++) {
            l1[f] = "";
            de.peaksound.protocol.PortFunction fn = cur.ports().get(f);
            fill[f] = portColor(fn);
            l2[f] = portShort(fn);
        }
        cube.render(g, font, left + 78, top + 74, 84, fill, l1, l2);
        Component drag = Component.translatable("peaksound.menu.sides.drag");
        g.text(font, drag, left + 78 - font.width(drag) / 2, top + 142, 0xFF777777);

        int right = width / 2 + 20;
        Component face = Component.translatable("peaksound.menu.sides.face." + CubeView.NAMES_KEYS[cube.selected]);
        g.text(font, face, right, top + 28, 0xFFFFFFFF);
        de.peaksound.protocol.PortFunction fn = cur.ports().get(cube.selected);
        Component name = Component.translatable("peaksound.port." + fn.key());
        g.text(font, name, right + 70 - font.width(name) / 2, top + 56, 0xFF000000 | portColor(fn));
        Component desc = Component.translatable("peaksound.port." + fn.key() + ".desc");
        g.text(font, desc, right, top + 80, 0xFFAAAAAA);
        Component hint = Component.translatable(fn.item() ? "peaksound.menu.sides.hint.item" : "peaksound.menu.sides.hint");
        if (fn.item() || cube.selected < 4) g.text(font, hint, right, top + 104, 0xFF777777);
    }
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        if (tab == Tab.PLAYLIST && clickPlaylist(event.x(), event.y(), event.button())) return true;
        if (tab == Tab.SIDES && event.button() == 0) {
            int face = cube.pick(event.x(), event.y());
            if (face >= 0) {
                cube.selected = face;
                draggingCube = true;
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (tab == Tab.SIDES && event.button() == 0 && (draggingCube || cube.pick(event.x(), event.y()) >= 0)) {
            cube.drag(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        draggingCube = false;
        return super.mouseReleased(event);
    }

    // ---- Reiter "Playlist": eigenes Inventar der Jukebox (15 Slots) ----

    private static final int SLOT = 18;
    private int selectedSlot = -1;

    private int gridX() { return width / 2 - 155; }

    private int invX() { return width / 2 - 81; }

    /** Der Eintrag im Jukebox-Slot (oder null). */
    private Packet.PlaylistEntry entryAt(int slot) {
        for (Packet.PlaylistEntry e : playlist.entries()) if (e.slot() == slot) return e;
        return null;
    }

    private int currentSlot() {
        int cur = playlist.current();
        return cur >= 0 && cur < playlist.entries().size() ? playlist.entries().get(cur).slot() : -1;
    }

    /** Position im Eintragsliste (Abspielreihenfolge) zum Slot, oder -1. */
    private int indexOfSlot(int slot) {
        List<Packet.PlaylistEntry> entries = playlist.entries();
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).slot() == slot) return i;
        return -1;
    }

    private Button playButton, takeButton, upButton, downButton;

    private void initPlaylist(int top) {
        int x = gridX() + 100;
        playButton = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.play.short"),
                b -> { int i = indexOfSlot(selectedSlot); if (i >= 0) send(Packet.PlaylistAction.Action.PLAY, i); })
                .bounds(x, top, 84, 20).tooltip(Tooltip.create(Component.translatable("peaksound.menu.playlist.play"))).build());
        addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.stop"), b -> send(Packet.PlaylistAction.Action.STOP, 0))
                .bounds(x + 86, top, 60, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.skip"), b -> send(Packet.PlaylistAction.Action.SKIP, 0))
                .bounds(x + 148, top, 62, 20).build());
        takeButton = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.take"),
                b -> { if (selectedSlot >= 0) ClientNetwork.send(new Packet.JukeboxInvAction(bx, by, bz, Packet.JukeboxInvAction.Kind.TAKE, selectedSlot)); })
                .bounds(x, top + 22, 84, 20).build());
        upButton = addRenderableWidget(Button.builder(Component.literal("\u25B2"), b -> moveSelected(true)).bounds(x + 86, top + 22, 30, 20).build());
        downButton = addRenderableWidget(Button.builder(Component.literal("\u25BC"), b -> moveSelected(false)).bounds(x + 118, top + 22, 30, 20).build());
        loopButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withLoop(cur.loop().next()));
            refreshLabels();
        }).bounds(x, top + 44, 104, 20).build());
        shuffleButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withShuffle(!cur.shuffle()));
            refreshLabels();
        }).bounds(x + 106, top + 44, 104, 20).build());
        refreshLabels();
        refreshPlaylistButtons();
    }

    private void moveSelected(boolean up) {
        int i = indexOfSlot(selectedSlot);
        if (i < 0) return;
        List<Packet.PlaylistEntry> entries = playlist.entries();
        int j = up ? i - 1 : i + 1;
        if (j < 0 || j >= entries.size()) return;
        send(up ? Packet.PlaylistAction.Action.MOVE_UP : Packet.PlaylistAction.Action.MOVE_DOWN, i);
        selectedSlot = entries.get(j).slot(); // die Auswahl folgt der Platte
    }

    private void refreshPlaylistButtons() {
        if (playButton == null) return;
        Packet.PlaylistEntry e = entryAt(selectedSlot);
        int i = indexOfSlot(selectedSlot);
        playButton.active = e != null && e.available();
        takeButton.active = e != null;
        upButton.active = e != null && i > 0;
        downButton.active = e != null && i < playlist.entries().size() - 1;
    }

    /** Klick im Reiter Playlist: Jukebox-Slot (Linksklick = auswaehlen, Rechtsklick = herausnehmen) oder Platte aus dem eigenen Inventar. */
    private boolean clickPlaylist(double mx, double my, int button) {
        int top = height / 2 - 100 + 28;
        int gx = gridX();
        for (int slot = 0; slot < 15; slot++) {
            int sx = gx + (slot % 5) * SLOT, sy = top + (slot / 5) * SLOT;
            if (mx >= sx && mx < sx + SLOT && my >= sy && my < sy + SLOT) {
                if (button == 1 && entryAt(slot) != null) {
                    ClientNetwork.send(new Packet.JukeboxInvAction(bx, by, bz, Packet.JukeboxInvAction.Kind.TAKE, slot));
                } else if (button == 0) {
                    selectedSlot = slot;
                }
                refreshPlaylistButtons();
                return true;
            }
        }
        var inv = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        if (inv == null || button != 0) return false;
        for (int n = 0; n < 36; n++) {
            int[] p = invSlotPos(n, top);
            if (mx >= p[0] && mx < p[0] + SLOT && my >= p[1] && my < p[1] + SLOT) {
                if (PeakItemStacks.is(inv.getItem(n), de.peaksound.protocol.PeakItemType.WRITTEN_RECORD)) {
                    ClientNetwork.send(new Packet.JukeboxInvAction(bx, by, bz, Packet.JukeboxInvAction.Kind.PUT, n));
                }
                return true;
            }
        }
        return false;
    }

    /** Bildschirmposition eines Inventar-Slots (0-8 Schnellleiste unten, 9-35 darueber). */
    private int[] invSlotPos(int n, int top) {
        int x = invX();
        int baseY = top + 78;
        if (n < 9) return new int[] {x + n * SLOT, baseY + 3 * SLOT + 4};
        int row = (n - 9) / 9, col = (n - 9) % 9;
        return new int[] {x + col * SLOT, baseY + row * SLOT};
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
        } else if (tab == Tab.SPEAKERS) {
            upmixButton.setMessage(toggle("peaksound.menu.upmix", cur.upmix()));
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
        if ((tab == Tab.PLAYLIST || tab == Tab.SPEAKERS) && ++pollTicks >= POLL_TICKS) {
            pollTicks = 0;
            if (tab == Tab.PLAYLIST) ClientNetwork.send(new Packet.PlaylistRequest(bx, by, bz));
            else ClientNetwork.send(new Packet.SpeakerOverviewRequest(bx, by, bz));
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

        if (tab == Tab.SIDES) extractSides(g, top + 28);
        else if (tab == Tab.PLAYLIST) extractPlaylist(g, top + 28, mouseX, mouseY);
        else if (tab == Tab.SPEAKERS) extractSpeakers(g, top + 28);
        else {
            Component hint = Component.translatable("peaksound.menu.equalizer.hint");
            g.text(font, hint, width / 2 - font.width(hint) / 2, top + 28 + 150, 0xFF777777);
        }
    }

    /** Uebersicht der verbundenen Lautsprecher mit Nummer, Name und Kanal-Zuweisung (nur Anzeige). */
    private void extractSpeakers(GuiGraphicsExtractor g, int top) {
        int left = width / 2 - 155;
        if (speakerEntries.isEmpty()) {
            Component hint = Component.translatable("peaksound.menu.speakers.empty");
            g.text(font, hint, width / 2 - font.width(hint) / 2, top + 20, 0xFFAAAAAA);
            Component hint2 = Component.translatable("peaksound.menu.speakers.empty2");
            g.text(font, hint2, width / 2 - font.width(hint2) / 2, top + 34, 0xFF777777);
            return;
        }
        g.text(font, Component.translatable("peaksound.menu.speakers.head"), left, top, 0xFFAAAAAA);
        int rows = Math.min(speakerEntries.size(), 12);
        for (int i = 0; i < rows; i++) {
            Packet.SpeakerEntry e = speakerEntries.get(i);
            int y = top + 14 + i * 12;
            String name = e.name().isBlank() ? "-" : e.name();
            g.text(font, "#" + e.number(), left, y, 0xFFFFAA00);
            g.text(font, name, left + 28, y, 0xFFFFFFFF);
            Component channel = Component.translatable("peaksound.channel." + e.channel().key())
                    .append(" (" + e.channel().shortName() + ")");
            g.text(font, channel, left + 150, y, 0xFF55FFFF);
            g.text(font, e.volume() + " %", left + 280, y, 0xFFAAAAAA);
        }
        if (speakerEntries.size() > rows) {
            g.text(font, "+" + (speakerEntries.size() - rows), left, top + 14 + rows * 12, 0xFF777777);
        }
    }

    private void slotBackground(GuiGraphicsExtractor g, int x, int y, int border) {
        g.fill(x, y, x + SLOT, y + SLOT, border);
        g.fill(x + 1, y + 1, x + SLOT - 1, y + SLOT - 1, 0xFF3B3B42);
    }

    private void extractPlaylist(GuiGraphicsExtractor g, int top, int mouseX, int mouseY) {
        int gx = gridX();
        int playing = currentSlot();
        net.minecraft.world.item.ItemStack recordIcon = PeakItemStacks.create(de.peaksound.protocol.PeakItemType.WRITTEN_RECORD);
        for (int slot = 0; slot < 15; slot++) {
            int sx = gx + (slot % 5) * SLOT, sy = top + (slot / 5) * SLOT;
            int border = slot == selectedSlot ? 0xFFFFFFFF : slot == playing ? 0xFF55FF55 : 0xFF1E1E24;
            slotBackground(g, sx, sy, border);
            Packet.PlaylistEntry e = entryAt(slot);
            if (e != null) {
                g.item(recordIcon, sx + 1, sy + 1);
                if (!e.available()) g.fill(sx + 1, sy + 1, sx + SLOT - 1, sy + SLOT - 1, 0x88AA0000);
                if (mouseX >= sx && mouseX < sx + SLOT && mouseY >= sy && mouseY < sy + SLOT) {
                    int seconds = e.durationMs() / 60 * 0 + e.durationMs() / 1000;
                    String sub = (e.artist().isBlank() ? "" : e.artist() + "  \u00B7  ") + String.format("%d:%02d", seconds / 60, seconds % 60);
                    g.setTooltipForNextFrame(List.of(Component.literal(e.title()).getVisualOrderText(),
                            Component.literal(sub).withStyle(net.minecraft.ChatFormatting.GRAY).getVisualOrderText()), mouseX, mouseY);
                }
            }
        }
        // Bedienungshinweis ueber dem eigenen Inventar
        g.text(font, Component.literal(fitText(Component.translatable("peaksound.menu.playlist.help").getString(), 310)), gx, top + 66, 0xFF8A8A94);
        var inv = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getInventory();
        if (inv == null) return;
        for (int n = 0; n < 36; n++) {
            int[] p = invSlotPos(n, top);
            net.minecraft.world.item.ItemStack stack = inv.getItem(n);
            boolean record = PeakItemStacks.is(stack, de.peaksound.protocol.PeakItemType.WRITTEN_RECORD);
            slotBackground(g, p[0], p[1], record ? 0xFF4C9A4C : 0xFF1E1E24);
            if (!stack.isEmpty()) {
                g.item(stack, p[0] + 1, p[1] + 1);
                g.itemDecorations(font, stack, p[0] + 1, p[1] + 1);
                if (!record) g.fill(p[0] + 1, p[1] + 1, p[0] + SLOT - 1, p[1] + SLOT - 1, 0x99202024);
            }
        }
    }

    private String fitText(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String cut = s;
        while (cut.length() > 1 && font.width(cut + "\u2026") > maxWidth) cut = cut.substring(0, cut.length() - 1);
        return cut + "\u2026";
    }
}
