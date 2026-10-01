package zcylas.totality.api.entitlement.integration;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityRegistry;
import zcylas.totality.api.entitlement.EntitlementGrantProvider;
import zcylas.totality.api.entitlement.GrantSourceRef;
import zcylas.totality.api.entitlement.GrantSourceTypes;
import zcylas.totality.api.magic.spell.Spell;

import java.util.Set;

/**
 * Grants the non-spell abilities every character has (currently Harvest, Rest and Ground Slam — the
 * abilities whose {@code isDefault()} is true) from the character baseline. Source-bound and re-derived
 * on every reconciliation, so it is never stored as progression.
 *
 * <p>Spells are deliberately excluded: their {@code isDefault()} is development scaffolding and is served
 * only by {@link DebugSpellAccessProvider} when explicitly enabled.
 */
public final class BaselineAbilityGrantProvider implements EntitlementGrantProvider {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("totality", "baseline_abilities");
    public static final GrantSourceRef SOURCE = GrantSourceRef.of(GrantSourceTypes.BASELINE,
            Identifier.fromNamespaceAndPath("totality", "character_baseline"));

    @Override
    public Identifier providerId() {
        return ID;
    }

    @Override
    public Set<Identifier> sourceTypeIds() {
        return Set.of(GrantSourceTypes.BASELINE);
    }

    @Override
    public void collectGrants(ServerPlayer player, Collector collector) {
        for (Ability ability : AbilityRegistry.all()) {
            if (!(ability instanceof Spell) && ability.isDefault()) {
                collector.grant(AbilityEntitlements.keyFor(ability), SOURCE);
            }
        }
    }
}
