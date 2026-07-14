package zcylas.totality.api.shop;

import org.jetbrains.annotations.Nullable;

/**
 * Structured outcome of {@link TradeSessionManager#handleSell} (economy hardening pass, Part 5) —
 * see {@link BuyResult} for why this replaced per-rejection {@code WARN} logging.
 */
public record SellResult(boolean success, @Nullable Reason reason, @Nullable String detail, long payout, int quantitySold) {

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
        /** {@link MerchantSellQuoteView} rejected the stack — see {@link #detail()} for the reason. */
        NOT_SELLABLE,
        /** The player cannot safely receive the payout (e.g. would overflow their Wallet). */
        CANNOT_RECEIVE
    }

    public static SellResult success(long payout, int quantitySold) {
        return new SellResult(true, null, null, payout, quantitySold);
    }

    public static SellResult rejected(Reason reason) {
        return new SellResult(false, reason, null, 0L, 0);
    }

    public static SellResult rejected(Reason reason, String detail) {
        return new SellResult(false, reason, detail, 0L, 0);
    }
}
