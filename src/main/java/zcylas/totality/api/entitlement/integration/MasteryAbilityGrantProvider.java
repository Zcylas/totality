package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.entitlement.EntitlementGrantProvider;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;
import zcylas.totality.api.rpg.skills.core.Mastery;
import zcylas.totality.api.rpg.skills.core.MasteryRegistry;
import zcylas.totality.api.rpg.skills.core.PlayerMasteries;
import zcylas.totality.api.rpg.skills.core.Skill;

import java.util.Set;

/**
 * Grants a mastery's ability (e.g. Veinminer) while the player holds at least rank 1 of that mastery
 * (canonical §9.6). Mastery ranks stay in the Skills system; this only projects them.
 */
public final class MasteryAbilityGrantProvider implements EntitlementGrantProvider {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "mastery_grants");

    @Override
    public Identifier providerId() {
        return ID;
    }

    @Override
    public Set<Identifier> sourceTypeIds() {
        return Set.of(GrantSourceTypes.MASTERY);
    }

    @Override
    public void collectGrants(ServerPlayer player, Collector collector) {
        PlayerMasteries masteries = MasteriesComponents.get(player).getMasteries();
        for (Skill skill : Skill.values()) {
            for (Mastery mastery : MasteryRegistry.getMasteries(skill)) {
                if (mastery.getAbilityId() == null || masteries.getUnlockedRank(mastery.getId()) < 1) continue;
                Identifier abilityId = Identifier.tryParse(mastery.getAbilityId());
                Identifier masteryId = Identifier.tryBuild("totality", mastery.getId());
                if (abilityId == null || masteryId == null) continue;
                collector.grant(AbilityEntitlements.keyFor(abilityId), GrantSourceRef.of(GrantSourceTypes.MASTERY, masteryId));
            }
        }
    }
}
