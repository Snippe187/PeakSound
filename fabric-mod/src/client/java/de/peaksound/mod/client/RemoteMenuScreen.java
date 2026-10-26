package de.peaksound.mod.client;

import de.peaksound.protocol.Packet;
import de.peaksound.protocol.RemoteAction;
import de.peaksound.protocol.RemoteSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menue der Musik-Fernbedienung: Welche Aktion loest ein Redstone-Signal an diesem Block aus (an allen Jukeboxen des
 * Netzes, an dem die Fernbedienung haengt)? Optional kann das Signal umgedreht werden.
 */
final class RemoteMenuScreen extends Screen {
    private static final long SEND_DELAY_MS = 250;

    private final int bx, by, bz;
    private final boolean connected;
    private RemoteSettings cur;
    private boolean dirty;
    private long lastChange;
    private Button invertButton;

    private RemoteMenuScreen(Packet.OpenRemoteMenu menu) {
        super(Component.translatable("peaksound.menu.remote.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.connected = menu.connected();
    }

    static void open(Packet.OpenRemoteMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new RemoteMenuScreen(menu));
    }

    @Override
    protected void init() {
        int left = width / 2 - 110, top = height / 2 - 70;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> change(cur.withAction(cur.action().previous())))
                .bounds(left, top + 40, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> change(cur.withAction(cur.action().next())))
                .bounds(left + 196, top + 40, 24, 20).build());
        invertButton = addRenderableWidget(Button.builder(Component.empty(), b -> change(cur.withInvert(!cur.invert())))
                .bounds(left, top + 100, 220, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left, top + 128, 220, 20).build());
        refresh();
    }

    private void refresh() {
        invertButton.setMessage(Component.translatable("peaksound.menu.remote.invert",
                Component.translatable(cur.invert() ? "options.on" : "options.off")));
    }

    private void change(RemoteSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
        refresh();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.RemoteUpdate(bx, by, bz, cur));
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
        int left = width / 2 - 110, top = height / 2 - 70;
        g.text(font, title, width / 2 - font.width(title) / 2, top, 0xFFFFFFFF);
        Component status = Component.translatable(connected ? "peaksound.menu.remote.connected" : "peaksound.menu.remote.unconnected");
        g.text(font, status, width / 2 - font.width(status) / 2, top + 14, connected ? 0xFFAAAAAA : 0xFFFFAA00);
        RemoteAction a = cur.action();
        Component name = Component.translatable("peaksound.remote.action." + a.key());
        g.text(font, name, width / 2 - font.width(name) / 2, top + 46, 0xFFFFFF55);
        Component desc = Component.translatable("peaksound.remote.action." + a.key() + ".desc");
        g.text(font, desc, left, top + 68, 0xFFAAAAAA);
        Component hint = Component.translatable(a.analog() ? "peaksound.menu.remote.hint.analog" : "peaksound.menu.remote.hint.digital");
        g.text(font, hint, left, top + 82, 0xFF777777);
    }
}