package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.init.ModTags;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PowerAttackManager {

    private static final Set<UUID> pendingPowerAttacks = new HashSet<>();
    private static final Set<UUID> activePowerAttacks  = new HashSet<>();

    private static final int BASE_COST_ONE_HANDED = 30;
    private static final int BASE_COST_TWO_HANDED  = 45;
    private static final float DAMAGE_MULTIPLIER   = 1.5f;

    public static void onPowerAttackReceived(ServerPlayer player) {
        if (PlayerStaminaManager.getStamina(player) <= 0) return;
        int cost = getStaminaCost(player);

        int disciplinedRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                .get(player).getMasteries().getUnlockedRank("disciplined_fighter");
        if (disciplinedRank > 0) cost = (int)(cost * 0.75f);

        if (!PlayerStaminaManager.hasStamina(player, cost)) return;
        PlayerStaminaManager.removeStamina(player, cost);
        pendingPowerAttacks.add(player.getUUID());
    }

    /** Consumes the pending power attack (called in CombatServerEvents). */
    public static boolean consumePowerAttack(ServerPlayer player) {
        return pendingPowerAttacks.remove(player.getUUID());
    }

    /** Marks that this attack should use advantage (called after consumePowerAttack). */
    public static void markPowerAttack(UUID uuid) {
        activePowerAttacks.add(uuid);
    }

    /** Clears the advantage flag and returns whether it was set. */
    public static boolean clearPowerAttack(UUID uuid) {
        return activePowerAttacks.remove(uuid);
    }

    public static float getDamageMultiplier(ServerPlayer player) {
        float multiplier = DAMAGE_MULTIPLIER;
        int stanceRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                .get(player).getMasteries().getUnlockedRank("fighters_stance");
        if (stanceRank == 1) multiplier += 0.25f;
        else if (stanceRank >= 2) multiplier += 0.50f;

        int furiousRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                .get(player).getMasteries().getUnlockedRank("furious_strength");
        if (furiousRank > 0)
            multiplier += PlayerStaminaManager.getStamina(player) * 0.001f;

        return multiplier;
    }

    private static int getStaminaCost(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        if (held.is(ModTags.TWO_HANDED_WEAPONS)) return BASE_COST_TWO_HANDED;
        return BASE_COST_ONE_HANDED;
    }

    public static void onPlayerLeave(ServerPlayer player) {
        pendingPowerAttacks.remove(player.getUUID());
        activePowerAttacks.remove(player.getUUID());
    }

    private PowerAttackManager() {}
}