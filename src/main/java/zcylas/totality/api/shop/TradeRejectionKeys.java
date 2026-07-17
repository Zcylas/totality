package zcylas.totality.api.shop;

import org.jetbrains.annotations.Nullable;

/**
 * Maps the EXISTING structured {@link BuyResult.Reason}/{@link SellResult.Reason} enums (economy
 * hardening pass, Part 5) to localization keys for {@link
 * zcylas.totality.networking.shop.TradeRejectionPayload} (Phase 4, Part D) — reuses those reasons
 * directly rather than inventing a parallel rejection-code system. {@link SellResult#quoteReason()}
 * (for {@code NOT_SELLABLE}) is a real {@link SellRejectionReason} enum value (Phase 4 correction
 * pass, Part E) — never inspected as free-text English prose. Pure, server-side only — the client
 * never sees or trusts raw text, only the resolved key.
 */
public final class TradeRejectionKeys {

    private TradeRejectionKeys() {}

    public static String forBuy(BuyResult.Reason reason) {
        return switch (reason) {
            case NO_SESSION, NPC_INVALID -> "totality.trading.reject.trade_ended";
            case INVALID_INDEX, INVALID_PRICE, INVALID_MERCHANT_STATE, OVERFLOW -> "totality.trading.reject.generic";
            case INVALID_QUANTITY -> "totality.trading.reject.invalid_quantity";
            case CANNOT_AFFORD -> "totality.trading.reject.cannot_afford";
            case OUT_OF_STOCK -> "totality.trading.reject.stock_changed";
        };
    }

    public static String forSell(SellResult.Reason reason, @Nullable SellRejectionReason quoteReason) {
        return switch (reason) {
            case NO_SESSION, NPC_INVALID -> "totality.trading.reject.trade_ended";
            case INVALID_QUANTITY -> "totality.trading.reject.invalid_quantity";
            case INVALID_SLOT -> "totality.trading.reject.generic";
            case EMPTY_STACK -> "totality.trading.reject.item_gone";
            case INSUFFICIENT_STACK -> "totality.trading.reject.stock_changed";
            case CANNOT_RECEIVE -> "totality.trading.reject.generic";
            case CONFIRMATION_REQUIRED -> "totality.trading.reject.confirmation_required";
            case STALE_CONFIRMATION -> "totality.trading.reject.stale_confirmation";
            case NOT_SELLABLE -> forQuoteReason(quoteReason);
        };
    }

    private static String forQuoteReason(@Nullable SellRejectionReason reason) {
        if (reason == null) return "totality.trading.reject.generic";
        return switch (reason) {
            case NOT_ACCEPTED -> "totality.trading.reject.not_accepted";
            case NO_VALUE -> "totality.trading.reject.no_value";
            case MERCHANT_ZERO_CREDITS -> "totality.trading.reject.merchant_zero_credits";
            case INVALID_QUANTITY -> "totality.trading.reject.invalid_quantity";
            case NONE, OVERFLOW, GENERIC -> "totality.trading.reject.generic";
        };
    }
}
