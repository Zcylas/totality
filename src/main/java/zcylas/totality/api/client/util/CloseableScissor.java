package zcylas.totality.api.client.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * AutoCloseable wrapper for GUI scissor clipping.
 * Use in a try-with-resources block to guarantee {@code disableScissor()} is
 * always called, even if an exception is thrown inside the block.
 *
 * <p>Ported from ResourcefulLib (TeamResourceful).</p>
 *
 * <p>Usage:
 * <pre>{@code
 * try (var scissor = new CloseableScissor(graphics, panelX, panelY, panelW, panelH)) {
 *     renderScrollableContent(graphics, ...);
 * } // disableScissor() called automatically
 * }</pre>
 */
public record CloseableScissor(GuiGraphicsExtractor graphics) implements AutoCloseable {

    /**
     * Enable scissor clipping to the given rectangle and return a handle
     * whose {@link #close()} disables it.
     */
    public CloseableScissor(GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
        this(graphics);
        graphics.enableScissor(x, y, x + width, y + height);
    }

    @Override
    public void close() {
        graphics.disableScissor();
    }
}
