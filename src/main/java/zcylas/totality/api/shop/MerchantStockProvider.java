package zcylas.totality.api.shop;

import java.util.List;

/**
 * Per-entity BUY stock (design document Section 3a/7) — deliberately a SEPARATE interface from
 * {@link MerchantRuntime} rather than folding stock into the Credits-only contract: a merchant's
 * business Credits and its randomized sale stock are different concerns (confirmed by
 * {@code trade_screen.png}, which shows them as separate UI elements), and non-stock-backed
 * merchants (the existing shared {@link ShopTemplate}/{@link ShopRegistry} shops) have no need to
 * implement this at all.
 *
 * <p>Implementations must never remove or reorder an entry merely because it reaches zero stock —
 * the client identifies BUY entries by index, so a sold-out entry stays in its position, simply
 * unavailable, matching {@link MerchantRuntime}'s existing precedent of representing "unusable"
 * as a queryable state rather than deleting anything.
 */
public interface MerchantStockProvider {

    /** A snapshot of every stock entry, in stable order. Each {@link MerchantStockEntry} is
     *  itself immutable and already returns defensive item copies — no caller can mutate this
     *  provider's internal stock through the returned list or its elements. */
    List<MerchantStockEntry> stockEntries();

    /** True if {@code entryIndex} is valid, {@code quantity} is positive, and the entry currently
     *  holds at least {@code quantity} — the read-only check a caller must perform BEFORE relying
     *  on {@link #decrementStock(int, int)} to succeed. */
    boolean canPurchaseStock(int entryIndex, int quantity);

    /** Attempts to remove exactly {@code quantity} from the entry at {@code entryIndex}. Returns
     *  {@code false} (no state changed) if {@link #canPurchaseStock(int, int)} would have been
     *  false for the same arguments — callers that already validated with that method are
     *  guaranteed this succeeds, since nothing about server-thread-only mutation can invalidate
     *  that check between the two calls. */
    boolean decrementStock(int entryIndex, int quantity);
}
