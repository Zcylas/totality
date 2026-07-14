package zcylas.totality.api.shop;

import org.jetbrains.annotations.Nullable;

/**
 * Structured outcome of {@link TradeSessionManager#handleBuy} (economy hardening pass, Part 5) —
 * replaces the previous behavior of logging a {@code WARN} line for every rejection, most of
 * which are ordinary gameplay outcomes (can't afford it, stale session) rather than anything
 * suspicious. Callers (network handlers, verification) inspect this instead of scraping logs.
 */
public record BuyResult(boolean success, @Nullable Reason reason, long totalCost, int quantityBought) {

    public enum Reason {
        /** No active trade session for this player. */
        NO_SESSION,
        /** Entity-backed session failed revalidation (NPC gone/removed/wrong dimension/out of range). */
        NPC_INVALID,
        /** {@code index} does not address a real entry in the shop's catalog. */
        INVALID_INDEX,
        /** The catalog entry itself is malformed (negative authored price) — an authoring error. */
        INVALID_PRICE,
        /** {@code price * quantity}, or the merchant's resulting balance, would overflow a long. */
        OVERFLOW,
        /** The player cannot afford {@code total} right now. */
        CANNOT_AFFORD,
        /** A stock-backed entry's current stock is less than the requested quantity (Phase 3). */
        OUT_OF_STOCK
    }

    public static BuyResult success(long totalCost, int quantityBought) {
        return new BuyResult(true, null, totalCost, quantityBought);
    }

    public static BuyResult rejected(Reason reason) {
        return new BuyResult(false, reason, 0L, 0);
    }
}
