package zcylas.totality.api.shop;

import org.jetbrains.annotations.Nullable;

/**
 * Structured outcome of {@link TradeSessionManager#handleSell} (economy hardening pass, Part 5) —
 * see {@link BuyResult} for why this replaced per-rejection {@code WARN} logging.
 *
 * <p>Phase 4 correction pass, Part E: {@code quoteReason} replaces the previous free-text
 * {@code detail} string for {@code NOT_SELLABLE} — {@link TradeRejectionKeys} maps it directly,
 * never by inspecting English prose.
 */
public record SellResult(
        boolean success, @Nullable Reason reason, @Nullable SellRejectionReason quoteReason, long payout, int quantitySold) {

    public enum Reason {
        /** No active trade session for this player. */
        NO_SESSION,
        /** Entity-backed session failed revalidation (NPC gone/removed/wrong dimension/out of range). */
        NPC_INVALID,
        /** {@code quantity} was not positive — never produced by the normal SELL UI. */
        INVALID_QUANTITY,
        /** {@code slotIndex} is outside the player's inventory — never produced by the normal SELL UI. */
        INVALID_SLOT,
        /** The targeted slot holds nothing. */
        EMPTY_STACK,
        /** The targeted slot holds fewer items than requested (stack changed since the client's quote). */
        INSUFFICIENT_STACK,
        /** {@link MerchantSellQuoteView} rejected the stack — see {@link #quoteReason()} for the reason. */
        NOT_SELLABLE,
        /** The player cannot safely receive the payout (e.g. would overflow their Wallet). */
        CANNOT_RECEIVE,
        /** The quote is sellable but the merchant cannot fully afford {@code totalValue}, and the
         *  request did not carry explicit confirmation of the reduced payout (Phase 4 correction
         *  pass, Part A). */
        CONFIRMATION_REQUIRED,
        /** The request carried confirmation, but the confirmed terms no longer match the current,
         *  freshly recomputed quote (merchant Credits, price, or quantity changed since the
         *  player confirmed) — rejected rather than silently honoring stale/worse terms. */
        STALE_CONFIRMATION
    }

    public static SellResult success(long payout, int quantitySold) {
        return new SellResult(true, null, null, payout, quantitySold);
    }

    public static SellResult rejected(Reason reason) {
        return new SellResult(false, reason, null, 0L, 0);
    }

    public static SellResult rejected(Reason reason, SellRejectionReason quoteReason) {
        return new SellResult(false, reason, quoteReason, 0L, 0);
    }
}
