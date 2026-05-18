package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;

/**
 * Static API for querying and updating combat state.
 *
 * Usage:
 *   CombatStateManager.isInCombat(player)   — check in stamina regen, masteries, etc.
 *   CombatStateManager.onDamage(player)     — call from damage events
 *   CombatStateManager.tick(player)         — call every server tick
 *
 * Future mastery hook example:
 *   if (!CombatStateManager.isInCombat(player)) regenAmount *= 2; // Double regen mastery
 */
public final class CombatStateManager {

    public static boolean isInCombat(ServerPlayer player) {
        return CombatComponents.COMBAT_STATE
                .maybeGet((ComponentProvider) player)
                .map(CombatStateComponent::isInCombat)
                .orElse(false);
    }

    /** Call when the player deals or receives damage. */
    public static void onDamage(ServerPlayer player) {
        CombatComponents.COMBAT_STATE
                .maybeGet((ComponentProvider) player)
                .ifPresent(CombatStateComponent::reset);
    }

    /** Call every server tick to count the timer down. */
    public static void tick(ServerPlayer player) {
        CombatComponents.COMBAT_STATE
                .maybeGet((ComponentProvider) player)
                .ifPresent(CombatStateComponent::tick);
    }

    private CombatStateManager() {}
}