package zcylas.totality.api.client.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Extra GUI drawing utilities that complement the existing {@code TotalityGuiGraphics} mixin.
 *
 * <p>Ported from puzzles-lib (Fuzs) {@code GuiGraphicsHelper}.</p>
 *
 * <p>Sprite-based nine-slice and tile methods are omitted here because the
 * {@code GuiGraphicsExtractor.blitNineSlicedSprite} / {@code blitTiledSprite} signatures
 * and the {@code SpriteId} + {@code Sheets.GUI_SHEET} API surface need to be verified
 * against the exact 26.1.2 build before adding. Use vanilla's
 * {@code GuiGraphicsExtractor.blitSprite(ResourceLocation, ...)} or the atlas-sprite
 * variants directly until then.
 *
 * <p>Gradient fills ({@code fillHorizontalGradient}, {@code fillVerticalGradient}) live
 * on the {@code TotalityGuiGraphics} mixin interface — call them by casting
 * {@code (TotalityGuiGraphics) graphics} after updating the package path for your project.
 */
public final class GuiHelper {

    private GuiHelper() {}

    // -----------------------------------------------------------------------
    // Border / frame
    // -----------------------------------------------------------------------

    /**
     * Draw a rectangular border of {@code borderSize} pixels without filling the inside.
     * Equivalent to four {@code fill()} calls around the perimeter.
     *
     * <p>Useful for: tooltip border frames, info panels, confirmation popups, HP bar outlines.</p>
     *
     * @param x          left edge
     * @param y          top edge
     * @param width      total width including the border
     * @param height     total height including the border
     * @param borderSize thickness in pixels (goes inward from all four sides)
     * @param color      ARGB color
     */
    public static void fillFrame(GuiGraphicsExtractor g,
                                 int x, int y, int width, int height,
                                 int borderSize, int color) {
        fillFrameArea(g, x, y, x + width, y + height, borderSize, color);
    }

    /**
     * Same as {@link #fillFrame} but specified as corner coordinates.
     */
    public static void fillFrameArea(GuiGraphicsExtractor g,
                                     int minX, int minY, int maxX, int maxY,
                                     int borderSize, int color) {
        g.fill(minX, minY,              maxX,              minY + borderSize, color); // top
        g.fill(minX, maxY - borderSize, maxX,              maxY,              color); // bottom
        g.fill(minX, minY + borderSize, minX + borderSize, maxY - borderSize, color); // left
        g.fill(maxX - borderSize, minY + borderSize, maxX, maxY - borderSize, color); // right
    }

    // -----------------------------------------------------------------------
    // Outlined text
    // -----------------------------------------------------------------------

    /**
     * Draw {@code component} with a solid 1-pixel outline by rendering it 8 times
     * (shifted one pixel in each diagonal/cardinal direction) in {@code backgroundColor},
     * then once in {@code color} on top.
     *
     * <p>This is the same technique vanilla uses for the XP level number on the HUD.</p>
     *
     * @param color           foreground ARGB color
     * @param backgroundColor outline ARGB color
     */
    public static void drawOutlined(GuiGraphicsExtractor g, Font font, Component component,
                                    int x, int y, int color, int backgroundColor) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) {
                    g.text(font, component, x + dx, y + dy, backgroundColor, false);
                }
            }
        }
        g.text(font, component, x, y, color, false);
    }
}
