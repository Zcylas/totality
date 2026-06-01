// api/client/gui/TotalityGuiRenderer.java
package zcylas.totality.api.client.gui;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2f;

/**
 * GUI rendering utilities for Totality screens and HUD.
 * Provides rounded rects, shadows, gradients, and lines.
 * Adapted from ImproperUI's RenderUtils + render states.
 *
 * Usage:
 *   TotalityGuiRenderer.fillRoundRect(context, x, y, w, h, 4, 0xFF1A1A2E);
 *   TotalityGuiRenderer.fillRoundShadow(context, x, y, w, h, 4, 6, 0x80000000);
 *   TotalityGuiRenderer.drawLine(context, x1, y1, x2, y2, 1f, 0xFFFFFFFF);
 */
public final class TotalityGuiRenderer {

    // ── Pipelines ─────────────────────────────────────────────────────────────

    private static final ColorTargetState  WITH_BLEND  = new ColorTargetState(BlendFunction.TRANSLUCENT);
    private static final DepthStencilState DEPTH_NONE  = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    public static final RenderPipeline PIPELINE_QUADS;

    static {
        PIPELINE_QUADS = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                        .withLocation(Identifier.fromNamespaceAndPath("totality", "pipeline/gui_fill"))
                        .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                        .withColorTargetState(WITH_BLEND)
                        .withCull(false)
                        .withDepthStencilState(DEPTH_NONE)
                        .build()
        );
    }

    private TotalityGuiRenderer() {}

    // ── Fill ──────────────────────────────────────────────────────────────────

    /** Solid filled rectangle. */
    public static void fillRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
        fillRoundRect(ctx, x, y, w, h, 0, color);
    }

    /** Vertical gradient rectangle. */
    public static void fillVerticalGradient(GuiGraphicsExtractor ctx,
                                            int x, int y, int w, int h,
                                            int colorTop, int colorBottom) {
        ctx.guiRenderState.addGuiElement(new QuadState(ctx,
                x, y, x + w, y,
                x + w, y + h, x, y + h,
                colorTop, colorTop, colorBottom, colorBottom));
    }

    /** Horizontal gradient rectangle. */
    public static void fillHorizontalGradient(GuiGraphicsExtractor ctx,
                                              int x, int y, int w, int h,
                                              int colorLeft, int colorRight) {
        ctx.guiRenderState.addGuiElement(new QuadState(ctx,
                x, y, x + w, y,
                x + w, y + h, x, y + h,
                colorLeft, colorRight, colorRight, colorLeft));
    }

    /** Filled circle. */
    public static void fillCircle(GuiGraphicsExtractor ctx, int cx, int cy, int radius, int color) {
        fillRoundRect(ctx, cx - radius, cy - radius, radius * 2, radius * 2, radius, color);
    }

    /** Solid rounded rectangle. */
    public static void fillRoundRect(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int radius, int color) {
        ctx.guiRenderState.addGuiElement(new RoundRectState(ctx, x, y, w, h, radius,
                color, color, color, color, color));
    }

    /** Rounded rectangle with per-corner colors and center color. */
    public static void fillRoundRectGradient(GuiGraphicsExtractor ctx,
                                             int x, int y, int w, int h, int radius,
                                             int colorTL, int colorTR, int colorBR, int colorBL,
                                             int colorCenter) {
        ctx.guiRenderState.addGuiElement(new RoundRectState(ctx, x, y, w, h, radius,
                colorTL, colorTR, colorBR, colorBL, colorCenter));
    }

    // ── Shadow / glow ─────────────────────────────────────────────────────────

    /**
     * Rounded border glow — draws a border around the rect that fades outward.
     * Useful for panel glows, selection highlights.
     */
    public static void fillRoundShadow(GuiGraphicsExtractor ctx,
                                       int x, int y, int w, int h,
                                       int radius, float thickness,
                                       int innerColor, int outerColor) {
        ctx.guiRenderState.addGuiElement(new RoundShadowState(ctx, x, y, w, h,
                radius, thickness,
                innerColor, outerColor, innerColor, outerColor,
                innerColor, outerColor, innerColor, outerColor));
    }

    /** Rounded border glow with solid color (outer fades to transparent). */
    public static void fillRoundShadow(GuiGraphicsExtractor ctx,
                                       int x, int y, int w, int h,
                                       int radius, float thickness, int color) {
        int transparent = color & 0x00FFFFFF; // zero alpha
        fillRoundShadow(ctx, x, y, w, h, radius, thickness, color, transparent);
    }

    // ── Lines ─────────────────────────────────────────────────────────────────

    /** Draw a 1px line between two points. */
    public static void drawLine(GuiGraphicsExtractor ctx,
                                int x1, int y1, int x2, int y2, int color) {
        drawLine(ctx, x1, y1, x2, y2, 1.0f, color);
    }

    /** Draw a line with custom thickness. */
    public static void drawLine(GuiGraphicsExtractor ctx,
                                float x1, float y1, float x2, float y2,
                                float thickness, int color) {
        float dx  = x2 - x1;
        float dy  = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len == 0) return;

        float nx  = -dy / len * (thickness / 2f);
        float ny  =  dx / len * (thickness / 2f);

        ctx.guiRenderState.addGuiElement(new QuadState(ctx,
                x1 + nx, y1 + ny,
                x2 + nx, y2 + ny,
                x2 - nx, y2 - ny,
                x1 - nx, y1 - ny,
                color, color, color, color));
    }

    /**
     * Fill a convex polygon as a triangle fan from its centroid.
     *
     * @param xs          x coordinates of vertices
     * @param ys          y coordinates of vertices
     * @param n           number of vertices
     * @param outerColor  color at the edge vertices
     * @param centerColor color at the centroid
     */
    public static void fillPolygon(GuiGraphicsExtractor ctx,
                                   float[] xs, float[] ys, int n,
                                   int outerColor, int centerColor) {
        float cx = 0, cy = 0;
        for (int i = 0; i < n; i++) { cx += xs[i]; cy += ys[i]; }
        cx /= n; cy /= n;

        for (int i = 0; i < n; i++) {
            int next = (i + 1) % n;
            // Degenerate quad: two center vertices + two edge vertices = one triangle
            ctx.guiRenderState.addGuiElement(new QuadState(ctx,
                    cx,      cy,
                    cx,      cy,
                    xs[i],   ys[i],
                    xs[next], ys[next],
                    centerColor, centerColor, outerColor, outerColor));
        }
    }

    /** Draw a rectangle outline. */
    public static void drawRectOutline(GuiGraphicsExtractor ctx,
                                       int x, int y, int w, int h,
                                       float thickness, int color) {
        drawLine(ctx, x,     y,     x + w, y,     thickness, color); // top
        drawLine(ctx, x,     y + h, x + w, y + h, thickness, color); // bottom
        drawLine(ctx, x,     y,     x,     y + h, thickness, color); // left
        drawLine(ctx, x + w, y,     x + w, y + h, thickness, color); // right
    }

    // ── Internal render states ────────────────────────────────────────────────

    /** Simple colored quad (4 corners, 4 colors). */
    private record QuadState(
            Matrix3x2f pose,
            float x1, float y1, float x2, float y2,
            float x3, float y3, float x4, float y4,
            int c1, int c2, int c3, int c4,
            ScreenRectangle scissor
    ) implements GuiElementRenderState {

        QuadState(GuiGraphicsExtractor ctx,
                  float x1, float y1, float x2, float y2,
                  float x3, float y3, float x4, float y4,
                  int c1, int c2, int c3, int c4) {
            this(new Matrix3x2f(ctx.pose()),
                    x1, y1, x2, y2, x3, y3, x4, y4,
                    c1, c2, c3, c4, ctx.scissorStack.peek());
        }

        @Override public void buildVertices(VertexConsumer buf) {
            buf.addVertexWith2DPose(pose, x1, y1).setColor(c1);
            buf.addVertexWith2DPose(pose, x2, y2).setColor(c2);
            buf.addVertexWith2DPose(pose, x3, y3).setColor(c3);
            buf.addVertexWith2DPose(pose, x4, y4).setColor(c4);
        }

        @Override public RenderPipeline pipeline()      { return PIPELINE_QUADS;        }
        @Override public TextureSetup   textureSetup()  { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea()  { return scissor;                }
        @Override public ScreenRectangle bounds() {
            float minX = Math.min(x1, Math.min(x2, Math.min(x3, x4)));
            float maxX = Math.max(x1, Math.max(x2, Math.max(x3, x4)));
            float minY = Math.min(y1, Math.min(y2, Math.min(y3, y4)));
            float maxY = Math.max(y1, Math.max(y2, Math.max(y3, y4)));
            ScreenRectangle rect = new ScreenRectangle(
                    (int) minX, (int) minY,
                    (int)(maxX - minX), (int)(maxY - minY)
            ).transformMaxBounds(pose);
            return scissor == null ? rect : scissor.intersection(rect);
        }
    }

    /** Rounded rectangle render state — triangle fan geometry. */
    private static final class RoundRectState implements GuiElementRenderState {

        private final Matrix3x2f     pose;
        private final float          x, y, w, h, r;
        private final int            c1, c2, c3, c4, cCenter;
        private final ScreenRectangle scissor;

        RoundRectState(GuiGraphicsExtractor ctx,
                       float x, float y, float w, float h, float radius,
                       int c1, int c2, int c3, int c4, int cCenter) {
            this.pose    = new Matrix3x2f(ctx.pose());
            this.x       = x;
            this.y       = y;
            this.w       = w;
            this.h       = h;
            this.r       = (float) Mth.clamp(radius, 0, Math.min(w, h) / 2.0);
            this.c1      = c1;
            this.c2      = c2;
            this.c3      = c3;
            this.c4      = c4;
            this.cCenter = cCenter;
            this.scissor = ctx.scissorStack.peek();
        }

        @Override
        public void buildVertices(VertexConsumer buf) {
            float[][] corners = {
                    { x + w - r, y + h - r },
                    { x + r,     y + h - r },
                    { x + r,     y + r     },
                    { x + w - r, y + r     }
            };
            int[] colors = { c3, c4, c1, c2 };

            float cx = x + w / 2f;
            float cy = y + h / 2f;

            for (int i = 0; i < 360; i += 10) {
                int   corner = i / 90;
                float a1     = (float) Math.toRadians(i);
                float a2     = (float) Math.toRadians(i + 10);

                float x2 = corners[corner][0] + Mth.cos(a1) * r;
                float y2 = corners[corner][1] + Mth.sin(a1) * r;
                float x3 = corners[corner][0] + Mth.cos(a2) * r;
                float y3 = corners[corner][1] + Mth.sin(a2) * r;

                buf.addVertexWith2DPose(pose, cx, cy).setColor(cCenter);
                buf.addVertexWith2DPose(pose, cx, cy).setColor(cCenter);
                buf.addVertexWith2DPose(pose, x2, y2).setColor(colors[corner]);
                buf.addVertexWith2DPose(pose, x3, y3).setColor(colors[corner]);
            }

            // Fill straight edges
            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, x + w - r,  y + h     ).setColor(c3);
            buf.addVertexWith2DPose(pose, x + r,      y + h     ).setColor(c4);

            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, x,          y + h - r ).setColor(c4);
            buf.addVertexWith2DPose(pose, x,          y + r     ).setColor(c1);

            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, x + r,      y         ).setColor(c1);
            buf.addVertexWith2DPose(pose, x + w - r,  y         ).setColor(c2);

            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, cx,         cy        ).setColor(cCenter);
            buf.addVertexWith2DPose(pose, x + w,      y + r     ).setColor(c2);
            buf.addVertexWith2DPose(pose, x + w,      y + h - r ).setColor(c3);
        }

        @Override public RenderPipeline  pipeline()     { return PIPELINE_QUADS;         }
        @Override public TextureSetup    textureSetup() { return TextureSetup.noTexture(); }
        @Override public ScreenRectangle scissorArea()  { return scissor;                 }
        @Override public ScreenRectangle bounds() {
            ScreenRectangle rect = new ScreenRectangle(
                    (int) x, (int) y, (int) w, (int) h
            ).transformMaxBounds(pose);
            return scissor == null ? rect : scissor.intersection(rect);
        }
    }

    /** Rounded border/shadow state — ring geometry that fades outward. */
    private static final class RoundShadowState implements GuiElementRenderState {

        private final Matrix3x2f      pose;
        private final float           x, y, w, h, r, thickness;
        private final int             ci1, co1, ci2, co2, ci3, co3, ci4, co4;
        private final ScreenRectangle scissor;

        RoundShadowState(GuiGraphicsExtractor ctx,
                         float x, float y, float w, float h, float radius, float thickness,
                         int ci1, int co1, int ci2, int co2,
                         int ci3, int co3, int ci4, int co4) {
            this.pose      = new Matrix3x2f(ctx.pose());
            this.x         = x;
            this.y         = y;
            this.w         = w;
            this.h         = h;
            this.r         = (float) Mth.clamp(radius, 0, Math.min(w, h) / 2.0);
            this.thickness = thickness;
            this.ci1 = ci1; this.co1 = co1;
            this.ci2 = ci2; this.co2 = co2;
            this.ci3 = ci3; this.co3 = co3;
            this.ci4 = ci4; this.co4 = co4;
            this.scissor   = ctx.scissorStack.peek();
        }

        @Override
        public void buildVertices(VertexConsumer buf) {
            float[][] corners = {
                    { x + w - r, y + h - r },
                    { x + r,     y + h - r },
                    { x + r,     y + r     },
                    { x + w - r, y + r     }
            };
            int[][] colors = {
                    { ci3, co3 }, { ci4, co4 }, { ci1, co1 }, { ci2, co2 }
            };

            for (int i = 0; i < 360; i += 10) {
                int   corner = i / 90;
                float a1     = (float) Math.toRadians(i);
                float a2     = (float) Math.toRadians(i + 10);

                float x1 = corners[corner][0] + Mth.cos(a1) * r;
                float y1 = corners[corner][1] + Mth.sin(a1) * r;
                float x2 = corners[corner][0] + Mth.cos(a2) * r;
                float y2 = corners[corner][1] + Mth.sin(a2) * r;
                float x3 = corners[corner][0] + Mth.cos(a2) * (r + thickness);
                float y3 = corners[corner][1] + Mth.sin(a2) * (r + thickness);
                float x4 = corners[corner][0] + Mth.cos(a1) * (r + thickness);
                float y4 = corners[corner][1] + Mth.sin(a1) * (r + thickness);

                buf.addVertexWith2DPose(pose, x1, y1).setColor(colors[corner][0]);
                buf.addVertexWith2DPose(pose, x2, y2).setColor(colors[corner][0]);
                buf.addVertexWith2DPose(pose, x3, y3).setColor(colors[corner][1]);
                buf.addVertexWith2DPose(pose, x4, y4).setColor(colors[corner][1]);
            }

            // Straight edge shadows
            buf.addVertexWith2DPose(pose, x + w - r, y + h          ).setColor(ci3);
            buf.addVertexWith2DPose(pose, x + r,     y + h          ).setColor(ci4);
            buf.addVertexWith2DPose(pose, x + r,     y + h + thickness).setColor(co4);
            buf.addVertexWith2DPose(pose, x + w - r, y + h + thickness).setColor(co3);

            buf.addVertexWith2DPose(pose, x,           y + h - r ).setColor(ci4);
            buf.addVertexWith2DPose(pose, x,           y + r     ).setColor(ci1);
            buf.addVertexWith2DPose(pose, x - thickness, y + r    ).setColor(co1);
            buf.addVertexWith2DPose(pose, x - thickness, y + h - r).setColor(co4);

            buf.addVertexWith2DPose(pose, x + r,     y           ).setColor(ci1);
            buf.addVertexWith2DPose(pose, x + w - r, y           ).setColor(ci2);
            buf.addVertexWith2DPose(pose, x + w - r, y - thickness).setColor(co2);
            buf.addVertexWith2DPose(pose, x + r,     y - thickness).setColor(co1);

            buf.addVertexWith2DPose(pose, x + w,           y + r    ).setColor(ci2);
            buf.addVertexWith2DPose(pose, x + w,           y + h - r).setColor(ci3);
            buf.addVertexWith2DPose(pose, x + w + thickness, y + h - r).setColor(co3);
            buf.addVertexWith2DPose(pose, x + w + thickness, y + r  ).setColor(co2);
        }

        @Override public RenderPipeline  pipeline()     { return PIPELINE_QUADS;          }
        @Override public TextureSetup    textureSetup() { return TextureSetup.noTexture();  }
        @Override public ScreenRectangle scissorArea()  { return scissor;                  }
        @Override public ScreenRectangle bounds() {
            ScreenRectangle rect = new ScreenRectangle(
                    (int)(x - thickness), (int)(y - thickness),
                    (int)(w + thickness * 2), (int)(h + thickness * 2)
            ).transformMaxBounds(pose);
            return scissor == null ? rect : scissor.intersection(rect);
        }
    }


}