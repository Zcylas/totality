package zcylas.totality.api.economy.value;

/**
 * RETAIL (player buys from a merchant) or SELL (player sells to a merchant) — a minimal
 * placeholder scoped ONLY to pricing quotes. {@code TOTALITY_ECONOMY_BANKING_TRADING.txt}
 * Section 6 proposes a broader {@code TransactionType} (DEPOSIT/WITHDRAWAL/TRANSFER/PURCHASE/
 * SALE/etc.) for a future shared Economy Transaction API — this is deliberately NOT that enum,
 * to avoid pretending ledger/refund/transfer semantics exist yet. Align the two later, if/when
 * that broader API is actually built, rather than inventing unrelated meanings for values it
 * doesn't use here.
 */
public enum PricingDirection {
    RETAIL,
    SELL
}
