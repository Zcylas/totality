package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.entitlement.EntitlementGrantProvider;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.OriginData;
import zcylas.totality.api.rpg.ancestry.OriginRegistry;
import zcylas.totality.api.rpg.ancestry.PlayerAncestryComponent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects Species- and Origin-derived grants from {@code PlayerAncestryComponent} (canonical §9.3), each with
 * its own independently identifiable provenance. Changing ancestry removes every grant of the old source;
 * a key the new ancestry also grants continues through the new source.
 *
 * <p>Compatibility adapter: today's {@code OriginData} entries are species variants (e.g. Pureblood
 * Viltrumite) whose {@code startingAbilities} carry the innate abilities, so those are attributed to the
 * {@code totality:origin} source. Species-level grants can be registered with {@link #registerSpeciesGrant};
 * none exist in current content. This adapter does not settle the future Species/Origin split.
 */
public final class AncestryEntitlementGrantProvider implements EntitlementGrantProvider {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "ancestry_grants");

    private static final Map<Identifier, List<EntitlementKey>> SPECIES_GRANTS = new LinkedHashMap<>();

    public static void registerSpeciesGrant(Identifier speciesId, EntitlementKey key) {
        SPECIES_GRANTS.computeIfAbsent(speciesId, id -> new ArrayList<>()).add(key);
    }

    @Override
    public Identifier providerId() {
        return ID;
    }

    @Override
    public Set<Identifier> sourceTypeIds() {
        return Set.of(GrantSourceTypes.SPECIES, GrantSourceTypes.ORIGIN);
    }

    @Override
    public void collectGrants(ServerPlayer player, Collector collector) {
        PlayerAncestryComponent ancestry = AncestryComponents.get(player);
        collect(ancestry.getSpeciesId(), ancestry.getOriginId(), collector);
    }

    static void collect(@Nullable Identifier speciesId, @Nullable Identifier originId, Collector collector) {
        if (speciesId != null) {
            for (EntitlementKey key : SPECIES_GRANTS.getOrDefault(speciesId, List.of())) {
                collector.grant(key, GrantSourceRef.of(GrantSourceTypes.SPECIES, speciesId));
            }
        }
        OriginData origin = originId != null ? OriginRegistry.get(originId) : null;
        if (origin != null) {
            for (Identifier abilityId : origin.getStartingAbilities()) {
                collector.grant(AbilityEntitlements.keyFor(abilityId), GrantSourceRef.of(GrantSourceTypes.ORIGIN, originId));
            }
        }
    }
}
