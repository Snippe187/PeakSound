package de.peaksound.mod.client;

import de.peaksound.protocol.Limits;
import de.peaksound.protocol.Packet;
import de.peaksound.protocol.RadioLogic;
import de.peaksound.protocol.RadioSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Radio-Menue fuer Sender und Empfaenger: an/aus, Frequenz (Feinabstimmung in 0,1-MHz-Schritten), beim Empfaenger
 * Reichweite und Lautstaerke. Zeigt, welcher Sender auf der Frequenz laeuft (bzw. was der Sender gerade sendet).
 */
final class RadioMenuScreen extends Screen {
    private static final long SEND_DELAY_MS = 250;
    private static RadioMenuScreen active;

    private final int bx, by, bz;
    private final boolean transmitter;
    private final boolean pocket;
    private RadioSettings cur;
    private String station;
    private boolean dirty;
    private long lastChange;

    private RadioMenuScreen(Packet.OpenRadioMenu menu) {
        super(Component.translatable(menu.transmitter() ? "peaksound.menu.radio.title.tx"
                : menu.x() == Limits.POCKET_COORD ? "peaksound.menu.radio.title.pocket" : "peaksound.menu.radio.title.rx"));
        this.pocket = menu.x() == Limits.POCKET_COORD;
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.transmitter = menu.transmitter();
        this.station = menu.station();
    }

    static void open(Packet.OpenRadioMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new RadioMenuScreen(menu));
    }

    static void onStatus(Packet.RadioStatus status) {
        RadioMenuScreen s = active;
        if (s != null && s.bx == status.x() && s.by == status.y() && s.bz == status.z()) s.station = status.station();
    }

    @Override
    protected void init() {
        active = this;
        int left = width / 2 - 100, top = height / 2 - 70;
        addRenderableWidget(Button.builder(onOff("peaksound.menu.radio.enabled", cur.enabled()), b -> {
            change(cur.withEnabled(!cur.enabled()));
            b.setMessage(onOff("peaksound.menu.radio.enabled", cur.enabled()));
        }).bounds(left, top + 30, 200, 20).build());
        // Frequenz: grob (+-1,0 MHz) und fein (+-0,1 MHz)
        addRenderableWidget(Button.builder(Component.literal("-1"), b -> tune(-10)).bounds(left, top + 56, 30, 20).build());
        addRenderableWidget(Button.builder(Component.literal("-0.1"), b -> tune(-1)).bounds(left + 32, top + 56, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+0.1"), b -> tune(1)).bounds(left + 134, top + 56, 34, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+1"), b -> tune(10)).bounds(left + 170, top + 56, 30, 20).build());
        if (!transmitter) {
            int y = top + 108;
            if (!pocket) {
                addRenderableWidget(new IntSlider(left, y, 200, "peaksound.menu.radio.range", 1, RadioSettings.MAX_RANGE, cur.range(),
                        v -> change(cur.withRange(v))));
                y += 24;
            }
            addRenderableWidget(new IntSlider(left, y, 200, "peaksound.menu.radio.volume", 0, 100, cur.volume(),
                    v -> change(cur.withVolume(v))));
        }
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left, top + (transmitter ? 112 : pocket ? 136 : 160), 200, 20).build());
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
    }

    private void tune(int delta) {
        change(cur.withFrequency(RadioLogic.clampFrequency(cur.frequency() + delta)));
    }

    private void change(RadioSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.RadioUpdate(bx, by, bz, cur));
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty && System.currentTimeMillis() - lastChange > SEND_DELAY_MS) flush();
    }

    @Override
    public void removed() {
        flush();
        if (active == this) active = null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int top = height / 2 - 70;
        g.text(font, title, width / 2 - font.width(title) / 2, top, 0xFFFFFFFF);
        Component hint = Component.translatable(transmitter ? "peaksound.menu.radio.hint.tx" : "peaksound.menu.radio.hint.rx");
        g.text(font, hint, width / 2 - font.width(hint) / 2, top + 14, 0xFF808080);
        // Frequenz-Anzeige zwischen den Tasten
        Component freq = Component.literal(RadioLogic.format(cur.frequency()));
        g.text(font, freq, width / 2 - font.width(freq) / 2, top + 62, 0xFFFFD24A);
        Component line;
        int color;
        if (transmitter) {
            boolean on = cur.enabled() && !station.isEmpty();
            line = on ? Component.translatable("peaksound.menu.radio.sending", station) : Component.translatable("peaksound.menu.radio.silent");
            color = on ? 0xFF7EE06A : 0xFFFFAA00;
        } else if (!cur.enabled()) {
            line = Component.translatable("peaksound.menu.radio.off");
            color = 0xFFAAAAAA;
        } else if (station.isEmpty()) {
            line = Component.translatable("peaksound.menu.radio.static");
            color = 0xFFFFAA00;
        } else {
            line = Component.translatable("peaksound.menu.radio.station", station);
            color = 0xFF7EE06A;
        }
        g.text(font, line, width / 2 - font.width(line) / 2, top + 86, color);
    }
}
