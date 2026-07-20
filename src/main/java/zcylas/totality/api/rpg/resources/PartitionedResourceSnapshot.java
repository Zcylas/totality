package zcylas.totality.api.rpg.resources;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

/**
 * A read-only, point-in-time view of one {@link ResourceModel#PARTITIONED_POOL} resource's
 * authoritative mechanical current/maximum, broken down per integer partition (spell-slot tier,
 * Hit Die size, ...), expressed in the resource's own fixed-point units. The partitioned
 * counterpart to {@link ResourceSnapshot} for the Phase 2D partitioned external-query extension.
 * See {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §6.2, and the Phase 2D report's "Partitioned
 * query-result design" section for why this shape was chosen over two parallel current/maximum maps.
 *
 * Produced only by an {@link zcylas.totality.api.rpg.resources.external.ExternalPlayerResourceAdapter}
 * (wrapped in {@link ResourceQueryResult.PartitionedSuccess}) and validated by
 * {@link PlayerResourceService} before being trusted — never constructed as a side effect of a
 * failed lookup.
 *
 * <p>Deliberately generic: this type carries no knowledge of spell levels, Hit Die sizes, or any
 * other owner-specific partition meaning — the partition key is "an opaque, stable, ascending
 * integer identity" exactly as canonical §6.2 describes. A resource-specific adapter (e.g.
 * {@code StandardSpellSlotsResourceAdapter}) is responsible for choosing which partitions to
 * populate and what they mean; this class only guarantees the shape is well-formed.
 *
 * <p>Each partition entry pairs a complete current/maximum pair in one {@link ResourcePartitionSnapshot}
 * rather than two independently-keyed maps, so a partition can never have a current value with no
 * matching maximum (or vice versa) — "prefer one ordered map of complete per-partition entries
 * rather than two unrelated maps that could contain mismatched keys" (Phase 2D task). Overflow is
 * deliberately not represented here: no query-result consumer needs it yet (the spell-slot adapter
 * this type was introduced for has no use for it at all), unlike {@link
 * zcylas.totality.api.rpg.resources.state.PartitionedResourceState}, whose overflow field belongs to
 * the storage layer, not this query-snapshot layer. A future consumer that genuinely needs overflow
 * in a query result should extend this shape deliberately rather than have one invented speculatively
 * here.
 */
public record PartitionedResourceSnapshot(
        Identifier resourceId,
        NavigableMap<Integer, ResourcePartitionSnapshot> partitions,
        long unitScale
) {

    public PartitionedResourceSnapshot {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(partitions, "partitions");
        if (unitScale < 1) {
            throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
        }
        // Defensive immutable copy into a fresh TreeMap: enforces deterministic ascending integer
        // order regardless of the caller's own map implementation/iteration order (never relies on
        // LinkedHashMap insertion order alone), and makes the partitions map itself impossible to
        // mutate after construction — a caller holding the original mutable map used to build this
        // snapshot cannot retroactively change what was already validated and returned. Null
        // rejection for both the key and the value is performed explicitly below via
        // Objects.requireNonNull, not by the TreeMap#put call itself: a natural-ordering TreeMap
        // does independently reject a null key (comparing it against existing keys throws
        // NullPointerException), but it does not reject a null value — TreeMap, like every
        // java.util.Map implementation, permits null values freely, since values are never compared
        // for ordering. Relying on that would leave null-value rejection as an accident of this
        // particular copy strategy rather than a guarantee of this type; the explicit checks make it
        // one regardless of how the copy is implemented.

        NavigableMap<Integer, ResourcePartitionSnapshot> copy = new TreeMap<>();
        for (Map.Entry<Integer, ResourcePartitionSnapshot> entry : partitions.entrySet()) {
            Integer partition = entry.getKey();
            ResourcePartitionSnapshot value = entry.getValue();
            Objects.requireNonNull(partition, "partition key must not be null");
            Objects.requireNonNull(value, () -> "partition value must not be null for partition " + partition);
            copy.put(partition, value);
        }
        partitions = Collections.unmodifiableNavigableMap(copy);
    }

    /** The real mechanical current value for {@code partition}, or empty if it is not present. */
    public java.util.Optional<ResourcePartitionSnapshot> partition(int partition) {
        return java.util.Optional.ofNullable(partitions.get(partition));
    }

    /**
     * One partition's complete current/maximum pair, in the owning resource's fixed-point units.
     * Immutable by construction (a record of two {@code long}s). Deliberately declared as a static
     * nested type of {@link PartitionedResourceSnapshot} rather than its own top-level file — it has
     * no meaning independent of the snapshot that contains it, exactly like a map entry.
     */
    public record ResourcePartitionSnapshot(long currentUnits, long maximumUnits) {}
}
