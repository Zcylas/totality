package zcylas.totality.api.shop;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.economy.value.ItemPricingService;
import zcylas.totality.api.economy.value.ItemValueRegistry;
import zcylas.totality.api.economy.value.PriceQuote;
import zcylas.totality.api.economy.value.PricingContext;
import zcylas.totality.api.economy.value.PricingDirection;

import java.util.Optional;

/**
 * Server-computed, display-ready SELL state for one stack against one merchant (design document
 * Section G). Purely informational for a future client display — the server ALWAYS recomputes
 * and revalidates this same information at actual SELL submission time
 * ({@link TradeSessionManager#handleSell}), which does not trust anything the client previously
 * displayed.
 *
 * <p>Also doubles as the shared pure computation both a future SELL UI and this phase's
 * {@code handleSell}/self-tests can call, so the "is this sellable and for how much" logic exists
 * exactly once.
 *
 * <p><b>Phase 4 correction pass, Part A:</b> the selectable quantity is bounded by the stack count
 * (and the server cap), never by merchant affordability — {@code effectiveMaxQuantity} equals
 * {@code maxQuantityByStack} whenever the merchant has any positive Credits at all. Merchant
 * affordability instead governs {@code payableAmount}/{@code forfeitedValue}/{@code
 * requiresConfirmation}: the full quoted {@code totalValue} for the requested quantity is never
 * silently clamped, and a quote whose value exceeds the merchant's current Credits is still
 * {@code sellable()} — it just additionally requires the player's explicit confirmation
 * ({@link TradeSessionManager#handleSell}) before committing at the reduced {@code payableAmount}.
 * A merchant with exactly zero Credits is a distinct, non-confirmation-eligible case
 * ({@link SellRejectionReason#MERCHANT_ZERO_CREDITS}) — SELL is unavailable outright, not offered
 * as "sell for ₵0".
 */
public record MerchantSellQuoteView(
        boolean accepted,
        boolean hasValue,
        long unitPayout,
        int maxQuantityByStack,
        int effectiveMaxQuantity,
        int requestedQuantity,
        /** Full quoted value of {@code requestedQuantity} units — NEVER clamped to what the
         *  merchant can currently pay. */
        long totalValue,
        /** The merchant's current Credits balance, snapshotted at compute time (never negative —
         *  a corrupt negative balance reads as 0 here; {@code TradeSessionManager} separately
         *  refuses to transact at all against a corrupt merchant). */
        long merchantCredits,
        /** {@code min(totalValue, merchantCredits)} — what the merchant can actually pay right
         *  now for {@code requestedQuantity}. Equals {@code totalValue} whenever the merchant can
         *  fully afford it. */
        long payableAmount,
        /** {@code totalValue - payableAmount} — what the player would forfeit by accepting the
         *  reduced payout. Zero whenever the merchant can fully afford the sale. */
        long forfeitedValue,
        /** True only when this quote is otherwise sellable AND {@code forfeitedValue > 0} — the
         *  merchant can afford SOME but not all of the requested value. A separate concern from
         *  {@link #sellable()}: an underfunded-but-sellable quote still requires the player's
         *  explicit confirmation before committing. */
        boolean requiresConfirmation,
        SellRejectionReason rejectionReason
) {
    /** Matches the existing BUY quantity cap ({@code TradingScreen.MAX_QUANTITY}) so SELL and
     *  BUY share the same sane per-transaction quantity bound. */
    public static final int MAX_SELL_QUANTITY = 100;

    public boolean sellable() {
        return rejectionReason == SellRejectionReason.NONE;
    }

    public static MerchantSellQuoteView compute(
            ServerPlayer player, MerchantRuntime merchant, ItemStack stack, int stackCount, int requestedQuantity
    ) {
        boolean accepted = merchant.accepts(stack);
        Optional<Long> baseValue = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
        boolean hasValue = baseValue.isPresent();
        // A corrupt (negative) merchant balance is never displayed as spendable — TradeSessionManager
        // separately refuses to transact against one at all (defense-in-depth, unchanged).
        long merchantCredits = Math.max(0, merchant.currentCredits());

        if (!accepted) {
            return unsellable(false, hasValue, requestedQuantity, merchantCredits, SellRejectionReason.NOT_ACCEPTED);
        }
        if (!hasValue) {
            return unsellable(true, false, requestedQuantity, merchantCredits, SellRejectionReason.NO_VALUE);
        }

        PricingContext context = new PricingContext(
                player, Optional.of(merchant.merchantId()), PricingDirection.SELL, Optional.empty());
        Optional<PriceQuote> unitQuote = ItemPricingService.quoteSell(stack, 1, context);
        if (unitQuote.isEmpty()) {
            // Defensive only — hasValue being true means quoteSell should never fail here.
            return unsellable(true, true, requestedQuantity, merchantCredits, SellRejectionReason.GENERIC);
        }
        long unitPayout = unitQuote.get().unitPrice();
        int maxQuantityByStack = Math.max(0, Math.min(MAX_SELL_QUANTITY, stackCount));

        if (merchantCredits <= 0) {
            // Distinct, non-confirmation-eligible case (Part A) — SELL is unavailable outright,
            // never offered as an implicit "sell for ₵0" free-disposal transaction.
            return new MerchantSellQuoteView(true, true, unitPayout, maxQuantityByStack, 0,
                    requestedQuantity, 0L, 0L, 0L, 0L, false, SellRejectionReason.MERCHANT_ZERO_CREDITS);
        }

        SellRejectionReason rejection = SellRejectionReason.NONE;
        if (requestedQuantity <= 0 || requestedQuantity > maxQuantityByStack) {
            rejection = SellRejectionReason.INVALID_QUANTITY;
        }

        long totalValue = 0L;
        if (rejection == SellRejectionReason.NONE) {
            try {
                totalValue = Math.multiplyExact(unitPayout, (long) requestedQuantity);
            } catch (ArithmeticException overflow) {
                rejection = SellRejectionReason.OVERFLOW;
            }
        }

        if (rejection != SellRejectionReason.NONE) {
            return new MerchantSellQuoteView(true, true, unitPayout, maxQuantityByStack, maxQuantityByStack,
                    requestedQuantity, 0L, merchantCredits, 0L, 0L, false, rejection);
        }

        long payableAmount = Math.min(totalValue, merchantCredits);
        long forfeitedValue = totalValue - payableAmount;
        boolean requiresConfirmation = forfeitedValue > 0;

        return new MerchantSellQuoteView(true, true, unitPayout, maxQuantityByStack, maxQuantityByStack,
                requestedQuantity, totalValue, merchantCredits, payableAmount, forfeitedValue,
                requiresConfirmation, SellRejectionReason.NONE);
    }

    private static MerchantSellQuoteView unsellable(
            boolean accepted, boolean hasValue, int requestedQuantity, long merchantCredits, SellRejectionReason reason) {
        return new MerchantSellQuoteView(accepted, hasValue, 0L, 0, 0, requestedQuantity, 0L, merchantCredits, 0L, 0L, false, reason);
    }
}
