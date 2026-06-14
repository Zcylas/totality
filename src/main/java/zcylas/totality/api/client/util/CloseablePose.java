package zcylas.totality.client.util.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * AutoCloseable wrapper for {@link com.mojang.blaze3d.vertex.PoseStack} push/pop.
 * Ensures {@code popPose()} is always called, even on exception paths.
 *
 * <p>Ported from ResourcefulLib (TeamResourceful).</p>
 *
 * <p>Usage:
 * <pre>{@code
 * try (var pose = new CloseablePose(graphics)) {
 *     graphics.pose().translate(x, y, 0);
 *     graphics.pose().scale(scale, scale, 1);
 *     renderSomething(graphics);
 * } // popPose() called automatically
 * }</pre>
 */
public record CloseablePose(GuiGraphicsExtractor graphics) implements AutoCloseable {

    /**
     * Compact canonical constructor — calls {@code pushPose()} immediately.
     * {@link #close()} will call {@code popPose()}.
     */
    public CloseablePose {
        graphics.pose().pushMatrix();
    }

    @Override
    public void close() {
        graphics.pose().popMatrix();
    }
}
