package zcylas.totality.api.rpg.resources.client.parity;

import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * A pure, side-agnostic immutable value representation used by the Phase 3B-2 shadow-parity engine
 * for <b>either</b> the generic or the legacy side of a comparison. Deliberately a dedicated type,
 * not a reuse of {@code ClientResourceQueryResult} (the Phase 3B-1 façade's own result type):
 * the legacy client managers ({@code ClientManaManager}, {@code ClientStaminaManager},
 * {@code ClientSpellSlotManager}, {@code PlayerChargesComponent}) have no façade-shaped result type
 * of their own at all, so a single shared shape is needed to describe both sides uniformly without
 * pretending a legacy manager read is a real façade query.
 *
 * <p>Never retains a packet payload, a mutable legacy array/map, a live component reference, or a
 * client player reference — every numeric quantity is copied into a plain, validated, immutable
 * value at construction time.
 */
public sealed interface ClientResourceParitySummary {

    /** A single bounded current/maximum/overflow value (Mana, Stamina, a present Rage value). */
    record Scalar(long currentUnits, long maximumUnits, long overflowUnits, long unitScale)
            implements ClientResourceParitySummary {
        public Scalar {
            if (unitScale < 1) {
                throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
            }
            if (currentUnits < 0 || maximumUnits < 0 || overflowUnits < 0) {
                throw new IllegalArgumentException("currentUnits/maximumUnits/overflowUnits must be >= 0");
            }
            if (currentUnits > Math.addExact(maximumUnits, overflowUnits)) {
                throw new IllegalArgumentException("currentUnits must not exceed maximumUnits + overflowUnits");
            }
        }
    }

    /** Independent current/maximum/overflow triples keyed by integer partition (standard spell slots). */
    record Partitioned(NavigableMap<Integer, Partition> partitions, long unitScale)
            implements ClientResourceParitySummary {

        public Partitioned {
            Objects.requireNonNull(partitions, "partitions");
            if (unitScale < 1) {
                throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
            }
            // Defensive rebuild into an unmodifiable, naturally-ordered TreeMap — deterministic
            // ascending order regardless of the caller's own map/iteration order, and the resulting
            // map can never be mutated after construction.
            NavigableMap<Integer, Partition> ordered = new TreeMap<>();
            for (Map.Entry<Integer, Partition> entry : partitions.entrySet()) {
                Integer key = entry.getKey();
                Partition value = entry.getValue();
                Objects.requireNonNull(key, "partition key must not be null");
                Objects.requireNonNull(value, () -> "partition value must not be null for partition " + key);
                if (value.partitionId() != key) {
                    throw new IllegalArgumentException(
                            "partition key " + key + " does not match Partition.partitionId() " + value.partitionId());
                }
                ordered.put(key, value);
            }
            partitions = Collections.unmodifiableNavigableMap(ordered);
        }

        /** Builds a {@link Partitioned} from a list of partitions, rejecting a duplicate partition
         *  id before any map is constructed. */
        public static Partitioned of(List<Partition> partitions, long unitScale) {
            Objects.requireNonNull(partitions, "partitions");
            NavigableMap<Integer, Partition> map = new TreeMap<>();
            for (Partition partition : partitions) {
                Objects.requireNonNull(partition, "partition entry must not be null");
                if (map.put(partition.partitionId(), partition) != null) {
                    throw new IllegalArgumentException("duplicate partition id " + partition.partitionId());
                }
            }
            return new Partitioned(map, unitScale);
        }

        public Optional<Partition> partition(int partitionId) {
            return Optional.ofNullable(partitions.get(partitionId));
        }

        /** One partition's complete current/maximum/overflow triple. Immutable by construction. */
        public record Partition(int partitionId, long currentUnits, long maximumUnits, long overflowUnits) {
            public Partition {
                if (currentUnits < 0 || maximumUnits < 0 || overflowUnits < 0) {
                    throw new IllegalArgumentException("currentUnits/maximumUnits/overflowUnits must be >= 0");
                }
                if (currentUnits > Math.addExact(maximumUnits, overflowUnits)) {
                    throw new IllegalArgumentException("currentUnits must not exceed maximumUnits + overflowUnits");
                }
            }
        }
    }

    /** No numeric value could be produced on this side. */
    record Unavailable(ClientResourceUnavailableReason reason) implements ClientResourceParitySummary {
        public Unavailable {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
