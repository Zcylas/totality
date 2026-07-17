package zcylas.totality.api.rpg.resources.integration;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.ResourceAmount;

import java.util.Objects;

/**
 * How a resource's state starts when it is first instantiated for a player. Canonical design
 * §16.6, exact names/shape: {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md}.
 *
 * Placed under {@code .integration} rather than the top-level {@code api.rpg.resources} package
 * to match the canonical package layout (§3.1), which groups grant-related types —
 * {@code ResourceGrantProvider}, {@code ResourceGrant}, {@code ResourceGrantSourceType}, etc. —
 * under {@code api/rpg/resources/integration/}. This is the only file currently in that package;
 * its siblings ({@code ResourceGrant}, {@code ResourceGrantProvider}, ...) are not implemented by
 * this Phase 1 patch.
 *
 * This remains a declaration-only value hierarchy: no {@code ResourceGrantProvider}, no
 * {@code ResourceGrant}, and no acquisition logic that would actually consume one of these values
 * exists yet. Nothing in production code constructs one of these outside tests.
 */
public sealed interface ResourceGrantInitialization {

    record AtMinimum() implements ResourceGrantInitialization {}

    record AtMaximum() implements ResourceGrantInitialization {}

    /** Initializes at {@code numerator / denominator} of the resolved maximum. */
    record AtFraction(long numerator, long denominator) implements ResourceGrantInitialization {
        public AtFraction {
            if (denominator <= 0) {
                throw new IllegalArgumentException(
                        "AtFraction denominator must be > 0, was " + denominator);
            }
            if (numerator < 0) {
                throw new IllegalArgumentException(
                        "AtFraction numerator must be >= 0, was " + numerator);
            }
        }
    }

    /** Initializes at a server-authored absolute unit-space amount. */
    record AtAbsolute(ResourceAmount amount) implements ResourceGrantInitialization {
        public AtAbsolute {
            Objects.requireNonNull(amount, "amount");
        }
    }

    /** Reuses whatever state already exists rather than reinitializing it. */
    record PreserveExisting() implements ResourceGrantInitialization {}

    /** Defers to a registered, code-owned initialization strategy identified by {@code strategyId}. */
    record Custom(Identifier strategyId) implements ResourceGrantInitialization {
        public Custom {
            Objects.requireNonNull(strategyId, "strategyId");
        }
    }
}
