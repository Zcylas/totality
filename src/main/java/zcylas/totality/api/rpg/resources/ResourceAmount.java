package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Objects;
import java.util.OptionalInt;

/**
 * A concrete unit-space amount for one resource, optionally scoped to one partition of a
 * {@link ResourceModel#PARTITIONED_POOL} resource.
 *
 * Referenced by {@code zcylas.totality.api.rpg.resources.integration.ResourceGrantInitialization.AtAbsolute}
 * (canonical §16.6) and by {@link PlayerResourceService#restore}/{@link PlayerResourceService#drain}/
 * {@link PlayerResourceService#reconcileMaximum} and {@link ResourceOperation}'s
 * {@code Drain}/{@code Restore} transaction variants (canonical §12.1, §21.2). The canonical
 * document uses this type throughout but never gives its own field-level definition; this is the
 * minimal, immutable value shape needed to make {@code AtAbsolute} concrete, reconstructed from
 * its one shown usage example. No transaction, live-state validation, or mutation logic belongs
 * here — that lives in {@link PlayerResourceService} itself.
 *
 * <p>2026-09-15 pre-Phase-4 foundation pass: {@code trySpend}/{@code restore}/{@code drain}/
 * {@code set}/{@code transact} are now implemented on {@link PlayerResourceService} (for
 * {@code GENERIC_COMPONENT}-authority resources; see that class's own Javadoc for the current
 * {@code EXTERNAL_ADAPTER} mutation boundary) — this type is no longer merely anticipatory.</p>
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
