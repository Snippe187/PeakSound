package de.peaksound.mod.client;

import de.peaksound.protocol.ChainMath;
import de.peaksound.protocol.JukeboxSettings;
import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Das Jukebox-Menue: Name, Reichweite, Lautstaerke, Ketten-Schalter, Noten-Partikel und "Jetzt laeuft"-Anzeige.
 * Aenderungen werden gebuendelt an den Server geschickt; der Server prueft jeden Wert erneut.
 */
final class JukeboxMenuScreen extends Screen {
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

    private final int bx, by, bz;
    private final boolean playing;
    private final String title;
    private final String artist;
    private JukeboxSettings cur;
    private boolean dirty;
    private long lastChange;

    private EditBox nameBox;
    private IntSlider range;
    private IntSlider volume;
    private Button chainButton;
    private Button particlesButton;
    private Button rainbowButton;
    private Button nowPlayingButton;
    private IntSlider amount;
    private IntSlider density;
    private IntSlider heightSlider;

    private JukeboxMenuScreen(Packet.OpenJukeboxMenu menu) {
        super(Component.translatable("peaksound.menu.jukebox.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.playing = menu.playing();
        this.title = menu.title();
        this.artist = menu.artist();
    }

    static void open(Packet.OpenJukeboxMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new JukeboxMenuScreen(menu));
    }

    @Override
    protected void init() {
        int left = width / 2 - 155;
        int right = width / 2 + 5;
        int w = 150;
        int top = height / 2 - 85;

        nameBox = new EditBox(font, width / 2 - 155, top, 310, 20, Component.translatable("peaksound.menu.name"));
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
        }).bounds(left, top + 76, w, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("peaksound.menu.chain.tooltip"))).build());
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

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, top + 156, 200, 20).build());
        refreshLabels();
    }

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
        chainButton.setMessage(toggle("peaksound.menu.chain", cur.chain()));
        nowPlayingButton.setMessage(toggle("peaksound.menu.nowplaying", cur.showNowPlaying()));
        particlesButton.setMessage(toggle("peaksound.menu.particles", cur.particles()));
        rainbowButton.setMessage(toggle("peaksound.menu.rainbow", cur.rainbow()));
        boolean p = cur.particles();
        amount.active = p;
        density.active = p;
        heightSlider.active = p;
        rainbowButton.active = p;
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
    }

    @Override
    public void removed() {
        flush();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 85;
        g.text(font, title(), width / 2 - font.width(title()) / 2, top - 22, 0xFFFFFFFF);
        Component now = playing
                ? Component.translatable("peaksound.menu.playing", title, artist.isBlank() ? "-" : artist)
                : Component.translatable("peaksound.menu.nothing");
        g.text(font, now, width / 2 - font.width(now) / 2, top - 10, 0xFFAAAAAA);
    }

    private Component title() {
        String name = cur.name();
        return name.isBlank() ? Component.translatable("peaksound.menu.jukebox.title") : Component.literal(name);
    }
}
