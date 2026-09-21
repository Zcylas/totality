package zcylas.totality.client.tooltip;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.mixin.client.AbstractContainerScreenAccessor;

/**
 * Wires wheel input into {@link TooltipScrollController}, called from {@code MouseHandlerMixin}
 * — the same {@code MouseHandler.onScroll} hook point the mod already uses for
 * {@code FluidTankScrollHandler}. This fires <em>before</em> Minecraft's own screen-scroll
 * routing, so consuming here never even reaches — let alone depends on — the broken chain
 * described in {@link TooltipScrollController}'s class Javadoc.
 *
 * <p>Reads the current screen's {@code hoveredSlot} directly (via the pre-existing
 * {@link AbstractContainerScreenAccessor}) rather than recomputing it from a cursor position:
 * {@code MouseHandler.onScroll} only carries the raw scroll delta, no mouse coordinates, and
 * {@code hoveredSlot} is already the exact field vanilla's own (unreachable) container-screen
 * scroll logic reads too — using it here keeps this handler consistent with vanilla's own notion
 * of "which slot is hovered" without needing to duplicate any hit-testing.
 *
 * <p><b>Event-consumption contract (Tooltip scrolling event-consumption fix):</b> {@link
 * #onMouseScroll} returns {@code true} <em>only</em> when {@link TooltipScrollController#onMouseScroll}
 * actually changed the scroll offset — {@code MouseHandlerMixin} cancels the raw GLFW wheel event
 * if and only if this returns {@code true}, so the background screen (e.g. a container's own
 * item-catalog scroll, or Creative's) only ever sees a wheel event when the Totality tooltip did
 * <em>not</em> move for it. This method never mutates any state itself — the offset-changed
 * decision, including every boundary case, lives entirely in {@link TooltipScrollController}.
 *
 * <p><b>Why an overflowing tooltip's own boundary can still look like "both scroll at once"
 * (confirmed by decompiling {@code minecraft-merged.jar} for MC 26.2, not merely suspected):</b>
 * GLFW's scroll callback is bound to {@code MouseHandler}'s {@code lambda$setup$4}, which does not
 * call {@code onScroll} synchronously — it wraps it in {@code Minecraft.execute(() -> onScroll(...))},
 * deferring it onto the main-thread task queue. A single fast wheel gesture (especially from a
 * precision trackpad or a "smooth scrolling" mouse driver, which report many small deltas per
 * physical motion) can enqueue several {@code onScroll} invocations that all drain in one burst
 * before the next frame renders. Once the tooltip's own (often small) overflow is exhausted partway
 * through that burst, every remaining event in the very same gesture correctly — and, per this
 * contract's own required behavior, intentionally — falls through uncancelled to the screen. This
 * is the specified behavior for "tooltip already at top/bottom" (see the required-behavior list this
 * fix was written against), not a failure of cancellation; it is not, and cannot be, fixed by
 * changing how the event is consumed, only by changing how far the tooltip is allowed to scroll
 * relative to the gesture size — an unrelated, separate design question this fix does not attempt
 * to answer.
 */
public final class TotalityTooltipScrollHandler {

    public static boolean onMouseScroll(double scrollDelta) {
        if (scrollDelta == 0) return false;

        Screen screen = Minecraft.getInstance().gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?>)) return false;

        Slot hoveredSlot = ((AbstractContainerScreenAccessor) screen).totality$getHoveredSlot();
        if (hoveredSlot == null || !hoveredSlot.hasItem()) return false;

        ItemStack stack = hoveredSlot.getItem();
        if (!TotalityTooltipRenderer.isEligible(stack)) return false;

        return TooltipScrollController.onMouseScroll(screen, hoveredSlot, stack, scrollDelta);
    }

    private TotalityTooltipScrollHandler() {}
}
