package zcylas.totality.api.rpg.resources.client;

import net.minecraft.resources.Identifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The one public result vocabulary for the Phase 3B client Resource query façade. A distinct type
 * from the server's {@code ResourceQueryResult}/{@code ResourceQueryFailureReason} — see the Phase 3B
 * readiness audit §6.1 for why. Never constructed as a fabricated zero for a missing or
 * unsynchronized Resource: every branch is either a validated numeric success or a named
 * {@link Unavailable} reason.
 *
 * <p>Deliberately does not depend on any Phase 3A wire record ({@code ResourceScalarWireSnapshot},
 * {@code ResourcePartitionedWireSnapshot}, ...) — conversion from those happens in the reader layer
 * (see {@code GenericSyncClientResourceReader}), so a future wire-format change cannot silently
 * become a public API change here.
 */
public sealed interface ClientResourceQueryResult {

    Identifier resourceId();

    /** A single bounded current/maximum/overflow value (Health, Food, Breath, Mana, Stamina, Rage). */
    record Scalar(
            Identifier resourceId,
            long currentUnits,
            long maximumUnits,
            long overflowUnits,
            long unitScale,
            ClientResourceSource source,
            ClientResourceTrust trust
    ) implements ClientResourceQueryResult {
        public Scalar {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(trust, "trust");
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
    record Partitioned(
            Identifier resourceId,
            NavigableMap<Integer, Partition> partitions,
            long unitScale,
            ClientResourceSource source,
            ClientResourceTrust trust
    ) implements ClientResourceQueryResult {

        public Partitioned {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(partitions, "partitions");
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(trust, "trust");
            if (unitScale < 1) {
                throw new IllegalArgumentException("unitScale must be >= 1, was " + unitScale);
            }
            // Defensive rebuild into an unmodifiable, naturally-ordered TreeMap — deterministic
            // ascending order regardless of the caller's own map/iteration order, and the resulting
            // map can never be mutated after construction. Duplicate partition ids are structurally
            // impossible once already in a Map; see #of(...) for the List-based factory that rejects
            // duplicates before a Map is ever built.
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

        /**
         * Builds a {@link Partitioned} from an ordered-or-not list of partitions, rejecting a
         * duplicate partition id before any map is constructed — the list-based counterpart to how
         * {@code ResourcePartitionedWireSnapshot} validates its own wire-shaped input.
         */
        public static Partitioned of(
                Identifier resourceId,
                List<Partition> partitions,
                long unitScale,
                ClientResourceSource source,
                ClientResourceTrust trust
        ) {
            Objects.requireNonNull(partitions, "partitions");
            NavigableMap<Integer, Partition> map = new TreeMap<>();
            for (Partition partition : partitions) {
                Objects.requireNonNull(partition, "partition entry must not be null");
                if (map.put(partition.partitionId(), partition) != null) {
                    throw new IllegalArgumentException(
                            "duplicate partition id " + partition.partitionId() + " for resource " + resourceId);
                }
            }
            return new Partitioned(resourceId, map, unitScale, source, trust);
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

    /** No numeric value could be produced. Never paired with a fabricated current/maximum. */
    record Unavailable(Identifier resourceId, ClientResourceUnavailableReason reason) implements ClientResourceQueryResult {
        public Unavailable {
            Objects.requireNonNull(resourceId, "resourceId");
            Objects.requireNonNull(reason, "reason");
        }
    }

    static Unavailable unavailable(Identifier resourceId, ClientResourceUnavailableReason reason) {
        return new Unavailable(resourceId, reason);
    }
}
