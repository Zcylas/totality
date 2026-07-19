package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A concrete unit-space amount for one resource, optionally scoped to one partition of a
 * {@link ResourceModel#PARTITIONED_POOL} resource.
 *
 * Referenced by {@code zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization.AtAbsolute}
 * (canonical §16.6) and, in the canonical design, {@link PlayerResourceService}'s future
 * restore/drain/transaction operations (canonical §12.1, §21.2 — e.g.
 * {@code ResourceAmount.scalar(STAMINA_ID, amount)}). The canonical document uses this type
 * throughout but never gives its own field-level definition; this is the minimal, immutable
 * value shape needed to make {@code AtAbsolute} concrete, reconstructed from its one shown usage
 * example. No transaction, live-state validation, or mutation logic belongs here.
 * {@link PlayerResourceService} exists as of Phase 2A but implements only the query/snapshot
 * path — restore/drain/spend/set/transactions (the operations this type is ultimately for) remain
 * unimplemented, deferred to a later phase.
 */
public record ResourceAmount(Identifier resourceId, long units, OptionalInt partition) {

    public ResourceAmount {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(partition, "partition");
    }

    public static ResourceAmount scalar(Identifier resourceId, long units) {
        return new ResourceAmount(resourceId, units, OptionalInt.empty());
    }

    public static ResourceAmount partitioned(Identifier resourceId, int partition, long units) {
        return new ResourceAmount(resourceId, units, OptionalInt.of(partition));
    }
}
