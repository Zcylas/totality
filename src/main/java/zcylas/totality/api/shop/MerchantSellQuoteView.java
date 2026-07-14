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
 */
public record MerchantSellQuoteView(
        boolean accepted,
        boolean hasValue,
        long unitPayout,
        int maxQuantityByStack,
        int maxQuantityByMerchant,
        int effectiveMaxQuantity,
        int requestedQuantity,
        long totalPayout,
        Optional<String> rejectionReason
) {
    /** Matches the existing BUY quantity cap ({@code TradingScreen.MAX_QUANTITY}) so SELL and
     *  BUY share the same sane per-transaction quantity bound. */
    public static final int MAX_SELL_QUANTITY = 100;

    public boolean sellable() {
        return rejectionReason.isEmpty();
    }

    public static MerchantSellQuoteView compute(
            ServerPlayer player, MerchantRuntime merchant, ItemStack stack, int stackCount, int requestedQuantity
    ) {
        boolean accepted = merchant.accepts(stack);
        Optional<Long> baseValue = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
        boolean hasValue = baseValue.isPresent();

        if (!accepted) {
            return unsellable(accepted, hasValue, requestedQuantity, "Merchant does not accept this item");
        }
        if (!hasValue) {
            return unsellable(accepted, hasValue, requestedQuantity, "Item has no resolvable value");
        }

        PricingContext context = new PricingContext(
                player, Optional.of(merchant.merchantId()), PricingDirection.SELL, Optional.empty());
        Optional<PriceQuote> unitQuote = ItemPricingService.quoteSell(stack, 1, context);
        if (unitQuote.isEmpty()) {
            // Defensive only — hasValue being true means quoteSell should never fail here.
            return unsellable(accepted, hasValue, requestedQuantity, "Unable to compute a SELL quote");
        }
        long unitPayout = unitQuote.get().unitPrice();

        int maxQuantityByStack = Math.max(0, Math.min(MAX_SELL_QUANTITY, stackCount));
        int maxQuantityByMerchant = unitPayout <= 0
                ? MAX_SELL_QUANTITY
                : (int) Math.min(MAX_SELL_QUANTITY, merchant.currentCredits() / unitPayout);
        int effectiveMaxQuantity = Math.max(0, Math.min(maxQuantityByStack, maxQuantityByMerchant));

        Optional<String> rejection = Optional.empty();
        if (requestedQuantity <= 0) {
            rejection = Optional.of("Requested quantity must be positive");
        } else if (requestedQuantity > maxQuantityByStack) {
            rejection = Optional.of("Requested quantity exceeds what the player's stack holds");
        } else if (requestedQuantity > maxQuantityByMerchant) {
            rejection = Optional.of("Merchant cannot afford that quantity");
        }

        long totalPayout;
        try {
            totalPayout = Math.multiplyExact(unitPayout, (long) Math.max(0, requestedQuantity));
        } catch (ArithmeticException overflow) {
            rejection = Optional.of("Requested quantity overflows the total payout");
            totalPayout = 0L;
        }

        return new MerchantSellQuoteView(true, true, unitPayout, maxQuantityByStack, maxQuantityByMerchant,
                effectiveMaxQuantity, requestedQuantity, totalPayout, rejection);
    }

    private static MerchantSellQuoteView unsellable(boolean accepted, boolean hasValue, int requestedQuantity, String reason) {
        return new MerchantSellQuoteView(accepted, hasValue, 0L, 0, 0, 0, requestedQuantity, 0L, Optional.of(reason));
    }
}
