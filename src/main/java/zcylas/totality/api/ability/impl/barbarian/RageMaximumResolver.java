package zcylas.totality.api.ability.impl.barbarian;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceMaximumResolver;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;

/**
 * Reproduces {@code totality:rage}'s exact legacy maximum formula by delegating to {@link
 * BarbarianRageAbility#getMaxRage(ServerPlayer)} — the same {@code RAGE_CHARGES[classLevel]} lookup
 * legacy {@code registerChargePool}/{@code updateChargePool} always used, now exposed through the
 * canonical §9.1 resolver contract. Deliberately a thin delegate rather than a reimplementation, the
 * same pattern {@code ManaMaximumResolver}/{@code StaminaMaximumResolver} established in Phase 4
 * (Phase 5 Rage migration, 2026-09-15).
 */
public final class RageMaximumResolver implements ResourceMaximumResolver {

    public static final RageMaximumResolver INSTANCE = new RageMaximumResolver();

    private RageMaximumResolver() {}

    @Override
    public ResourceMaximum resolve(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context) {
        return ResourceMaximum.Scalar.of(BarbarianRageAbility.getMaxRage(player));
    }
}
