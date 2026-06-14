package zcylas.totality.api.rpg.stats;

import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.ClassLevelUpRegistry;

/**
 * Registers server-side events for the RPG stats system.
 *
 * On join:
 *   1. Recalculate all stat-driven modifiers (CON → HP etc.)
 *   2. Restore saved stamina, mana and HP values
 *   3. Sync everything to client
 *
 * On disconnect:
 *   1. Save current stamina, mana and HP into PlayerStatsComponent
 *
 * On respawn:
 *   1. Recalculate all stat-driven modifiers
 *   2. Restore to full resources
 *   3. Sync everything to client
 */
public final class StatsServerEvents {

    private StatsServerEvents() {}

    public static void register() {

        // ── On join ───────────────────────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerResourceRecalculator.recalculateAndRestore(handler.player);

            // Re-fire the class level-up handler so class features (charge pools,
            // etc.) are initialized correctly on join, not only on first level-up.
            net.minecraft.resources.Identifier primaryClass =
                    ClassComponents.get(handler.player).getPrimaryClassId();
            if (primaryClass != null) {
                int playerLevel = StatsComponents.get(handler.player).getStats().getLevel();
                ClassLevelUpRegistry.fire(handler.player, primaryClass, playerLevel);
            }
        });

        // ── On disconnect ─────────────────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            StatsComponents.get(handler.player).saveCurrentResources();
        });

        // ── On respawn / death copy ───────────────────────────────────────────
        ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
            PlayerResourceRecalculator.recalculateAndRestore(newPlayer);
            if (!alive) {
                // Player died — force-stop any active toggle abilities so their
                // modifiers (damage bonus, resistances, etc.) don't persist.
                zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility.forceStop(newPlayer);
            }
        });
    }
}