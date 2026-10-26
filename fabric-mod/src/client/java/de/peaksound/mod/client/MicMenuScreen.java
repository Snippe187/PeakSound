package de.peaksound.mod.client;

import de.peaksound.protocol.MicSettings;
import de.peaksound.protocol.Packet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Mikrofon-Menue: an/aus, Sprechreichweite, Stimmen-Lautstaerke und optionales Ducking (Musik leiser, solange jemand
 * spricht). Die Sprachuebertragung braucht Simple Voice Chat auf Server und Client.
 */
final class MicMenuScreen extends Screen {
    private static final long SEND_DELAY_MS = 250;

    private final int bx, by, bz;
    private final boolean connected, voiceChat;
    private MicSettings cur;
    private boolean dirty;
    private long lastChange;

    private MicMenuScreen(Packet.OpenMicMenu menu) {
        super(Component.translatable("peaksound.menu.mic.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.connected = menu.connected();
        this.voiceChat = menu.voiceChat();
    }

    static void open(Packet.OpenMicMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new MicMenuScreen(menu));
    }

    @Override
    protected void init() {
        int left = width / 2 - 100, top = height / 2 - 80;
        addRenderableWidget(Button.builder(onOff("peaksound.menu.mic.enabled", cur.enabled()), b -> {
            change(cur.withEnabled(!cur.enabled()));
            b.setMessage(onOff("peaksound.menu.mic.enabled", cur.enabled()));
        }).bounds(left, top + 36, 200, 20).build());
        addRenderableWidget(new IntSlider(left, top + 60, 200, "peaksound.menu.mic.range", 1, MicSettings.MAX_RANGE, cur.range(),
                v -> change(cur.withRange(v))));
        addRenderableWidget(new IntSlider(left, top + 84, 200, "peaksound.menu.mic.volume", 0, MicSettings.MAX_VOLUME, cur.volume(),
                v -> change(cur.withVolume(v))));
        Button duck = addRenderableWidget(Button.builder(onOff("peaksound.menu.mic.ducking", cur.ducking()), b -> {
            change(cur.withDucking(!cur.ducking()));
            rebuildWidgets();
        }).bounds(left, top + 108, 200, 20).build());
        duck.active = true;
        addRenderableWidget(Button.builder(onOff("peaksound.menu.redstone", cur.redstone()), b -> {
            change(cur.withRedstone(!cur.redstone()));
            b.setMessage(onOff("peaksound.menu.redstone", cur.redstone()));
        }).bounds(left, top + 156, 200, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("peaksound.menu.redstone.mic.tooltip"))).build());
        IntSlider level = addRenderableWidget(new IntSlider(left, top + 132, 200, "peaksound.menu.mic.duck", 0, 100, cur.duckPercent(),
                v -> change(cur.withDuckPercent(v))));
        level.active = cur.ducking();
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left, top + 180, 200, 20).build());
    }

    private static Component onOff(String key, boolean on) {
        return Component.translatable(key, Component.translatable(on ? "options.on" : "options.off"));
    }

    private void change(MicSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.MicUpdate(bx, by, bz, cur));
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
        int top = height / 2 - 80;
        g.text(font, title, width / 2 - font.width(title) / 2, top, 0xFFFFFFFF);
        Component status = !voiceChat ? Component.translatable("peaksound.menu.mic.novoice")
                : connected ? Component.translatable("peaksound.menu.mic.connected")
                : Component.translatable("peaksound.menu.mic.unconnected");
        g.text(font, status, width / 2 - font.width(status) / 2, top + 14, voiceChat && connected ? 0xFFAAAAAA : 0xFFFFAA00);
        Component hint = Component.translatable("peaksound.menu.mic.hint");
        g.text(font, hint, width / 2 - font.width(hint) / 2, top + 25, 0xFF808080);
    }
}