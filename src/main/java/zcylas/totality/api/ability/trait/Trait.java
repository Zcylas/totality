package zcylas.totality.api.ability.trait;

import net.minecraft.server.level.ServerPlayer;

/**
 * A reusable passive effect that can be applied to and removed from a player.
 *
 * Traits are the building blocks of PhysiologyPassive abilities.
 * They are checked and reapplied every passive tick, so they
 * self-heal if lost due to respawn, dimension change, etc.
 *
 * Example usage:
 *   Traits.knockbackResistance(1.0)
 *   Traits.attackDamage(4.0)
 *   Traits.noFallDamage()
 */
public interface Trait {

    /**
     * Called every passive tick.
     * Should check if the effect is already applied before reapplying.
     */
    void apply(ServerPlayer player);

    /**
     * Called when the passive ability is removed from the player.
     * Should clean up all modifiers/effects this trait applied.
     */
    void remove(ServerPlayer player);
}