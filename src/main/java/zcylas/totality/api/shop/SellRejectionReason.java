package zcylas.totality.api.shop;

/**
 * Structured reason a {@link MerchantSellQuoteView} is not currently sellable (Phase 4 correction
 * pass, Part E) — replaces {@link TradeRejectionKeys}' previous approach of inspecting fixed
 * English detail strings ("does not accept", "no resolvable value", ...) to decide which
 * localization key applies. The server/shared quote logic picks the reason directly; the client
 * never derives it from prose.
 *
 * <p>{@code NONE} means the quote IS currently sellable — a separate concern from {@link
 * MerchantSellQuoteView#requiresConfirmation()}, which can be true even when this is {@code NONE}
 * (an underfunded-but-sellable quote still needs the player's explicit consent to a reduced
 * payout, not a rejection).
 */
public enum SellRejectionReason {
    NONE,
    /** The merchant does not buy this item's category at all. */
    NOT_ACCEPTED,
    /** The item has no resolvable central {@code ItemValueRegistry} value. */
    NO_VALUE,
    /** The merchant's current Credits balance is exactly zero — SELL is unavailable outright,
     *  never a confirmation-eligible case (Part A: "do not make zero-Credit selling part of
     *  normal SELL behavior"). */
    MERCHANT_ZERO_CREDITS,
    /** {@code requestedQuantity} is non-positive or exceeds the stack/server-cap-bounded maximum. */
    INVALID_QUANTITY,
    /** The requested quantity's total value overflows a {@code long}. */
    OVERFLOW,
    /** Defensive-only fallback — an unresolvable SELL quote for a reason that shouldn't occur
     *  given {@code hasValue} was already confirmed true. */
    GENERIC
}
