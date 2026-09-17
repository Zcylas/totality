package zcylas.totality.api.soulgem;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.item.soulgem.SoulGemItem;

/**
 * Minimal domain helper for attempting to insert a known {@link CapturedSoul} into a Soul Gem
 * ItemStack. This is NOT a live Entity-capture pipeline — no Soul Trap exists yet; a caller (test or
 * future system) must already have a {@link CapturedSoul} to offer. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §13, and its 2026-09-17
 * review-correction section for the stacked-vessel fix below.
 *
 * <p><b>One-vessel-only invariant (2026-09-17 review correction):</b> {@code stack.set(...)} mutates
 * the data component shared by every physical item represented by that one {@code ItemStack} — safe
 * only when {@code stack.getCount() == 1}. Capturing into a stack of 2+ empty gems would otherwise
 * write the same {@link CapturedSoul} (the same {@code soulInstanceId}) onto every physical gem in
 * that stack, which is exactly the "two captured zombies must never share an instance id" invariant
 * {@link CapturedSoul} itself exists to prevent — so this service refuses instead, via {@link
 * SoulCaptureResult#STACKED_VESSEL_REQUIRES_SPLIT}, rather than silently cloning one soul across
 * multiple physical vessels. Splitting a stack down to one gem and placing the result back is future
 * Soul Trap/inventory-logic work, deliberately out of scope for this small domain helper — see
 * {@code SoulGemSystemVerification} for the "future intended flow" this leaves room for.
 *
 * <p>Exact check order, and why: (1) {@code NOT_A_SOUL_GEM} — not even the right item family; (2)
 * {@code ALREADY_FILLED} — a fact about the vessel's own state, independent of stack count, and the
 * most informative failure when both conditions happen to hold (a stacked, already-filled gem stays
 * {@code ALREADY_FILLED}, not {@code STACKED_VESSEL_REQUIRES_SPLIT} — this ordering is deliberately
 * unchanged from the original foundation pass); (3) {@link SoulCaptureResult#STACKED_VESSEL_REQUIRES_SPLIT}
 * — still a precondition on the vessel's physical shape, checked before any judgment about the soul
 * being offered, so a stacked vessel is never misreported as e.g. {@code RANK_TOO_STRONG}; (4)
 * {@code RANK_ZERO_INELIGIBLE} (§8's global eligibility gate); (5) {@code CATEGORY_REJECTED}; (6)
 * {@code RANK_TOO_STRONG}; (7) write. A failed attempt at any stage leaves {@code stack} completely
 * unchanged — count, components, and any existing soul all untouched.
 */
public final class SoulCaptureService {

    public static SoulCaptureResult attemptCapture(ItemStack stack, CapturedSoul soul) {
        if (!(stack.getItem() instanceof SoulGemItem gem)) {
            return SoulCaptureResult.NOT_A_SOUL_GEM;
        }
        if (stack.has(CapturedSoulComponent.CAPTURED_SOUL)) {
            return SoulCaptureResult.ALREADY_FILLED;
        }
        if (stack.getCount() > 1) {
            return SoulCaptureResult.STACKED_VESSEL_REQUIRES_SPLIT;
        }
        if (!SoulCaptureEligibility.isEligibleForCapture(soul.rank())) {
            return SoulCaptureResult.RANK_ZERO_INELIGIBLE;
        }
        SoulGemAcceptanceRule rule = gem.acceptanceRule();
        if (!rule.acceptsCategory(soul.category())) {
            return SoulCaptureResult.CATEGORY_REJECTED;
        }
        if (!rule.acceptsRank(soul.rank())) {
            return SoulCaptureResult.RANK_TOO_STRONG;
        }
        stack.set(CapturedSoulComponent.CAPTURED_SOUL, soul);
        return SoulCaptureResult.SUCCESS;
    }

    private SoulCaptureService() {}
}
