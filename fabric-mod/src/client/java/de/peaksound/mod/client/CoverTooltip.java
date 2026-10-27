package de.peaksound.mod.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

/** Daten des Cover-Tooltips (kommt vom Mixin) und seine Darstellung. */
public final class CoverTooltip {
    private CoverTooltip() {}

    /** Markiert den Tooltip einer beschriebenen Platte; {@code hash} bestimmt das Cover. */
    public record Data(String hash) implements TooltipComponent {}

    private static final int SIZE = 64;
    /** Standardbild ohne eingebettetes Cover: die Vanilla-Schallplatte. */
    private static final Identifier DEFAULT = Identifier.withDefaultNamespace("textures/item/music_disc_cat.png");

    /** Zeichnet das Cover eines Songs (oder das Standardbild) quadratisch; fragt es nur an, wenn der Song eins hat. */
    static void draw(GuiGraphicsExtractor g, String hash, boolean hasCover, int x, int y, int size) {
        CoverCache.Entry cover = hasCover ? CoverCache.get(hash) : null;
        if (cover != null && cover.state() == CoverCache.State.READY) {
            g.blit(RenderPipelines.GUI_TEXTURED, cover.texture(), x, y, 0f, 0f, size, size,
                    cover.width(), cover.height(), cover.width(), cover.height());
        } else {
            g.blit(RenderPipelines.GUI_TEXTURED, DEFAULT, x, y, 0f, 0f, size, size, 16, 16, 16, 16);
        }
    }

    static final class Renderer implements ClientTooltipComponent {
        private final String hash;

        Renderer(String hash) { this.hash = hash; }

        @Override
        public int getHeight(Font font) { return SIZE + 4; }

        @Override
        public int getWidth(Font font) { return SIZE; }

        @Override
        public void extractImage(Font font, int x, int y, int width, int height, GuiGraphicsExtractor g) {
            CoverCache.Entry cover = CoverCache.get(hash);
            if (cover.state() == CoverCache.State.READY) {
                g.blit(RenderPipelines.GUI_TEXTURED, cover.texture(), x, y, 0f, 0f, SIZE, SIZE,
                        cover.width(), cover.height(), cover.width(), cover.height());
            } else {
                g.blit(RenderPipelines.GUI_TEXTURED, DEFAULT, x, y, 0f, 0f, SIZE, SIZE, 16, 16, 16, 16);
            }
        }
    }

    /** Wird von Fabrics TooltipComponentCallback aufgerufen. */
    static ClientTooltipComponent create(TooltipComponent data) {
        return data instanceof Data d ? new Renderer(d.hash()) : null;
    }
}
