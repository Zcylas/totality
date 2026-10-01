package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.AbilityComponent;
import zcylas.totality.api.ability.AbilityComponents;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.entitlement.EntitlementKey;
import zcylas.totality.api.entitlement.EntitlementQueryContext;
import zcylas.totality.api.entitlement.EntitlementStateContributor;
import zcylas.totality.api.entitlement.requirement.EntitlementDependencyKey;

import java.util.Set;

/**
 * Exposes the Ability system's own selection state (equipped ability, selected spell) to the
 * {@code selected} axis of entitlement snapshots. Selection stays owned by {@code AbilityComponent}.
 */
public final class AbilitySelectionContributor implements EntitlementStateContributor {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "ability_selection");

    @Override
    public Identifier id() {
        return ID;
    }

    @Override
    public boolean appliesTo(Identifier typeId) {
        return typeId.equals(AbilityEntitlements.ABILITY_TYPE) || typeId.equals(AbilityEntitlements.SPELL_TYPE);
    }

    @Override
    public void contribute(EntitlementKey key, EntitlementQueryContext context, Collector collector) {
        ServerPlayer player = context.player();
        if (player == null) return;
        AbilityComponent abilities = AbilityComponents.ABILITIES.get((ComponentProvider) player);
        if (key.contentId().equals(abilities.getEquippedAbility()) || key.contentId().equals(abilities.getSelectedSpell())) {
            collector.selected();
        }
    }

    @Override
    public Set<EntitlementDependencyKey> dependencies() {
        return Set.of(AbilityEntitlements.SELECTION);
    }
}
