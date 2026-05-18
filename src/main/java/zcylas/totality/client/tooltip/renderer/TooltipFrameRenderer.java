package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

public class TooltipFrameRenderer {

    private static final Identifier FRAMES = Identifier.fromNamespaceAndPath(
            "totality", "textures/gui/tooltip/tooltip_frames.png"
    );

    private static final int TEXTURE_W = 128;
    private static final int TEXTURE_H = 128;

    private record FrameData(int uOffset, int cornerW, int cornerH, int crownW, int topH, int botH, int partOffset, int cornerOffset) {}

    private static final FrameData LEGENDARY = new FrameData(0,  8, 8, 48, 8, 8, 2, 5);
    private static final FrameData COMMON    = new FrameData(64, 5, 5, 54, 5, 4, 0, 3);

    private static FrameData frameFor(ItemRarity rarity) {
        return switch (rarity) {
            case LEGENDARY -> LEGENDARY;
            case COMMON    -> COMMON;
            default        -> null;
        };
    }

    // Border colors per rarity
    private static int borderColor(ItemRarity rarity, TooltipTheme theme) {
        return theme.border();
    }

    private static int borderInnerColor(ItemRarity rarity, TooltipTheme theme) {
        return theme.borderInner();
    }

    public static void drawBorder(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                  TooltipTheme theme, ItemRarity rarity) {
        int border      = theme.border();
        int borderInner = theme.borderInner();

        // Outer border line — all rarities
        graphics.fill(x,         y,         x + w,     y + 1,     border);
        graphics.fill(x,         y + h - 1, x + w,     y + h,     border);
        graphics.fill(x,         y,         x + 1,     y + h,     border);
        graphics.fill(x + w - 1, y,         x + w,     y + h,     border);

        // Inner border line — all rarities
        graphics.fill(x + 1,     y + 1,     x + w - 1, y + 2,     borderInner);
        graphics.fill(x + 1,     y + h - 2, x + w - 1, y + h - 1, borderInner);
        graphics.fill(x + 1,     y + 1,     x + 2,     y + h - 1, borderInner);
        graphics.fill(x + w - 2, y + 1,     x + w - 1, y + h - 1, borderInner);

        // Frame overlay if this rarity has one
        FrameData frame = frameFor(rarity);
        if (frame != null) {
            drawFrameOverlay(graphics, x, y, w, h, frame);
        }
    }

    private static void drawFrameOverlay(GuiGraphicsExtractor graphics, int x, int y, int w, int h, FrameData f) {
        // Top-left corner
        blit(graphics, x - f.cornerW() + f.cornerOffset(), y - f.topH() + f.cornerOffset(),
                f.cornerW(), f.topH(), f.uOffset(), 0);

        // Top-right corner
        blit(graphics, x + w - f.cornerOffset(), y - f.topH() + f.cornerOffset(),
                f.cornerW(), f.topH(), f.uOffset() + 64 - f.cornerW(), 0);

        // Bottom-left corner
        blit(graphics, x - f.cornerW() + f.cornerOffset(), y + h - f.cornerOffset(),
                f.cornerW(), f.botH(), f.uOffset(), f.topH());

        // Bottom-right corner
        blit(graphics, x + w - f.cornerOffset(), y + h - f.cornerOffset(),
                f.cornerW(), f.botH(), f.uOffset() + 64 - f.cornerW(), f.topH());

        // Top crown
        if (w >= f.crownW()) {
            blit(graphics, x + (w / 2) - (f.crownW() / 2), y - f.topH() + f.partOffset(),
                    f.crownW(), f.topH(), f.uOffset() + f.cornerW(), 0);

            // Bottom crown
            blit(graphics, x + (w / 2) - (f.crownW() / 2), y + h - f.partOffset(),
                    f.crownW(), f.botH(), f.uOffset() + f.cornerW(), f.topH());
        }
    }

    private static void blit(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int u, int v) {
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                FRAMES,
                x, y,
                (float) u, (float) v,
                w, h,
                TEXTURE_W, TEXTURE_H
        );
    }

    private TooltipFrameRenderer() {}
}