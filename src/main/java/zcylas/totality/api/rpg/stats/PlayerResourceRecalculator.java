package zcylas.totality.api.rpg.stats;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.combat.damage.DamageResistanceRecalculator;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.mana.SyncManaPayload;
import zcylas.totality.networking.stamina.StaminaServerTick;

/**
 * Central recalculator for all player resources.
 *
 * Call recalculate(player) whenever any stat changes:
 *   - Race/subrace selection
 *   - Class selection
 *   - Attribute point spending
 *   - Item equip/unequip
 *   - Quest rewards
 *   - Any other permanent stat change
 *
 * Recalculation order:
 *   1. Reapply stat-driven attribute modifiers (CON → MAX_HEALTH etc.)
 *   2. Clamp current HP to new max
 *   3. Clamp current stamina to new max
 *   4. Clamp current mana to new max
 *   5. Sync stats, stamina and mana to client
 */
public final class PlayerResourceRecalculator {

    private PlayerResourceRecalculator() {}

    /**
     * Recalculates all player resources based on current stats.
     * Safe to call multiple times — all operations are idempotent.
     */
    public static void recalculate(ServerPlayer player) {
        DamageResistanceRecalculator.recalculate(player);
        // 1. Reapply attribute modifiers (CON → MAX_HEALTH etc.)
        StatAttributeApplier.apply(player);

        // 2. Clamp current HP to new max
        float maxHp = player.getMaxHealth();
        if (player.getHealth() > maxHp) {
            player.setHealth(maxHp);
        }

        // 3. Clamp current stamina to new max
        int maxStamina = PlayerStaminaManager.getMaxStamina(player);
        int curStamina = PlayerStaminaManager.getStamina(player);
        if (curStamina > maxStamina) {
            PlayerStaminaManager.setStamina(player, maxStamina);
        }

        // 4. Clamp current mana to new max
        int maxMana = PlayerManaManager.getMaxMana(player);
        int curMana = PlayerManaManager.getMana(player);
        if (curMana > maxMana) {
            PlayerManaManager.setMana(player, maxMana);
        }

        // 5. Sync everything to client
        StatsComponents.get(player).sync();
        StaminaServerTick.syncStamina(player);
        ServerPlayNetworking.send(player, new SyncManaPayload(
                PlayerManaManager.getMana(player),
                PlayerManaManager.getMaxMana(player)));
    }

    /**
     * Full recalculate + restore resources to saved values.
     * Use on join/respawn where we want to restore rather than clamp.
     */
    public static void recalculateAndRestore(ServerPlayer player) {
        DamageResistanceRecalculator.recalculate(player);
        StatAttributeApplier.apply(player);

        // Restore HP from PlayerStatsComponent
        StatsComponents.get(player).restoreResources();

        // Clamp stamina and mana to new max in case stats changed
        int maxStamina = PlayerStaminaManager.getMaxStamina(player);
        int curStamina = PlayerStaminaManager.getStamina(player);
        if (curStamina > maxStamina) PlayerStaminaManager.setStamina(player, maxStamina);

        int maxMana = PlayerManaManager.getMaxMana(player);
        int curMana = PlayerManaManager.getMana(player);
        if (curMana > maxMana) PlayerManaManager.setMana(player, maxMana);

        // Sync everything to client
        StatsComponents.get(player).sync();
        StaminaServerTick.syncStamina(player);
        ServerPlayNetworking.send(player, new SyncManaPayload(
                PlayerManaManager.getMana(player),
                PlayerManaManager.getMaxMana(player)));
    }
}