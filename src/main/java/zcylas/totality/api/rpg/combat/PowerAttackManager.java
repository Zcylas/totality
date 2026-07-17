package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
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

    /**
     * True if {@code attacker} is currently legally permitted to attack {@code target} at all —
     * a SEPARATE concern from {@link #isValidTarget} (which only checks whether the target is a
     * combat-legal thing to aim a Power Attack at in the first place). Checked BEFORE Stamina is
     * committed for a Power Attack (correction pass, Part D): PvP-disabled worlds, team
     * friendly-fire settings, and general invulnerability must gate Stamina spend itself, not be
     * deferred until vanilla's own damage resolution silently no-ops the hit afterward.
     *
     * <p>Mirrors the exact rules vanilla's own player-attack resolution already applies for a
     * player target ({@link GameRules#PVP}, {@link Player#canHarmPlayer}) plus general
     * {@link Entity#isInvulnerable()} — no blanket restriction against neutral/allied non-player
     * mobs is added here; ordinary attackable mobs are unaffected. Takes the common {@link Player}
     * supertype so the identical check runs for both a {@code LocalPlayer} (client — used only to
     * avoid showing accepted flash/sound feedback for an attack the server would reject; the
     * client's own copy is optimistic-only and never authoritative) and a {@link ServerPlayer}
     * (server — the actual gate before {@link #onPowerAttackReceived} runs).
     *
     * <p>The {@link GameRules#PVP} check itself only runs when {@code attacker.level()} is a
     * {@link ServerLevel} — {@code getGameRules()} exists only there in this Minecraft version
     * (neither the generic {@code Level} nor {@code ClientLevel} expose it, confirmed by
     * inspection; game rule VALUES are synced to the client via a dedicated packet, but not
     * through {@code Level} itself). The server-side call (the actual authoritative gate before
     * Stamina is spent) is always a {@link ServerPlayer} and therefore always exercises this
     * check in full. The client-side call is optimistic-only anyway (see above) — no speculative
     * networking was added just to make the PVP-rule half of this cosmetic pre-check visible to
     * the client too; team friendly-fire and invulnerability are still checked there.
     */
    public static boolean isAttackerLegal(Player attacker, LivingEntity target) {
        if (target.isInvulnerable()) return false;
        if (target instanceof Player targetPlayer) {
            if (attacker.level() instanceof ServerLevel serverLevel && !serverLevel.getGameRules().get(GameRules.PVP)) {
                return false;
            }
            if (!attacker.canHarmPlayer(targetPlayer)) return false;
        }
        return true;
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