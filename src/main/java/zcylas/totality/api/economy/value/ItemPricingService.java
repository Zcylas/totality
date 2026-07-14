package zcylas.totality.api.economy.value;

import net.minecraft.world.item.ItemStack;

import java.util.Optional;

/**
 * Context-aware pricing quotes for retail purchases and player sales (design document
 * Section 1b). Computes a quote only — {@code CreditPaymentHelper} still moves the actual
 * Credits; nothing here mutates any balance.
 *
 * <p>Retail = 100% of resolved base value.
 *
 * <p>SELL = the Phase 2 CANONICAL INTERIM rounding rule (design document Section 1f/1h),
 * replacing the earlier accidental-behavior placeholder: floor(baseValue / 2), with a minimum
 * payout of 1 Credit for any positive base value —
 * {@code baseValue == 0 ? 0 : Math.max(1L, baseValue / 2L)}. This is an explicit decision, not
 * incidental Java integer-division behavior, but it is still explicitly INTERIM — future
 * percentage modifiers and a transaction-total (vs. per-unit) rounding rule remain unresolved
 * and may supersede it later.
 */
public final class ItemPricingService {

    private ItemPricingService() {}

    /** @throws IllegalArgumentException if {@code context} isn't a RETAIL context, or if
     *          {@code quantity} isn't positive.
     *  @throws ArithmeticException if {@code unitPrice * quantity} would overflow a long. */
    public static Optional<PriceQuote> quoteRetail(ItemStack stack, int quantity, PricingContext context) {
        if (context.direction() != PricingDirection.RETAIL) {
            throw new IllegalArgumentException("quoteRetail requires a RETAIL PricingContext, got " + context.direction());
        }
        return quote(stack, quantity, context, false);
    }

    /** @throws IllegalArgumentException if {@code context} isn't a SELL context, or if
     *          {@code quantity} isn't positive.
     *  @throws ArithmeticException if {@code unitPrice * quantity} would overflow a long. */
    public static Optional<PriceQuote> quoteSell(ItemStack stack, int quantity, PricingContext context) {
        if (context.direction() != PricingDirection.SELL) {
            throw new IllegalArgumentException("quoteSell requires a SELL PricingContext, got " + context.direction());
        }
        return quote(stack, quantity, context, true);
    }

    private static Optional<PriceQuote> quote(ItemStack stack, int quantity, PricingContext context, boolean sell) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }

        long unitPrice;
        if (context.authoredOverride().isPresent()) {
            unitPrice = context.authoredOverride().get();
        } else {
            Optional<Long> baseValue = ItemValueRegistry.INSTANCE.resolveBaseValue(stack);
            // A negative authored base value is a datapack authoring error, not a legitimate
            // price — treated the same as "no resolvable value" rather than letting
            // sellUnitPayout's negative guard throw mid-quote.
            if (baseValue.isEmpty() || baseValue.get() < 0) return Optional.empty();
            unitPrice = sell ? sellUnitPayout(baseValue.get()) : baseValue.get();
        }

        long total = Math.multiplyExact(unitPrice, (long) quantity);
        return Optional.of(new PriceQuote(unitPrice, quantity, total, context.direction()));
    }

    /**
     * Phase 2 canonical interim SELL rounding rule (Section 1h): floor half the base value, but
     * never quote less than 1 Credit for an item that has ANY positive value — a base value of
     * exactly 0 is the sole exception and stays a 0-Credit quote. Public (rather than an
     * internal-only detail) specifically so it can be verified directly against values no real
     * authored item currently has (e.g. an odd value), without needing to fabricate registry
     * data just to exercise the rounding formula.
     *
     * @throws IllegalArgumentException if {@code baseValue} is negative — a negative base value
     *          is an authoring/data error, not a legitimate 0-Credit item, and must never be
     *          silently turned into a positive minimum payout.
     */
    public static long sellUnitPayout(long baseValue) {
        if (baseValue < 0) {
            throw new IllegalArgumentException("baseValue must not be negative, was " + baseValue);
        }
        if (baseValue == 0) return 0L;
        return Math.max(1L, baseValue / 2L);
    }
}
