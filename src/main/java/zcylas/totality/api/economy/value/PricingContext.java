package zcylas.totality.api.economy.value;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.Optional;

/**
 * Transaction context for a price quote (design document Section 1b). {@code player} and
 * {@code merchantId} are carried but UNUSED by this phase's arithmetic — they exist so a
 * future Relationship/reputation/Persuasion/merchant-specialization modifier can be added
 * later by extending this record, without changing {@link ItemPricingService}'s method
 * signatures or any of its call sites.
 *
 * <p>Invalid state is rejected at construction rather than left for a caller to notice later:
 * no field may be null (use {@code Optional.empty()} for the optional ones, never a null
 * {@code Optional} reference), and a present {@code authoredOverride} must not be negative —
 * an override replaces the resolved price outright, so it needs the same "no negative unit
 * price" guarantee {@link ItemValueRegistry} already enforces on ordinary base values. Zero
 * remains a legal override, matching that same design decision (Section 5: a value of zero
 * carries no sellability meaning by itself).
 *
 * @param authoredOverride when present, replaces the resolved base-value calculation
 *                          entirely and becomes the quote's unit price directly (Section 1c)
 *                          — no wiring to an actual {@code ShopEntry} override source exists
 *                          yet, this only carries the value once one does.
 */
public record PricingContext(
        ServerPlayer player,
        Optional<Identifier> merchantId,
        PricingDirection direction,
        Optional<Long> authoredOverride
) {
    public PricingContext {
        Objects.requireNonNull(player, "player must not be null");
        Objects.requireNonNull(merchantId, "merchantId must not be null — use Optional.empty()");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(authoredOverride, "authoredOverride must not be null — use Optional.empty()");
        if (authoredOverride.isPresent() && authoredOverride.get() < 0) {
            throw new IllegalArgumentException(
                    "authoredOverride must not be negative, was " + authoredOverride.get());
        }
    }

    public static PricingContext retail(ServerPlayer player) {
        return new PricingContext(player, Optional.empty(), PricingDirection.RETAIL, Optional.empty());
    }

    public static PricingContext sell(ServerPlayer player) {
        return new PricingContext(player, Optional.empty(), PricingDirection.SELL, Optional.empty());
    }
}
