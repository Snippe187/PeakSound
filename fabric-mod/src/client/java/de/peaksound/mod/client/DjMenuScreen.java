package de.peaksound.mod.client;

import de.peaksound.protocol.DjSettings;
import de.peaksound.protocol.Packet;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * DJ-Pult-Menue: je Deck der Song aus der Playlist der angeschlossenen Jukebox, in der Mitte Crossfader,
 * Ueberblendzeit und Decklautstaerken. Der Server liefert den Zustand wiederholt (Abfrage alle 0,5 s).
 */
final class DjMenuScreen extends Screen {
    private static final long SEND_DELAY_MS = 250;
    private static final int POLL_TICKS = 10;
    private static final int PAGE_SIZE = 3;

    private static DjMenuScreen active;

    private final int bx, by, bz;
    private DjSettings cur;
    private Packet.DjDeck[] decks;
    private final int[] page = new int[2];
    private String signature;
    private boolean dirty;
    private long lastChange;
    private int pollTicks;

    private DjMenuScreen(Packet.DjState state) {
        super(Component.translatable("peaksound.menu.dj.title"));
        this.bx = state.x();
        this.by = state.y();
        this.bz = state.z();
        this.cur = state.settings();
        this.decks = new Packet.DjDeck[] {state.deckA(), state.deckB()};
        this.signature = signature(state);
    }

    private static String signature(Packet.DjState s) {
        return s.deckA() + "|" + s.deckB();
    }

    static void onState(Packet.DjState state) {
        Minecraft mc = Minecraft.getInstance();
        if (state.open()) {
            mc.setScreenAndShow(new DjMenuScreen(state));
        } else {
            DjMenuScreen menu = active;
            if (menu != null && menu.bx == state.x() && menu.by == state.y() && menu.bz == state.z()) {
                menu.apply(state);
            }
        }
    }

    private void apply(Packet.DjState state) {
        decks = new Packet.DjDeck[] {state.deckA(), state.deckB()};
        String next = signature(state);
        if (!next.equals(signature)) { // nur bei Aenderung neu aufbauen, damit laufende Regler nicht springen
            signature = next;
            rebuildWidgets();
        }
    }

    @Override
    protected void init() {
        active = this;
        int left = width / 2 - 155, right = width / 2 + 5, top = height / 2 - 116;
        for (int d = 0; d < 2; d++) {
            int x = d == 0 ? left : right;
            Packet.DjDeck deck = decks[d];
            List<Packet.PlaylistEntry> entries = deck.entries();
            int pages = Math.max(1, (entries.size() + PAGE_SIZE - 1) / PAGE_SIZE);
            page[d] = Math.min(page[d], pages - 1);
            for (int i = 0; i < PAGE_SIZE; i++) {
                int index = page[d] * PAGE_SIZE + i;
                if (index >= entries.size()) break;
                Packet.PlaylistEntry e = entries.get(index);
                boolean now = index == deck.current() && deck.playing();
                String text = (now ? "\u25B6 " : "  ") + (index + 1) + ". " + cut(e.title(), 22);
                int deckIndex = d;
                Button play = addRenderableWidget(Button.builder(Component.literal(text),
                        b -> ClientNetwork.send(new Packet.DjDeckAction(bx, by, bz, deckIndex, Packet.DjDeckAction.Action.PLAY, index)))
                        .bounds(x, top + 38 + i * 20, 150, 20).build());
                play.active = deck.connected() && e.available();
            }
            int deckIndex = d;
            Button prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page[deckIndex]--;
                rebuildWidgets();
            }).bounds(x, top + 100, 24, 20).build());
            prev.active = page[d] > 0;
            Button next = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page[deckIndex]++;
                rebuildWidgets();
            }).bounds(x + 26, top + 100, 24, 20).build());
            next.active = page[d] < pages - 1;
            Button skip = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.skip"),
                    b -> ClientNetwork.send(new Packet.DjDeckAction(bx, by, bz, deckIndex, Packet.DjDeckAction.Action.SKIP, 0)))
                    .bounds(x + 52, top + 100, 48, 20).build());
            skip.active = deck.connected();
            Button stop = addRenderableWidget(Button.builder(Component.translatable("peaksound.menu.playlist.stop"),
                    b -> ClientNetwork.send(new Packet.DjDeckAction(bx, by, bz, deckIndex, Packet.DjDeckAction.Action.STOP, 0)))
                    .bounds(x + 102, top + 100, 48, 20).build());
            stop.active = deck.connected();
        }

        IntSlider crossfader = addRenderableWidget(new IntSlider(left, top + 126, 310, "peaksound.menu.dj.crossfader", 0, 100, cur.crossfader(),
                v -> change(cur.withCrossfader(v))));
        crossfader.active = !cur.redstone();
        addRenderableWidget(new IntSlider(left, top + 148, 205, "peaksound.menu.dj.fade", 0, 30, cur.fadeSeconds(),
                v -> change(cur.withFadeSeconds(v))));
        addRenderableWidget(Button.builder(swapLabel(), b -> {
            change(cur.withSwapped(!cur.swapped()));
            b.setMessage(swapLabel());
        }).bounds(left + 210, top + 148, 100, 20).build());
        addRenderableWidget(new IntSlider(left, top + 170, 150, "peaksound.menu.dj.volume.a", 0, 100, cur.volumeA(),
                v -> change(cur.withVolumeA(v))));
        addRenderableWidget(new IntSlider(right, top + 170, 150, "peaksound.menu.dj.volume.b", 0, 100, cur.volumeB(),
                v -> change(cur.withVolumeB(v))));
        addRenderableWidget(Button.builder(redstoneLabel(), b -> {
            change(cur.withRedstone(!cur.redstone()));
            rebuildWidgets();
        }).bounds(left, top + 194, 310, 20).tooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("peaksound.menu.redstone.dj.tooltip"))).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width / 2 - 100, top + 218, 200, 20).build());
    }

    private Component redstoneLabel() {
        return Component.translatable("peaksound.menu.redstone", Component.translatable(cur.redstone() ? "options.on" : "options.off"));
    }

    private Component swapLabel() {
        return Component.translatable("peaksound.menu.dj.swap", Component.translatable(cur.swapped() ? "options.on" : "options.off"));
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max - 1) + "\u2026" : s;
    }

    private void change(DjSettings next) {
        cur = next;
        dirty = true;
        lastChange = System.currentTimeMillis();
    }

    private void flush() {
        if (!dirty) return;
        dirty = false;
        ClientNetwork.send(new Packet.DjUpdate(bx, by, bz, cur));
    }

    @Override
    public void tick() {
        super.tick();
        if (dirty && System.currentTimeMillis() - lastChange > SEND_DELAY_MS) flush();
        if (++pollTicks >= POLL_TICKS) {
            pollTicks = 0;
            ClientNetwork.send(new Packet.DjRequest(bx, by, bz));
        }
    }

    @Override
    public void removed() {
        if (active == this) active = null;
        flush();
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int left = width / 2 - 155, right = width / 2 + 5, top = height / 2 - 116;
        g.text(font, title, width / 2 - font.width(title) / 2, top, 0xFFFFFFFF);
        for (int d = 0; d < 2; d++) {
            Packet.DjDeck deck = decks[d];
            int x = d == 0 ? left : right;
            Component head = deck.connected()
                    ? Component.translatable(d == 0 ? "peaksound.menu.dj.deck.a" : "peaksound.menu.dj.deck.b", deck.jukeboxName().isBlank() ? "-" : deck.jukeboxName())
                    : Component.translatable("peaksound.menu.dj.unconnected", d == 0 ? "A" : "B");
            g.text(font, head, x, top + 14, deck.connected() ? 0xFFFFFFFF : 0xFFFFAA00);
            Component now = deck.playing() && !deck.title().isBlank()
                    ? Component.translatable("peaksound.menu.dj.now", cut(deck.title(), 24))
                    : Component.translatable("peaksound.menu.dj.idle");
            g.text(font, now, x, top + 26, 0xFFAAAAAA);
        }
    }
}