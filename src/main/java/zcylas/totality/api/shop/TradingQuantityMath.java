package zcylas.totality.api.shop;

/**
 * Pure BUY/SELL quantity math shared by {@link zcylas.totality.screen.shop.TradingScreen} (the
 * single client-side source of truth for the currently selected quantity, design document Phase 4
 * Part C) and its verification suite. Deliberately has no client-only dependencies (no GL/render
 * types) so it can be exercised directly from server-side/dev-environment verification without
 * needing a real {@code Screen}.
 *
 * <p>This is the fix for the Phase 3 quantity bug (Phase 4 audit finding): the OLD {@code
 * TradingScreen} capped its quantity slider at a flat {@code MAX_QUANTITY = 100} only — it never
 * read {@link zcylas.totality.networking.shop.ShopEntryDisplayData#limitedStock()}/{@code
 * availableStock()} at all, so a player could freely select a quantity above a Provisioner entry's
 * REAL current stock. The server correctly rejected the resulting BUY ({@code OUT_OF_STOCK}), but
 * the old screen had no rejection-feedback path either (design document Part D, also new this
 * phase), so the purchase simply appeared to silently do nothing whenever the selected quantity
 * exceeded the entry's actual stock — matching the reported "can only successfully buy one item at
 * a time" symptom for any low-stock entry. This class's {@link #maxBuyQuantity} is the actual fix:
 * the cap is now dynamically {@code min(100, availableStock-if-limited, maxAffordable)}, so the UI
 * can never let the player select an invalid quantity in the first place.
 */
public final class TradingQuantityMath {

    /** Server-enforced ceiling (design document Section 6 / {@code TradeSessionManager}) — the
     *  client cap must never exceed this, or a "valid-looking" client quantity could still be
     *  rejected server-side with no way for the UI to have anticipated it. */
    public static final int SERVER_MAX_QUANTITY = 100;

    private TradingQuantityMath() {}

    /**
     * The maximum BUY quantity the UI should ever offer for one catalog entry:
     * {@code min(100, current available stock when limited, maximum quantity affordable by the
     * player)}. A unit price {@code <= 0} never divides — such an entry is capped only by stock/100
     * (Section 6: "a unit price of 0 does not create division-by-zero behavior"). A sold-out
     * limited entry ({@code availableStock <= 0}) always returns 0, matching an unaffordable entry
     * (funds {@code < unitPrice}, also 0) — never an invalid positive quantity in either case.
     */
    public static int maxBuyQuantity(boolean limitedStock, int availableStock, long unitPrice, long funds) {
        int cap = SERVER_MAX_QUANTITY;
        if (limitedStock) {
            cap = Math.min(cap, Math.max(0, availableStock));
        }
        if (cap <= 0) return 0;
        if (unitPrice <= 0) return cap;
        if (funds <= 0) return 0;
        long affordable = funds / unitPrice;
        int affordableInt = affordable >= SERVER_MAX_QUANTITY ? SERVER_MAX_QUANTITY : (int) affordable;
        return Math.min(cap, affordableInt);
    }

    /** Clamps {@code desired} into {@code [0, max]} without ever silently converting an
     *  out-of-range request into an unrelated positive value (Section 6: "quantity must never be
     *  silently converted from 0 or an excessive value") — a {@code max} of 0 always yields 0, a
     *  negative {@code desired} always yields 0, never {@code max}'s own value by accident. */
    public static int clamp(int desired, int max) {
        if (max <= 0) return 0;
        if (desired < 0) return 0;
        return Math.min(desired, max);
    }

    /**
     * Preserve-if-valid-else-clamp-down rule for a state refresh (Section 6: "a server-state
     * refresh should preserve the current quantity when still valid... otherwise clamp it downward
     * to the new valid maximum... never increase quantity automatically after a refresh"). Never
     * returns a value greater than {@code previous} even if {@code newMax} would otherwise allow it.
     */
    public static int reconcileAfterRefresh(int previous, int newMax) {
        if (newMax <= 0) return 0;
        return Math.min(previous, newMax);
    }

    /** Checked display total — never authoritative (the server always recomputes/revalidates at
     *  commit), but must not silently wrap on overflow either (Section 6: "must use checked
     *  arithmetic or already server-issued safe values"). Returns {@code -1} to signal overflow
     *  (quantity is capped at 100 and price at a sane range in practice, so this is defensive, not
     *  expected to trigger under normal data). */
    public static long checkedTotal(long unitAmount, int quantity) {
        try {
            return Math.multiplyExact(unitAmount, (long) quantity);
        } catch (ArithmeticException overflow) {
            return -1L;
        }
    }
}
