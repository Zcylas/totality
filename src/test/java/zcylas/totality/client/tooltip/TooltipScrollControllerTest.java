package zcylas.totality.client.tooltip;

import net.minecraft.world.inventory.Slot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure tests for scroll-offset arithmetic, the "not currently visible" guard, the active-target
 * identity-matching logic (visual-correction pass, Finding 7), and the offset-actually-changed
 * consumption decision plus slot-identity uniqueness (micro-correction Findings 1 and 2).
 * Deliberately never constructs a real {@code ItemStack} (not even {@code ItemStack.EMPTY}, which
 * sits in a static field and would force the whole class to load) — confirmed by direct probing in
 * a prior task that doing so throws before the item registry is bootstrapped, the same constraint
 * documented in {@code HealingPotionItemContractTest}'s class Javadoc. {@code null} is a valid,
 * genuinely distinct reference for {@code Screen}-typed fields/parameters here. {@code Slot}, by
 * contrast, genuinely can be constructed under plain JUnit — its constructor
 * ({@code Slot(Container, int, int, int)}) only assigns fields, confirmed by decompiling
 * {@code minecraft-merged.jar}'s bytecode, so a {@code null} {@code Container} argument is safe and
 * no registry is ever touched. This is exactly what makes the Finding 2 collision test below a
 * genuine proof rather than a simulation: two distinct, real {@code Slot} objects sharing the same
 * {@code .index} value are constructed and shown not to match. Only the full
 * {@code onRender}/{@code onMouseScroll} integration (which needs a real {@code ItemStack}) remains
 * untestable here, and is instead covered by {@code TooltipApiFoundationSourceRegressionTest}
 * sentinels plus manual client validation. {@link TooltipScrollController#computeMaxScroll},
 * {@link TooltipScrollController#clampScroll}, and {@link TooltipScrollController#applyScrollDelta}
 * were extracted specifically so the arithmetic {@code onRender}/{@code onMouseScroll} delegate to
 * remains testable in isolation.
 */
class TooltipScrollControllerTest {

    @AfterEach
    void resetState() {
        TooltipScrollController.reset();
    }

    @Test
    void scrollDoesNothingWhenNothingIsVisibleThisFrame() {
        TooltipScrollController.beginFrame();
        assertFalse(TooltipScrollController.onMouseScroll(null, null, null, -1));
    }

    @Test
    void computeMaxScrollIsZeroWhenContentFitsTheViewport() {
        assertEquals(0, TooltipScrollController.computeMaxScroll(50, 50));
        assertEquals(0, TooltipScrollController.computeMaxScroll(30, 50));
    }

    @Test
    void computeMaxScrollIsTheOverflowAmount() {
        assertEquals(150, TooltipScrollController.computeMaxScroll(200, 50));
    }

    @Test
    void clampScrollNeverGoesNegative() {
        assertEquals(0, TooltipScrollController.clampScroll(-40, 150));
    }

    @Test
    void clampScrollNeverExceedsMaxScroll() {
        assertEquals(150, TooltipScrollController.clampScroll(9999, 150));
    }

    @Test
    void clampScrollPassesThroughValuesAlreadyInRange() {
        assertEquals(75, TooltipScrollController.clampScroll(75, 150));
    }

    @Test
    void clampScrollWithZeroMaxScrollAlwaysClampsToZero() {
        // The "content fits, nothing to scroll" case — must not divide-by-zero or go negative.
        assertEquals(0, TooltipScrollController.clampScroll(30, 0));
        assertEquals(0, TooltipScrollController.clampScroll(-30, 0));
    }

    // ── applyScrollDelta (micro-correction Finding 1: consume only when offset actually changes) ──
    // The six required proofs, against the exact helper production's onMouseScroll delegates to.

    @Test
    void middlePositionWheelDownChangesOffset() {
        // Wheel down (negative scrollY, the established convention) moves toward later content.
        int newOffset = TooltipScrollController.applyScrollDelta(50, -1, TooltipScrollController.SCROLL_STEP, 100);
        assertEquals(58, newOffset);
        assertNotEquals(50, newOffset, "a mid-tooltip wheel-down must actually change the offset");
    }

    @Test
    void middlePositionWheelUpChangesOffset() {
        // Wheel up (positive scrollY) moves toward earlier content.
        int newOffset = TooltipScrollController.applyScrollDelta(50, 1, TooltipScrollController.SCROLL_STEP, 100);
        assertEquals(42, newOffset);
        assertNotEquals(50, newOffset, "a mid-tooltip wheel-up must actually change the offset");
    }

    @Test
    void topPositionWheelUpDoesNotChangeOffset() {
        int newOffset = TooltipScrollController.applyScrollDelta(0, 1, TooltipScrollController.SCROLL_STEP, 100);
        assertEquals(0, newOffset, "already at the top — scrolling further up must clamp, not go negative, "
                + "so the caller correctly treats this as an unconsumed event");
    }

    @Test
    void bottomPositionWheelDownDoesNotChangeOffset() {
        int newOffset = TooltipScrollController.applyScrollDelta(100, -1, TooltipScrollController.SCROLL_STEP, 100);
        assertEquals(100, newOffset, "already at the bottom — scrolling further down must clamp, not exceed "
                + "maxScroll, so the caller correctly treats this as an unconsumed event");
    }

    @Test
    void tinyDeltaThatRoundsToZeroDoesNotChangeOffset() {
        int newOffset = TooltipScrollController.applyScrollDelta(50, 0.001, TooltipScrollController.SCROLL_STEP, 100);
        assertEquals(50, newOffset, "a delta that rounds to zero pixels must not register as a change");
    }

    @Test
    void nonOverflowingTargetNeverHasAnyOffsetToChange() {
        // The 6th required proof ("non-overflowing target does not consume"): with maxScroll = 0,
        // clampScroll forces every candidate offset to 0 regardless of direction — there is no
        // representable "changed" state, matching onMouseScroll's own explicit maxScroll <= 0
        // early-return (see the wiring sentinel in TooltipApiFoundationSourceRegressionTest).
        assertEquals(0, TooltipScrollController.applyScrollDelta(0, -1, TooltipScrollController.SCROLL_STEP, 0));
        assertEquals(0, TooltipScrollController.applyScrollDelta(0, 1, TooltipScrollController.SCROLL_STEP, 0));
    }

    // ── Active-target identity matching (Finding 7; slot identity fixed in micro-correction Finding 2) ──

    @Test
    void targetMatchesTheExactSameSlotObject() {
        Slot slot = new Slot(null, 0, 0, 0);
        var target = new TooltipScrollController.ActiveTarget(null, slot, 150, 0, 0, 0, 0, 42L);
        assertTrue(TooltipScrollController.targetMatches(target, null, slot, 42L));
    }

    @Test
    void slotsWithTheSameIndexButDifferentIdentityDoNotMatch() {
        // The exact collision Slot.index alone cannot prevent: a player-inventory slot and an
        // external-container slot can legitimately share the same .index value, since it is only
        // unique within that slot's own backing Container. Two distinct, genuinely constructed
        // Slot objects sharing index = 0 simulate exactly that — reference-identity matching (not
        // index equality) is what correctly tells them apart.
        Slot playerSlot = new Slot(null, 0, 0, 0);
        Slot externalContainerSlot = new Slot(null, 0, 0, 0);
        var target = new TooltipScrollController.ActiveTarget(null, playerSlot, 150, 0, 0, 0, 0, 42L);
        assertFalse(TooltipScrollController.targetMatches(target, null, externalContainerSlot, 42L),
                "two distinct slots sharing the same Slot.index must not be treated as the same slot");
    }

    @Test
    void targetDoesNotMatchADifferentSlot() {
        Slot slotA = new Slot(null, 3, 0, 0);
        Slot slotB = new Slot(null, 5, 0, 0);
        var target = new TooltipScrollController.ActiveTarget(null, slotA, 150, 0, 0, 0, 0, 42L);
        assertFalse(TooltipScrollController.targetMatches(target, null, slotB, 42L));
    }

    @Test
    void targetDoesNotMatchAStaleFrame() {
        // Requirement: a wheel event arriving after a frame in which nothing rendered (the cursor
        // already left the slot) must not act on a leftover target from several frames ago.
        Slot slot = new Slot(null, 3, 0, 0);
        var target = new TooltipScrollController.ActiveTarget(null, slot, 150, 0, 0, 0, 0, 42L);
        assertFalse(TooltipScrollController.targetMatches(target, null, slot, 43L));
    }

    @Test
    void nullSlotOnlyMatchesNullSlot() {
        // null represents "no hovered slot" — must match only itself, never an actual slot.
        var target = new TooltipScrollController.ActiveTarget(null, null, 0, 0, 0, 0, 0, 1L);
        assertTrue(TooltipScrollController.targetMatches(target, null, null, 1L));

        Slot someSlot = new Slot(null, 0, 0, 0);
        assertFalse(TooltipScrollController.targetMatches(target, null, someSlot, 1L));
    }
}
