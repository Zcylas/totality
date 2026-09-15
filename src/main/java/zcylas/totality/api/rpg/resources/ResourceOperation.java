package zcylas.totality.api.rpg.resources;

import java.util.Objects;

/**
 * One line-item inside a {@link ResourceTransaction} — canonical
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §13.2 names {@code List<ResourceOperation>} as
 * the transaction's shape but never gives {@code ResourceOperation} itself a literal definition.
 * Reconstructed as a sealed union of the four single-operation mutation shapes
 * {@link PlayerResourceService} already exposes individually ({@code trySpend}/{@code drain}/
 * {@code restore}/{@code set}, taking {@link ResourceCost}/{@link ResourceAmount}/
 * {@link ResourceTarget} respectively) — a mechanical packaging of already-fully-specified
 * single-operation signatures into one list-friendly type, not a new operation semantic.
 */
public sealed interface ResourceOperation {

    record Spend(ResourceCost cost) implements ResourceOperation {
        public Spend { Objects.requireNonNull(cost, "cost"); }
    }

    record Drain(ResourceAmount amount) implements ResourceOperation {
        public Drain { Objects.requireNonNull(amount, "amount"); }
    }

    record Restore(ResourceAmount amount) implements ResourceOperation {
        public Restore { Objects.requireNonNull(amount, "amount"); }
    }

    record Set(ResourceTarget target) implements ResourceOperation {
        public Set { Objects.requireNonNull(target, "target"); }
    }
}
