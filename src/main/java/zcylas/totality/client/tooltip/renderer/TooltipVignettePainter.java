package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import zcylas.totality.api.core.rpgutils.rarity.TooltipVignetteStyle;

/**
 * The tooltip vignette: a subtle rarity-tinted treatment over the whole tooltip panel (preview, identity,
 * divider, body, lore and footer alike), drawn directly after {@link TooltipPainter#drawBackground} and
 * before the frame and all content, so it can never sit on top of text.
 *
 * <p>One vignette system with four geometries ({@link TooltipVignetteStyle}); the color is always the
 * caller-supplied resolved rarity color, never embedded here. RADIAL and DIAGONAL_SWEEP stretch a small
 * generated alpha mask (linear filtering via its {@code .mcmeta}) tinted with that color; BOTTOM_GLOW is a
 * vertical gradient; EDGE_FRAME is a stepped inner glow of constant pixel depth.
 */
public final class TooltipVignettePainter {

    /** Peak vignette opacity — kept low so body text stays readable over it. */
    public static final float STRENGTH = 0.28f;
    /** Depth of the EDGE_FRAME inner glow, in GUI pixels. */
    public static final int EDGE_FRAME_DEPTH = 6;
    /** BOTTOM_GLOW covers this fraction of the panel height, measured up from the bottom edge. */
    public static final float BOTTOM_GLOW_FRACTION = 0.45f;

    private static final int MASK_SIZE = 128;
    private static final Identifier RADIAL_MASK =
            Identifier.fromNamespaceAndPath("totality", "textures/gui/tooltip/vignette_radial.png");
    private static final Identifier DIAGONAL_MASK =
            Identifier.fromNamespaceAndPath("totality", "textures/gui/tooltip/vignette_diagonal.png");

    public static void draw(GuiGraphicsExtractor graphics, TooltipVignetteStyle style,
                            int x, int y, int w, int h, int rarityColor) {
        if (w <= 0 || h <= 0) return;
        switch (style) {
            case RADIAL -> drawMask(graphics, RADIAL_MASK, x, y, w, h, rarityColor);
            case DIAGONAL_SWEEP -> drawMask(graphics, DIAGONAL_MASK, x, y, w, h, rarityColor);
            case BOTTOM_GLOW -> {
                int glowTop = y + h - Math.round(h * BOTTOM_GLOW_FRACTION);
                graphics.fillGradient(x, glowTop, x + w, y + h, withAlpha(rarityColor, 0f), withAlpha(rarityColor, STRENGTH));
            }
            case EDGE_FRAME -> {
                int depth = Math.min(EDGE_FRAME_DEPTH, Math.min(w, h) / 2);
                for (int ring = 0; ring < depth; ring++) {
                    int color = withAlpha(rarityColor, edgeFrameAlpha(ring, depth));
                    int l = x + ring, t = y + ring, r = x + w - ring, b = y + h - ring;
                    graphics.fill(l, t, r, t + 1, color);               // top
                    graphics.fill(l, b - 1, r, b, color);               // bottom
                    graphics.fill(l, t + 1, l + 1, b - 1, color);       // left (between top and bottom)
                    graphics.fill(r - 1, t + 1, r, b - 1, color);       // right
                }
            }
        }
    }

    private static void drawMask(GuiGraphicsExtractor graphics, Identifier mask, int x, int y, int w, int h, int rarityColor) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, mask, x, y, 0, 0, w, h,
                MASK_SIZE, MASK_SIZE, MASK_SIZE, MASK_SIZE, withAlpha(rarityColor, STRENGTH));
    }

    /** Opacity of EDGE_FRAME ring {@code ring} (0 = outermost), fading smoothly to nothing at the inner edge. */
    static float edgeFrameAlpha(int ring, int depth) {
        if (depth <= 0) return 0f;
        float t = 1f - (float) ring / depth;
        return STRENGTH * t * t;
    }

    /** The rarity color's RGB with the given opacity (0..1). */
    static int withAlpha(int rgb, float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (rgb & 0x00FFFFFF);
    }

    private TooltipVignettePainter() {}
}
