package de.peaksound.mod.client;

import de.peaksound.protocol.Packet;
import de.peaksound.protocol.SpeakerChannel;
import de.peaksound.protocol.SpeakerSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Lautsprecher-Menue: Name, Funktion/Position im Surround-System, eigene Lautstaerke sowie Anzeige der
 * verbundenen Jukebox und der Nummer des Lautsprechers. Aenderungen gehen gebuendelt an den Server.
 */
final class SpeakerMenuScreen extends Screen {
    private static final long SEND_DELAY_MS = 250;

    private final int bx, by, bz;
    private final int number;
    private final String jukebox;
    private SpeakerSettings cur;
    private boolean dirty;
    private long lastChange;

    private Button channelButton;

    private SpeakerMenuScreen(Packet.OpenSpeakerMenu menu) {
        super(Component.translatable("peaksound.menu.speaker.title"));
        this.bx = menu.x();
        this.by = menu.y();
        this.bz = menu.z();
        this.cur = menu.settings();
        this.number = menu.number();
        this.jukebox = menu.jukeboxName();
    }

    static void open(Packet.OpenSpeakerMenu menu) {
        Minecraft.getInstance().setScreenAndShow(new SpeakerMenuScreen(menu));
    }

    @Override
    protected void init() {
        int left = width / 2 - 100;
        int top = height / 2 - 60;

        EditBox nameBox = new EditBox(font, left, top + 34, 200, 20, Component.translatable("peaksound.menu.name"));
        nameBox.setMaxLength(SpeakerSettings.MAX_NAME);
        nameBox.setHint(Component.translatable("peaksound.menu.speaker.name.hint"));
        nameBox.setValue(cur.name());
        nameBox.setResponder(text -> change(cur.withName(text)));
        addRenderableWidget(nameBox);

        // Funktion/Position per Hand waehlen: "<" und ">" blaettern durch alle 12 Positionen
        addRenderableWidget(Button.builder(Component.literal("<"), b -> {
            change(cur.withChannel(cur.channel().previous()));
            refresh();
        }).bounds(left, top + 62, 24, 20).build());
        channelButton = addRenderableWidget(Button.builder(Component.empty(), b -> {
            change(cur.withChannel(cur.channel().next()));
            refresh();
        }).bounds(left + 26, top + 62, 148, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> {
            change(cur.withChannel(cur.channel().next()));
            refresh();
        }).bounds(left + 176, top + 62, 24, 20).build());

        addRenderableWidget(new IntSlider(left, top + 90, 200, "peaksound.menu.speaker.volume", 0, 100, cur.volume(),
                v -> change(cur.withVolume(v))));
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(left, top + 122, 200, 20).build());
        refresh();
    }

    private void refresh() {
        SpeakerChannel c = cur.channel();
        channelButton.setMessage(Component.translatable("peaksound.menu.speaker.channel",
                Component.translatable("peaksound.channel." + c.key()), c.shortName()));
    }

    private void change(SpeakerSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.SpeakerUpdate(bx, by, bz, cur));
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
        int top = height / 2 - 60;
        Component title = cur.name().isBlank() ? Component.translatable("peaksound.menu.speaker.title") : Component.literal(cur.name());
        g.text(font, title, width / 2 - font.width(title) / 2, top - 6, 0xFFFFFFFF);
        // Nummer und verbundene Jukebox (Nummer 0 = nicht verbunden)
        Component line = number > 0
                ? Component.translatable("peaksound.menu.speaker.id", number, jukebox)
                : Component.translatable("peaksound.menu.speaker.unconnected");
        g.text(font, line, width / 2 - font.width(line) / 2, top + 10, number > 0 ? 0xFFAAAAAA : 0xFFFFAA00);
    }
}
