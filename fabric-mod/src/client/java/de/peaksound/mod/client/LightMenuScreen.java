package de.peaksound.mod.client;

import de.peaksound.protocol.LightSettings;
import de.peaksound.protocol.Packet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Scheinwerfer-Menue: bis zu vier Farben (wechseln sich ab), Helligkeit, Modus, Effekt, Frequenzband, Geschwindigkeit
 * und Strahlrichtung. Aenderungen gehen gebuendelt an den Server, der sie prueft und speichert.
 */
final class LightMenuScreen extends Screen {
    private static final long SEND_DELAY_MS = 250;
    private static final int[] PALETTE = {
            0xFFFFFF, 0xFF2020, 0xFF8000, 0xFFE000, 0x80FF00, 0x00E040, 0x00E0E0, 0x2080FF,
            0x3030FF, 0x8000FF, 0xFF00FF, 0xFF4090, 0x804000, 0xC0C0C0, 0x808080, 0x602020};

    private final int bx, by, bz;
    private final boolean connected;
    private LightSettings cur;
    private int selected;
    private boolean dirty;
    private long lastChange;

    private LightMenuScreen(Packet.OpenLightMenu menu) {
        super(Component.translatable("peaksound.menu.light.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.connected = menu.connected();
    }

    static void open(Packet.OpenLightMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new LightMenuScreen(menu));
    }

    private static Component swatch(int rgb) {
        return Component.literal("\u2588\u2588").withStyle(s -> s.withColor(rgb));
    }

    @Override
    protected void init() {
        int left = width / 2 - 155, right = width / 2 + 5, top = height / 2 - 104;
        selected = Math.min(selected, cur.colors().size() - 1);

        // Farben (linke Spalte)
        List<Integer> colors = cur.colors();
        for (int i = 0; i < colors.size(); i++) {
            int index = i;
            Component label = index == selected
                    ? Component.literal("[").append(swatch(colors.get(i))).append("]")
                    : swatch(colors.get(i));
            addRenderableWidget(Button.builder(label, b -> {
                selected = index;
                rebuildWidgets();
            }).bounds(left + i * 26, top + 24, 26, 20).build());
        }
        Button add = addRenderableWidget(Button.builder(Component.literal("+"), b -> {
            List<Integer> next = new ArrayList<>(cur.colors());
            next.add(next.get(selected));
            selected = next.size() - 1;
            change(cur.withColors(next));
            rebuildWidgets();
        }).bounds(left + 104, top + 24, 22, 20).build());
        add.active = colors.size() < LightSettings.MAX_COLORS;
        Button remove = addRenderableWidget(Button.builder(Component.literal("-"), b -> {
            List<Integer> next = new ArrayList<>(cur.colors());
            next.remove(selected);
            change(cur.withColors(next));
            rebuildWidgets();
        }).bounds(left + 128, top + 24, 22, 20).build());
        remove.active = colors.size() > 1;

        int rgb = colors.get(selected);
        addRenderableWidget(new IntSlider(left, top + 48, 150, "peaksound.menu.light.red", 0, 255, (rgb >> 16) & 255,
                v -> setChannel(16, v)));
        addRenderableWidget(new IntSlider(left, top + 70, 150, "peaksound.menu.light.green", 0, 255, (rgb >> 8) & 255,
                v -> setChannel(8, v)));
        addRenderableWidget(new IntSlider(left, top + 92, 150, "peaksound.menu.light.blue", 0, 255, rgb & 255,
                v -> setChannel(0, v)));
        for (int i = 0; i < PALETTE.length; i++) {
            int color = PALETTE[i];
            addRenderableWidget(Button.builder(swatch(color), b -> {
                setColor(color);
                rebuildWidgets();
            }).bounds(left + (i % 8) * 19, top + 118 + (i / 8) * 21, 19, 20).build());
        }

        // Verhalten (rechte Spalte)
        addRenderableWidget(Button.builder(modeLabel(), b -> {
            change(cur.withMode(cur.mode().next()));
            rebuildWidgets();
        }).bounds(right, top + 24, 150, 20).build());
        addRenderableWidget(Button.builder(effectLabel(), b -> {
            change(cur.withEffect(cur.effect().next()));
            rebuildWidgets();
        }).bounds(right, top + 46, 150, 20).build());
        Button band = addRenderableWidget(Button.builder(bandLabel(), b -> {
            change(cur.withBand(cur.band().next()));
            rebuildWidgets();
        }).bounds(right, top + 68, 150, 20).build());
        band.active = cur.mode() == LightSettings.LightMode.BEAT;
        addRenderableWidget(new IntSlider(right, top + 90, 150, "peaksound.menu.light.brightness", 1, 100, cur.brightness(),
                v -> change(cur.withBrightness(v))));
        addRenderableWidget(new IntSlider(right, top + 112, 150, "peaksound.menu.light.speed", 1, 10, cur.speed(),
                v -> change(cur.withSpeed(v))));
        addRenderableWidget(new IntSlider(right, top + 134, 150, "peaksound.menu.light.yaw", 0, 359, cur.yaw(),
                v -> change(cur.withYaw(v))));
        addRenderableWidget(new IntSlider(right, top + 156, 150, "peaksound.menu.light.pitch", -90, 90, cur.pitch(),
                v -> change(cur.withPitch(v))));

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, top + 184, 200, 20).build());
    }

    private Component modeLabel() {
        return Component.translatable("peaksound.menu.light.mode", Component.translatable("peaksound.menu.light.mode." + cur.mode().name().toLowerCase()));
    }

    private Component effectLabel() {
        return Component.translatable("peaksound.menu.light.effect", Component.translatable("peaksound.menu.light.effect." + cur.effect().name().toLowerCase()));
    }

    private Component bandLabel() {
        return Component.translatable("peaksound.menu.light.band", Component.translatable("peaksound.menu.light.band." + cur.band().name().toLowerCase()));
    }

    private void setChannel(int shift, int value) {
        int rgb = cur.colors().get(selected);
        setColor((rgb & ~(255 << shift)) | (value << shift));
    }

    private void setColor(int rgb) {
        List<Integer> next = new ArrayList<>(cur.colors());
        next.set(selected, rgb & 0xFFFFFF);
        change(cur.withColors(next));
    }

    private void change(LightSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.LightUpdate(bx, by, bz, cur));
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
        int top = height / 2 - 104;
        g.text(font, title, width / 2 - font.width(title) / 2, top, 0xFFFFFFFF);
        Component status = Component.translatable(connected ? "peaksound.menu.light.connected" : "peaksound.menu.light.unconnected");
        g.text(font, status, width / 2 - font.width(status) / 2, top + 11, connected ? 0xFFAAAAAA : 0xFFFFAA00);
    }
}