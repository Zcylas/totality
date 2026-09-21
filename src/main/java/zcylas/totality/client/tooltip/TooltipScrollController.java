package zcylas.totality.client.tooltip;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Owns mouse-wheel scroll state for the Totality tooltip viewport, keyed by an explicit
 * <b>active scroll target</b> rather than by cursor position over the rendered tooltip rectangle.
 *
 * <p><b>Why not cursor-over-tooltip, and why not a screen-level scroll mixin (visual-correction
 * pass, Finding 7):</b> the tooltip panel is drawn offset from the hovered slot (see
 * {@code TotalityTooltipRenderer#render}'s panel positioning), so requiring the cursor to sit over
 * the panel itself to scroll it is both unnatural (the player is looking at the item slot, not the
 * panel) and was never how input actually reached this controller to begin with. A first attempt
 * at fixing this tried injecting into {@code GuiEventListener}/{@code ContainerEventHandler}'s
 * {@code mouseScrolled} default methods — both confirmed (by decompiling the vanilla classes)
 * completely unreachable, because {@code AbstractContainerScreen} declares its own concrete
 * {@code mouseScrolled(DDDD)Z} override that routes wheel input through its private
 * {@code itemSlotMouseActions} list whenever a slot holding an item is hovered, and never calls
 * {@code super.mouseScrolled(...)} at all. The actual fix hooks one level higher instead:
 * {@code MouseHandler.onScroll} (the same raw-input mixin point the mod already uses for
 * {@code FluidTankScrollHandler}) fires before Minecraft routes the event to any screen at all, so
 * {@link TotalityTooltipScrollHandler} intercepts it there — reading the current screen's
 * {@code hoveredSlot} directly (already the exact field vanilla's own broken chain would have
 * read) rather than depending on any screen-level event-routing mechanism whatsoever.
 *
 * <p><b>Slot identity (visual-correction pass, micro-correction Finding 2):</b> the active target
 * identifies "which slot" by the {@link Slot} object's own reference identity, not by
 * {@link Slot#index} — {@code index} is only unique within that slot's own backing
 * {@code Container}, so a player-inventory slot and an external-container slot can legitimately
 * share the same {@code index} value. Comparing {@code Slot} references directly (every slot in an
 * open menu is a stable, distinct object for the menu's lifetime) is what actually guarantees two
 * different slots are never mistaken for the same one.
 *
 * <p>The active target — set once per frame by {@link #onRender} — carries everything
 * {@link TotalityTooltipScrollHandler#onMouseScroll} needs to decide whether an incoming wheel event
 * belongs to the tooltip currently on screen: the screen instance, the hovered slot's identity, the
 * stack/document identity, the maximum scroll offset, the viewport bounds, and a per-frame
 * freshness marker (so a wheel event arriving after a frame in which no Totality tooltip rendered
 * at all — e.g. the cursor just left the slot — is correctly treated as "no active tooltip").
 *
 * <ul>
 *   <li>Reset to the top when the screen, hovered slot, or stack/document identity changes.</li>
 *   <li>Clamped to the current overflow whenever disclosure level changes (more/less content).</li>
 *   <li>Reset when the screen closes (see {@link #registerLifecycleHooks()}).</li>
 *   <li>Never consumes wheel input when no Totality tooltip is currently overflowing, when the
 *       wheel event's screen/slot/stack doesn't match the frame's active target, or when the
 *       resulting scroll offset would not actually change (visual-correction pass, micro-correction
 *       Finding 1) — so normal screen scrolling remains available once a tooltip has scrolled as
 *       far as it can.</li>
 * </ul>
 *
 * Uses {@code null} (not {@code ItemStack.EMPTY}) as the "no stack yet" sentinel deliberately —
 * {@code ItemStack.EMPTY} cannot appear in a static field initializer here: referencing it
 * triggers the item registry the same way constructing a real stack does, which throws under
 * plain JUnit before the registry is bootstrapped (confirmed by this class's own test).
 */
public final class TooltipScrollController {

    /** Pixels of scroll offset applied per unit of raw wheel delta. */
    static final int SCROLL_STEP = 8;

    /**
     * One frame's worth of "which tooltip, on which screen, over which slot, can currently be
     * scrolled" — registered by {@link #onRender}, read by {@link #onMouseScroll}. Package-private
     * (not private) so the identity-matching arithmetic can be unit-tested directly. {@code slot}
     * is compared by reference identity, never by {@link Slot#index} (see the class Javadoc).
     */
    record ActiveTarget(@Nullable Screen screen, @Nullable Slot slot, int maxScroll,
                        int viewportX, int viewportY, int viewportWidth, int viewportHeight,
                        long frameMarker) {}

    @Nullable
    private static ItemStack currentStack = null;
    private static TooltipDisclosureLevel lastDisclosure = TooltipDisclosureLevel.DEFAULT;
    private static int scrollOffset = 0;
    @Nullable
    private static Slot currentSlot = null;
    @Nullable
    private static Screen currentScreen = null;
    private static long frameCounter = 0;
    @Nullable
    private static ActiveTarget activeTarget = null;
    private static boolean visibleThisFrame = false;

    private TooltipScrollController() {}

    /** Called at the top of every {@code extractTooltip} frame, before any tooltip is drawn. */
    public static void beginFrame() {
        visibleThisFrame = false;
        frameCounter++;
    }

    /**
     * Called once per frame by the renderer after body layout and panel position are known.
     * Resets scroll on screen/slot/item change, clamps on disclosure change, and re-clamps against
     * the current overflow. Registers this frame's {@link ActiveTarget} for
     * {@link TotalityTooltipScrollHandler} to match against.
     */
    public static void onRender(@Nullable Screen screen, @Nullable Slot slot, ItemStack stack,
                                TooltipDisclosureLevel disclosure, int bodyContentHeight, int bodyViewportHeight,
                                int viewportX, int viewportY, int viewportWidth, int viewportHeight) {
        boolean identityChanged = currentStack == null
                || !ItemStack.isSameItemSameComponents(stack, currentStack)
                || slot != currentSlot
                || screen != currentScreen;
        if (identityChanged) {
            currentStack = stack.copy();
            currentSlot = slot;
            currentScreen = screen;
            scrollOffset = 0;
        }
        if (disclosure != lastDisclosure) {
            lastDisclosure = disclosure;
        }

        int maxScroll = computeMaxScroll(bodyContentHeight, bodyViewportHeight);
        scrollOffset = clampScroll(scrollOffset, maxScroll);
        visibleThisFrame = true;
        activeTarget = new ActiveTarget(screen, slot, maxScroll, viewportX, viewportY, viewportWidth, viewportHeight, frameCounter);
    }

    public static int scrollOffset() {
        return scrollOffset;
    }

    public static boolean isOverflowing() {
        return activeTarget != null && activeTarget.maxScroll() > 0;
    }

    /**
     * Returns true only if the wheel event actually changed the scroll offset (visual-correction
     * pass, micro-correction Finding 1) — requires the event's screen/slot/stack to match exactly
     * this frame's registered {@link ActiveTarget}, that target to be fresh (rendered this very
     * frame, not a stale leftover), the tooltip to actually be overflowing, AND the resulting
     * offset (after clamping to {@code [0, maxScroll]}) to differ from the current one. A wheel
     * event at the very top (scrolling further up) or very bottom (scrolling further down) of an
     * otherwise-matching, overflowing tooltip is therefore correctly left unconsumed, letting
     * normal screen scrolling take over at that boundary — the caller,
     * {@link TotalityTooltipScrollHandler}, only reports the event as handled when this returns
     * true.
     */
    public static boolean onMouseScroll(@Nullable Screen screen, @Nullable Slot slot, ItemStack stack, double scrollY) {
        if (!visibleThisFrame || activeTarget == null) return false;
        if (!targetMatches(activeTarget, screen, slot, frameCounter)) return false;
        if (currentStack == null || !ItemStack.isSameItemSameComponents(stack, currentStack)) return false;
        if (activeTarget.maxScroll() <= 0) return false;

        int newOffset = applyScrollDelta(scrollOffset, scrollY, SCROLL_STEP, activeTarget.maxScroll());
        if (newOffset == scrollOffset) return false;

        scrollOffset = newOffset;
        return true;
    }

    /**
     * Pure identity-matching check, extracted so it can be unit-tested without a real
     * {@code Screen}/{@code ItemStack}: the target's screen and slot must match exactly (by
     * reference, for {@code slot} — never by {@link Slot#index}), and the target must have been
     * registered on the current frame (not a stale one).
     */
    static boolean targetMatches(ActiveTarget target, @Nullable Screen screen, @Nullable Slot slot, long currentFrame) {
        return target.screen() == screen && target.slot() == slot && target.frameMarker() == currentFrame;
    }

    /**
     * Applies one wheel event's delta to {@code currentOffset}, scaled by {@code step} and clamped
     * to {@code [0, maxScroll]}. This is the unchanged direction convention from the original
     * implementation: wheel-down (negative {@code scrollY}) increases the offset — moving toward
     * later tooltip content; wheel-up (positive {@code scrollY}) decreases it — moving toward
     * earlier content. Pure arithmetic, extracted so the "did this event actually change anything"
     * decision {@link #onMouseScroll} makes is directly testable without a real
     * {@code ItemStack}/{@code Screen}/{@code Slot}.
     */
    static int applyScrollDelta(int currentOffset, double scrollY, int step, int maxScroll) {
        return clampScroll(currentOffset - (int) Math.round(scrollY * step), maxScroll);
    }

    /** Pure arithmetic, extracted so it can be unit-tested without a real {@code ItemStack}. */
    static int computeMaxScroll(int contentHeight, int viewportHeight) {
        return Math.max(0, contentHeight - viewportHeight);
    }

    /** Pure arithmetic, extracted so it can be unit-tested without a real {@code ItemStack}. */
    static int clampScroll(int offset, int maxScroll) {
        return Math.max(0, Math.min(offset, maxScroll));
    }

    static void reset() {
        currentStack = null;
        currentSlot = null;
        currentScreen = null;
        scrollOffset = 0;
        activeTarget = null;
        visibleThisFrame = false;
    }

    /** Registers the screen-close reset hook. Call once from client init. */
    public static void registerLifecycleHooks() {
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) ->
                ScreenEvents.remove(screen).register(removedScreen -> reset()));
    }
}
