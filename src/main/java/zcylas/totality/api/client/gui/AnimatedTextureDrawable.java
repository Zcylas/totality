// api/client/gui/AnimatedTextureDrawable.java
package zcylas.totality.api.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Draws an animated spritesheet texture in a GUI.
 * Adapted from owo-lib's AnimatedTextureDrawable.
 *
 * The spritesheet should be a grid of frames laid out in columns × rows.
 * Each frame is {@code frameWidth × frameHeight} pixels.
 *
 * Usage:
 *   // 4×1 spritesheet, each frame 16×16, 100ms per frame, looping
 *   var anim = new AnimatedTextureDrawable(
 *       Identifier.fromNamespaceAndPath("totality", "textures/gui/gear_spin.png"),
 *       64, 16,   // sheet width × height
 *       16, 16,   // frame width × height
 *       100, true
 *   );
 *
 *   // In screen render:
 *   anim.render(context, x, y, mouseX, mouseY, delta);
 */
public final class AnimatedTextureDrawable {

    private final Identifier texture;
    private final int        sheetW, sheetH;
    private final int        frameW, frameH;
    private final int        frameCount;
    private final int        columns, rows;
    private final long       frameDelayMs;
    private final boolean    loop;

    private long startTime = -1L;

    /**
     * @param texture      Identifier of the spritesheet texture
     * @param sheetWidth   Total width of the spritesheet in pixels
     * @param sheetHeight  Total height of the spritesheet in pixels
     * @param frameWidth   Width of a single frame in pixels
     * @param frameHeight  Height of a single frame in pixels
     * @param frameDelayMs Milliseconds per frame
     * @param loop         Whether the animation should loop
     */
    public AnimatedTextureDrawable(Identifier texture,
                                   int sheetWidth, int sheetHeight,
                                   int frameWidth,  int frameHeight,
                                   long frameDelayMs, boolean loop) {
        this.texture      = texture;
        this.sheetW       = sheetWidth;
        this.sheetH       = sheetHeight;
        this.frameW       = frameWidth;
        this.frameH       = frameHeight;
        this.columns      = sheetWidth  / frameWidth;
        this.rows         = sheetHeight / frameHeight;
        this.frameCount   = columns * rows;
        this.frameDelayMs = frameDelayMs;
        this.loop         = loop;
    }

    /** Render the animation at the given screen position. */
    public void render(GuiGraphicsExtractor context, int x, int y,
                       int mouseX, int mouseY, float delta) {
        if (startTime == -1L) startTime = Util.getMillis();

        long elapsed = Util.getMillis() - startTime;
        long frame   = elapsed / frameDelayMs;

        if (loop) {
            frame = frame % frameCount;
        } else {
            frame = Math.min(frame, frameCount - 1);
        }

        int col    = (int)(frame % columns);
        int row    = (int)(frame / columns);
        int srcX   = col * frameW;
        int srcY   = row * frameH;

        context.blit(RenderPipelines.GUI_TEXTURED,
                texture, x, y, srcX, srcY,
                frameW, frameH, sheetW, sheetH);
    }

    /** Reset the animation back to the first frame. */
    public void reset() {
        startTime = Util.getMillis();
    }

    /** Return true if the animation has finished (non-looping). */
    public boolean isFinished() {
        if (loop) return false;
        if (startTime == -1L) return false;
        long elapsed = Util.getMillis() - startTime;
        return (elapsed / frameDelayMs) >= frameCount;
    }

    public int getFrameWidth()  { return frameW; }
    public int getFrameHeight() { return frameH; }
}