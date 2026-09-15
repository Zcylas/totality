package zcylas.totality.api.rpg.stamina;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.resources.PlayerResourceDefinition;
import zcylas.totality.api.rpg.resources.ResourceMaximum;
import zcylas.totality.api.rpg.resources.ResourceMaximumResolver;
import zcylas.totality.api.rpg.resources.ResourceResolutionContext;

/**
 * Reproduces {@code totality:stamina}'s exact legacy maximum formula by delegating to {@link
 * PlayerStaminaManager#getMaxStamina(net.minecraft.world.entity.player.Player)} — the same base/
 * stat/armor/held-item/effect/event-hook computation Stamina has always used, now exposed through
 * the canonical §9.1 resolver contract. Deliberately a thin delegate rather than a reimplementation:
 * the formula lives in exactly one place, so the two can never drift apart (Phase 4 Mana/Stamina
 * migration, 2026-09-15).
 */
public final class StaminaMaximumResolver implements ResourceMaximumResolver {

    public static final StaminaMaximumResolver INSTANCE = new StaminaMaximumResolver();

    private StaminaMaximumResolver() {}

    @Override
    public ResourceMaximum resolve(ServerPlayer player, PlayerResourceDefinition definition, ResourceResolutionContext context) {
        return ResourceMaximum.Scalar.of(PlayerStaminaManager.getMaxStamina(player));
    }
}
