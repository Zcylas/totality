package zcylas.totality.api.shop;

/**
 * Maps the EXISTING structured {@link BuyResult.Reason}/{@link SellResult.Reason} enums (economy
 * hardening pass, Part 5) to localization keys for {@link
 * zcylas.totality.networking.shop.TradeRejectionPayload} (Phase 4, Part D) — reuses those reasons
 * directly rather than inventing a parallel rejection-code system. {@link SellResult#detail()}
 * (for {@code NOT_SELLABLE}) is matched against the exact, fixed literal strings {@link
 * MerchantSellQuoteView} itself produces, so the finer "not accepted" / "no known value" /
 * "merchant can't afford it" distinction Part D's message list requires doesn't need a separate
 * reason-code refactor of that class. Pure, server-side only — the client never sees or trusts
 * raw text, only the resolved key.
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

    public static String forSell(SellResult.Reason reason, String detail) {
        return switch (reason) {
            case NO_SESSION, NPC_INVALID -> "totality.trading.reject.trade_ended";
            case INVALID_QUANTITY -> "totality.trading.reject.invalid_quantity";
            case INVALID_SLOT -> "totality.trading.reject.generic";
            case EMPTY_STACK -> "totality.trading.reject.item_gone";
            case INSUFFICIENT_STACK -> "totality.trading.reject.stock_changed";
            case CANNOT_RECEIVE -> "totality.trading.reject.generic";
            case NOT_SELLABLE -> forNotSellableDetail(detail);
        };
    }

    private static String forNotSellableDetail(String detail) {
        if (detail == null) return "totality.trading.reject.generic";
        if (detail.contains("does not accept")) return "totality.trading.reject.not_accepted";
        if (detail.contains("no resolvable value")) return "totality.trading.reject.no_value";
        if (detail.contains("cannot afford")) return "totality.trading.reject.merchant_cannot_afford";
        if (detail.contains("exceeds what the player's stack holds")) return "totality.trading.reject.invalid_quantity";
        return "totality.trading.reject.generic";
    }
}
