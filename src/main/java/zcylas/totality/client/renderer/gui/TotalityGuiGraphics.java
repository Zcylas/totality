package zcylas.totality.client.renderer.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

import com.mojang.blaze3d.pipeline.RenderPipeline;

@Environment(EnvType.CLIENT)
public interface TotalityGuiGraphics {

    static TotalityGuiGraphics of(GuiGraphicsExtractor graphics) {
        return (TotalityGuiGraphics) graphics;
    }

    // ── Solid fills ──────────────────────────────────────────────────────────

    default void colorRect(int x, int y, int width, int height, int color) {
        guiRenderState().addGuiElement(new ColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(),
                new Matrix3x2f(pose()), x, y, x + width, y + height,
                color, color, peekScissor()));
    }

    default void colorRect(float x, float y, float width, float height, int color) {
        colorRect((int) x, (int) y, (int) width, (int) height, color);
    }

    // ── Gradient fills ───────────────────────────────────────────────────────

    /** Vertical gradient — colorFrom at top, colorTo at bottom. */
    default void verticalGradientRect(int x, int y, int x2, int y2, int colorFrom, int colorTo) {
        guiRenderState().addGuiElement(new ColoredRectangleRenderState(
                RenderPipelines.GUI, TextureSetup.noTexture(),
                new Matrix3x2f(pose()), x, y, x2, y2,
                colorFrom, colorTo, peekScissor()));
    }

    default void verticalGradientRect(float x, float y, float x2, float y2, int colorFrom, int colorTo) {
        verticalGradientRect((int) x, (int) y, (int) x2, (int) y2, colorFrom, colorTo);
    }

    /**
     * Horizontal gradient — colorFrom at left, colorTo at right.
     * Implemented as stacked 1-pixel-wide vertical fills since
     * {@code ColoredRectangleRenderState} only supports top-to-bottom gradients.
     */
    default void horizontalGradientRect(int x, int y, int x2, int y2, int colorFrom, int colorTo) {
        int width = x2 - x;
        if (width <= 0) return;
        for (int i = 0; i < width; i++) {
            float t = (float) i / (width - 1);
            int col = lerpColor(colorFrom, colorTo, t);
            guiRenderState().addGuiElement(new ColoredRectangleRenderState(
                    RenderPipelines.GUI, TextureSetup.noTexture(),
                    new Matrix3x2f(pose()), x + i, y, x + i + 1, y2,
                    col, col, peekScissor()));
        }
    }

    default void horizontalGradientRect(float x, float y, float x2, float y2, int colorFrom, int colorTo) {
        horizontalGradientRect((int) x, (int) y, (int) x2, (int) y2, colorFrom, colorTo);
    }

    // ── Texture helpers ──────────────────────────────────────────────────────

    default void textureRect(Identifier texture, int x, int y, int width, int height, float u, float v) {
        blit(RenderPipelines.GUI_TEXTURED, texture,
                x, x + width, y, y + height,
                u / 256f, (u + width) / 256f,
                v / 256f, (v + height) / 256f, -1);
    }

    default void textureRect(Identifier texture, int x, int y, int width, int height,
                             float u, float v, float u2, float v2) {
        blit(RenderPipelines.GUI_TEXTURED, texture,
                x, x + width, y, y + height,
                u / 256f, u2 / 256f,
                v / 256f, v2 / 256f, -1);
    }

    default void textureRectColor(Identifier texture, int x, int y, int width, int height,
                                  float u, float v, float u2, float v2, int color) {
        blit(RenderPipelines.GUI_TEXTURED, texture,
                x, x + width, y, y + height,
                u / 256f, u2 / 256f,
                v / 256f, v2 / 256f, color);
    }

    default void textureRectStretched(Identifier texture, int x, int y, int width, int height) {
        blit(RenderPipelines.GUI_TEXTURED, texture, x, x + width, y, y + height, 0f, 1f, 0f, 1f, -1);
    }

    // ── Text helpers ─────────────────────────────────────────────────────────

    default void drawStringCentered(String text, float width, float height, int color, boolean shadow) {
        Minecraft mc = minecraft();
        int tw = mc.font.width(text);
        if (tw > width) {
            int dw = mc.font.width("...");
            if (tw > dw) {
                StringBuilder sb = new StringBuilder();
                int used = 0;
                for (int i = 0; i < text.length(); i++) {
                    int cw = mc.font.width(String.valueOf(text.charAt(i)));
                    if (used + cw + dw < width) { sb.append(text.charAt(i)); used += cw; }
                    else break;
                }
                text = sb + "...";
            }
        }
        as().text(mc.font, text,
                (int)(width / 2 - mc.font.width(text) / 2),
                (int)(height / 2 - mc.font.lineHeight / 2),
                color, shadow);
    }

    default void drawString(FormattedCharSequence text, int x, int y,
                            int color, int shadowColor, boolean shadow) {
        if (ARGB.alpha(color) != 0)
            guiRenderState().addText(new GuiTextRenderState(
                    font(), text, new Matrix3x2f(pose()),
                    x, y, color, shadowColor, shadow, true, peekScissor()));
    }

    // ── Item decorations ─────────────────────────────────────────────────────

    default void renderItemDecorations(ItemStack stack, int x, int y) {
        as().itemDecorations(font(), stack, x, y);
    }

    default void renderItemDecorations(ItemStack stack, int x, int y, @Nullable String text) {
        as().itemDecorations(font(), stack, x, y, text);
    }

    // ── Abstract — implemented by GuiGraphicsExtractorMixin ──────────────────

    void blit(RenderPipeline pipeline, Identifier texture,
              int x0, int x1, int y0, int y1,
              float u, float u2, float v, float v2,
              int color);

    GuiGraphicsExtractor as();
    Minecraft       minecraft();
    Matrix3x2fStack pose();
    Font            font();
    GuiRenderState  guiRenderState();
    @Nullable ScreenRectangle peekScissor();

    // ── Internal helpers ─────────────────────────────────────────────────────

    private static int lerpColor(int c1, int c2, float t) {
        int a = (int)(((c1 >> 24 & 0xFF) * (1-t)) + ((c2 >> 24 & 0xFF) * t));
        int r = (int)(((c1 >> 16 & 0xFF) * (1-t)) + ((c2 >> 16 & 0xFF) * t));
        int g = (int)(((c1 >>  8 & 0xFF) * (1-t)) + ((c2 >>  8 & 0xFF) * t));
        int b = (int)(((c1       & 0xFF) * (1-t)) + ((c2       & 0xFF) * t));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}