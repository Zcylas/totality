package zcylas.totality.api.entitlement;

import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * The derived, multi-axis state of one entitlement for one player (canonical §6.2). Never stored.
 *
 * @param temporarilyAccessible available only through source-bound / leased / session paths
 * @param debugOnly             available only through debug paths
 * @param sources               provenance of every fact and grant currently held for the key
 */
public record EntitlementSnapshot(
        EntitlementKey key,
        Optional<Identifier> discoveryLevelId,
        boolean visible,
        boolean known,
        boolean owned,
        boolean permanentlyUnlocked,
        boolean activelyGranted,
        boolean selected,
        boolean available,
        boolean temporarilyAccessible,
        boolean suspended,
        boolean debugOnly,
        List<SourceSummary> sources
) {

    public EntitlementSnapshot {
        sources = List.copyOf(sources);
    }

    public static EntitlementSnapshot empty(EntitlementKey key) {
        return new EntitlementSnapshot(key, Optional.empty(), false, false, false, false, false, false, false,
                false, false, false, List.of());
    }

    /** Display-safe provenance line (canonical §4.5): "Granted by Origin: Kryptonian". */
    public record SourceSummary(GrantSourceRef source, boolean temporary, boolean progressionEligible) {}
}
