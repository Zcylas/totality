package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.init.ModTags;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PowerAttackManager {

    private static final Set<UUID> pendingPowerAttacks = new HashSet<>();
    private static final Set<UUID> activePowerAttacks  = new HashSet<>();

    private static final int BASE_COST_ONE_HANDED  = 30;
    private static final int BASE_COST_TWO_HANDED   = 45;
    private static final float DAMAGE_MULTIPLIER    = 1.5f;

    /** Same melee reach {@link zcylas.totality.networking.combat.OffhandAttackHandler} already
     *  uses for its own (offhand) power attack — Power Attack is the mainhand analogue of that
     *  exact mechanic and must share the identical reach, not a second invented value. */
    public static final double MELEE_TARGET_RANGE = 4.5;
    private static final double MELEE_TARGET_RANGE_SQ = MELEE_TARGET_RANGE * MELEE_TARGET_RANGE;

    /**
     * True if {@code target} is a legal Power Attack target for {@code player} right now: a
     * living, alive, attackable entity other than the player themself, within melee reach
     * (correction pass, Part C). Shared by both the client (gates whether a hold-charge may even
     * begin/continue — {@code MinecraftAttackMixin}) and the server (re-validates before ever
     * consuming Stamina or marking advantage — never trusts the client's own gating alone).
     * Existing PvP/team/invulnerability rules are enforced downstream, unchanged, by vanilla's own
     * attack/damage resolution — this only gates whether a charge may start/continue at all, it
     * does not re-implement or bypass those rules. Takes the common {@link Player} supertype so
     * the identical check runs for both a {@code LocalPlayer} (client) and a {@link ServerPlayer}
     * (server) — one source of truth, not two copies that could drift apart.
     */
    public static boolean isValidTarget(Player player, @Nullable Entity target) {
        if (!(target instanceof LivingEntity living)) return false;
        if (living == player) return false;
        if (!living.isAlive() || !living.isAttackable()) return false;
        return player.distanceToSqr(living) <= MELEE_TARGET_RANGE_SQ;
    }

    // Mastery IDs — kept here so a rename is one-line, not a grep
    private static final String MASTERY_DISCIPLINED_FIGHTER = "disciplined_fighter";
    private static final String MASTERY_FIGHTERS_STANCE     = "fighters_stance";
    private static final String MASTERY_FURIOUS_STRENGTH    = "furious_strength";

    public static void onPowerAttackReceived(ServerPlayer player) {
        if (PlayerStaminaManager.getStamina(player) <= 0) return;
        int cost = getStaminaCost(player);

        int disciplinedRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                .get(player).getMasteries().getUnlockedRank(MASTERY_DISCIPLINED_FIGHTER);
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
                .get(player).getMasteries().getUnlockedRank(MASTERY_FIGHTERS_STANCE);
        if (stanceRank == 1) multiplier += 0.25f;
        else if (stanceRank >= 2) multiplier += 0.50f;

        int furiousRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                .get(player).getMasteries().getUnlockedRank(MASTERY_FURIOUS_STRENGTH);
        if (furiousRank > 0)
            multiplier += PlayerStaminaManager.getStamina(player) * 0.001f;

        return multiplier;
    }

    /** Stamina cost for the offhand's own independent power attack (RMB hold-to-charge). */
    public static int getOffhandStaminaCost(ServerPlayer player) {
        int cost = BASE_COST_ONE_HANDED;
        int disciplinedRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                .get(player).getMasteries().getUnlockedRank(MASTERY_DISCIPLINED_FIGHTER);
        if (disciplinedRank > 0) cost = (int)(cost * 0.75f);
        return cost;
    }

    private static int getStaminaCost(ServerPlayer player) {
        ItemStack held = player.getMainHandItem();
        int cost = held.is(ModTags.TWO_HANDED_WEAPONS) ? BASE_COST_TWO_HANDED : BASE_COST_ONE_HANDED;
        // Dual-wield power attack strikes with both weapons at once — double the power attack
        // cost (not the normal per-swing stamina, which is charged separately per hand).
        if (isDualWielding(player)) cost *= 2;
        return cost;
    }

    private static boolean isDualWielding(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();
        boolean mainIsMelee = main.is(ItemTags.SWORDS) || main.getItem() instanceof TotalityMeleeWeaponItem;
        boolean offIsMelee = off.is(ItemTags.SWORDS) || off.getItem() instanceof TotalityMeleeWeaponItem;
        return mainIsMelee && offIsMelee;
    }

    public static void onPlayerLeave(ServerPlayer player) {
        pendingPowerAttacks.remove(player.getUUID());
        activePowerAttacks.remove(player.getUUID());
    }

    private PowerAttackManager() {}
}