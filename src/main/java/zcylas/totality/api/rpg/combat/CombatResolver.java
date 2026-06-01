package zcylas.totality.api.rpg.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.impl.barbarian.BarbarianRageAbility;
import zcylas.totality.api.combat.damage.DamageFlags;
import zcylas.totality.api.combat.damage.TotalityDamage;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;
import zcylas.totality.networking.combat.DamageRollNotification;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.List;

/**
 * Resolves weapon and spell attacks server-side — no dice screen.
 * The dice screen (PendingDiceRollManager) is for dialogue, quests,
 * and narrative events only. Combat resolves instantly.
 *
 * Works for both players and mobs as attacker/caster.
 */
public final class CombatResolver {

    private CombatResolver() {}

    public enum SpellAttackType { MELEE, RANGED }

    // ── Weapon Attacks ────────────────────────────────────────────────────────

    /**
     * Resolves a weapon attack with an explicit damage modifier.
     */
    // resolveAttack signature — add type
    public static void resolveAttack(LivingEntity attacker,
                                     LivingEntity target,
                                     AbilityScore abilityScore,
                                     boolean proficient,
                                     RollType rollType,
                                     int diceCount,
                                     Dice damageDie,
                                     int damageModifier,
                                     TotalityDamageType damageType) {

        RollOutcome outcome = AttackRoll.roll(attacker, target, abilityScore, proficient, rollType);
        handleHit(attacker, target, outcome, resolveWeaponName(attacker), diceCount, damageDie, damageModifier, damageType, false, abilityScore);
    }

    // convenience overload
    public static void resolveAttack(LivingEntity attacker,
                                     LivingEntity target,
                                     AbilityScore abilityScore,
                                     boolean proficient,
                                     RollType rollType,
                                     int diceCount,
                                     Dice damageDie,
                                     TotalityDamageType damageType) {
        int mod = resolveAbilityMod(attacker, abilityScore);
        resolveAttack(attacker, target, abilityScore, proficient, rollType, diceCount, damageDie, mod, damageType);
    }
    public static void resolveAttack(LivingEntity attacker, LivingEntity target,
                                     AbilityScore abilityScore, boolean proficient,
                                     RollType rollType, int diceCount, Dice damageDie,
                                     int damageModifier, TotalityDamageType damageType,
                                     String weaponName) {
        RollOutcome outcome = AttackRoll.roll(attacker, target, abilityScore, proficient, rollType);
        handleHit(attacker, target, outcome, weaponName, diceCount, damageDie, damageModifier, damageType, false, abilityScore);
    }

    // resolveSpellAttack — spell damage is always magical
    public static void resolveSpellAttack(LivingEntity caster,
                                          LivingEntity target,
                                          String spellName,
                                          AbilityScore spellcastingAbility,
                                          SpellAttackType attackType,
                                          RollType rollType,
                                          int diceCount,
                                          Dice damageDie,
                                          TotalityDamageType damageType) {

        if (attackType == SpellAttackType.MELEE && caster.distanceTo(target) > MELEE_RANGE) {
            if (caster instanceof ServerPlayer p)
                SendNotificationPayload.send(p, spellName + " — Target out of reach!", SendNotificationPayload.GRAY);
            return;
        }

        RollType effectiveRollType = rollType;
        if (attackType == SpellAttackType.RANGED && isHostileInMeleeRange(caster)) {
            effectiveRollType = RollType.DISADVANTAGE;
        }

        RollOutcome outcome = AttackRoll.roll(caster, target, spellcastingAbility, true, effectiveRollType);
        handleHit(caster, target, outcome, spellName, diceCount, damageDie, 0, damageType, true, null);
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    /**
     * Shared hit/miss resolution for both weapon and spell attacks.
     */
    private static void handleHit(LivingEntity attacker,
                                  LivingEntity target,
                                  RollOutcome outcome,
                                  String label,
                                  int diceCount,
                                  Dice damageDie,
                                  int damageModifier,
                                  TotalityDamageType damageType,
                                  boolean isMagical,
                                  @Nullable AbilityScore abilityScore) { // ← new

        if (!outcome.isHit()) {
            if (attacker instanceof ServerPlayer p)
                SendNotificationPayload.send(p, label + " — Miss!", SendNotificationPayload.GRAY);
            return;
        }

        // Refresh rage combat timer on hit
        if (attacker instanceof ServerPlayer sp && BarbarianRageAbility.isRaging(sp)) {
            BarbarianRageAbility.refreshCombatTimer(sp);
        }

        // Collect extra bonuses from registry
        List<DamageBonus> extraBonuses = DamageBonusRegistry.resolve(attacker, abilityScore, isMagical);
        int extraAmount = extraBonuses.stream().mapToInt(DamageBonus::amount).sum();

        boolean isCrit = outcome == RollOutcome.CRITICAL_SUCCESS;
        int count      = isCrit ? diceCount * 2 : diceCount;
        DamageRollResult dmg = DamageRoll.roll(attacker, count, damageDie, damageModifier + extraAmount);

        DamageFlags[] flags = isMagical
                ? new DamageFlags[]{ DamageFlags.MAGICAL }
                : new DamageFlags[0];

        TotalityDamage.hurt(target, attacker, damageType, dmg.total(), flags);

        if (attacker instanceof ServerPlayer p)
            DamageRollNotification.send(p, label + (isCrit ? " ✦ CRIT" : ""),
                    dmg, abilityScore, extraBonuses);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static String resolveWeaponName(LivingEntity attacker) {
        var held = attacker.getMainHandItem();
        return held.isEmpty() ? "Unarmed Strike" : held.getHoverName().getString();
    }

    private static int resolveAbilityMod(LivingEntity attacker, AbilityScore abilityScore) {
        if (attacker instanceof ServerPlayer p) {
            PlayerStats stats = StatsComponents.getStats(p);
            return stats != null ? stats.getModifier(abilityScore) : 0;
        }
        // TODO: mob stat component
        return 0;
    }

    private static final double MELEE_RANGE = 3.5;

    private static boolean isHostileInMeleeRange(LivingEntity caster) {
        return !caster.level().getEntitiesOfClass(
                Mob.class,
                caster.getBoundingBox().inflate(MELEE_RANGE),
                mob -> mob instanceof Enemy
                        && !mob.isDeadOrDying()
                        && caster.distanceTo(mob) <= MELEE_RANGE
        ).isEmpty();
    }
}