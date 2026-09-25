package zcylas.totality.api.rpg.combat;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
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
import zcylas.totality.networking.combat.CombatRollNotification;
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

        AttackRoll.Result ar = AttackRoll.roll(attacker, target, abilityScore, proficient, rollType);
        handleHit(attacker, target, ar, resolveWeaponName(attacker), diceCount, damageDie, damageModifier, damageType, false, abilityScore);
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
    /**
     * Name-only overload — deliberately does NOT apply any weapon durability itself. For a caller
     * that already owns a separate, unrelated durability mechanism for the same conceptual item
     * (e.g. {@code ThrownShurikenEntity}, whose thrown-and-landed pickup stack takes its own
     * dedicated wear right where it lands, independent of melee semantics entirely) — using the
     * {@code ItemStack}-based overload below there would double-apply durability. Ordinary
     * held-weapon melee attacks (Totality's own {@code VanillaDamageInterceptor}/
     * {@code OffhandAttackHandler}) use the {@code ItemStack}-based overload instead, specifically
     * so the real wielded stack's durability is worn down automatically.
     */
    public static void resolveAttack(LivingEntity attacker, LivingEntity target,
                                     AbilityScore abilityScore, boolean proficient,
                                     RollType rollType, int diceCount, Dice damageDie,
                                     TotalityDamageType damageType, String weaponName) {
        int mod = resolveAbilityMod(attacker, abilityScore);
        AttackRoll.Result ar = AttackRoll.roll(attacker, target, abilityScore, proficient, rollType);
        handleHit(attacker, target, ar, weaponName, diceCount, damageDie, mod, damageType, false, abilityScore);
    }

    /**
     * Melee-weapon-durability review pass (2026-09-22): the weapon dealing this attack is now the
     * {@code ItemStack} actually wielded (in {@code slot}), not merely a display name — after a
     * confirmed hit (never a miss, matching vanilla's own semantics: {@code ItemStack#postHurtEnemy}
     * only ever fires once {@code target.hurt(...)} has already returned {@code true}), it is worn
     * down through the exact same normal, enchantment-aware mechanism vanilla itself uses — see
     * {@link #applyMeleeWeaponDurability}. This is what actually restores Pickaxe/Axe/Shovel (and
     * any other {@code DataComponents.WEAPON}-bearing item) durability loss on a landed melee hit —
     * Totality's own damage/roll pipeline (below) never touched item durability at all before this.
     */
    public static void resolveAttack(LivingEntity attacker, LivingEntity target,
                                     AbilityScore abilityScore, boolean proficient,
                                     RollType rollType, int diceCount, Dice damageDie,
                                     TotalityDamageType damageType, ItemStack weapon, EquipmentSlot slot) {
        int mod = resolveAbilityMod(attacker, abilityScore);
        AttackRoll.Result ar = AttackRoll.roll(attacker, target, abilityScore, proficient, rollType);
        String weaponName = weapon.isEmpty() ? "Unarmed Strike" : weapon.getHoverName().getString();
        handleHit(attacker, target, ar, weaponName, diceCount, damageDie, mod, damageType, false, abilityScore);
        if (ar.outcome().isHit() && attacker instanceof ServerPlayer player) {
            applyMeleeWeaponDurability(weapon, player, slot);
        }
    }

    /**
     * The standard vanilla melee-weapon durability cost — read live from the weapon's own
     * {@link Weapon} data component (never a hard-coded amount; every vanilla tool/weapon already
     * carries one, e.g. {@code ToolMaterial#applyToolProperties} attaches
     * {@code new Weapon(2, ...)} to every Pickaxe/Axe/Shovel/Hoe) — applied through
     * {@link ItemStack#hurtAndBreak(int, LivingEntity, EquipmentSlot)}, the exact same
     * enchantment-aware path (Unbreaking included, via {@code EnchantmentHelper.processDurabilityChange}
     * internally) both vanilla's own {@code ItemStack#postHurtEnemy} and Totality's own mining wear
     * ({@code PlayerMiningManager}) already use — no custom Totality Unbreaking logic, no
     * reimplementation. Deliberately not simply {@code weapon.postHurtEnemy(target, player)} itself:
     * that vanilla method always reports a broken item against {@code EquipmentSlot.MAINHAND}
     * regardless of which hand actually swung, which is wrong for Totality's own offhand attacks —
     * this takes {@code slot} explicitly so an offhand weapon breaking is reported correctly. A
     * weapon with no {@link Weapon} component (or an empty stack, i.e. an unarmed strike) takes no
     * durability, exactly like vanilla.
     */
    private static void applyMeleeWeaponDurability(ItemStack weapon, ServerPlayer player, EquipmentSlot slot) {
        if (weapon.isEmpty()) return;
        Weapon weaponData = weapon.get(DataComponents.WEAPON);
        if (weaponData == null) return;
        weapon.hurtAndBreak(weaponData.itemDamagePerAttack(), player, slot);
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
        resolveSpellAttack(caster, target, spellName, spellcastingAbility,
                attackType, rollType, diceCount, damageDie, damageType
                /* no extra flags */);
    }

    public static void resolveSpellAttack(LivingEntity caster,
                                          LivingEntity target,
                                          String spellName,
                                          AbilityScore spellcastingAbility,
                                          SpellAttackType attackType,
                                          RollType rollType,
                                          int diceCount,
                                          Dice damageDie,
                                          TotalityDamageType damageType,
                                          DamageFlags... extraFlags) {

        if (attackType == SpellAttackType.MELEE && caster.distanceTo(target) > MELEE_RANGE) {
            if (caster instanceof ServerPlayer p)
                SendNotificationPayload.send(p, spellName + " — Target out of reach!", SendNotificationPayload.GRAY);
            return;
        }

        RollType effectiveRollType = rollType;
        if (attackType == SpellAttackType.RANGED && isHostileInMeleeRange(caster)) {
            effectiveRollType = RollType.DISADVANTAGE;
        }

        AttackRoll.Result ar = AttackRoll.roll(caster, target, spellcastingAbility, true, effectiveRollType);
        handleHit(caster, target, ar, spellName, diceCount, damageDie, 0, damageType, true, null, extraFlags);
    }

    // ── Shared ────────────────────────────────────────────────────────────────

    /**
     * Shared hit/miss resolution for both weapon and spell attacks. Sends at most one compact
     * combined attack-and-damage notification per resolved attack (Part D) — two semantic lines on
     * a miss (label+outcome, ATK), three on a hit or critical hit (+DMG).
     */
    private static void handleHit(LivingEntity attacker,
                                  LivingEntity target,
                                  AttackRoll.Result attackResult,
                                  String label,
                                  int diceCount,
                                  Dice damageDie,
                                  int damageModifier,
                                  TotalityDamageType damageType,
                                  boolean isMagical,
                                  @Nullable AbilityScore abilityScore,
                                  DamageFlags... extraFlags) {

        RollOutcome outcome = attackResult.outcome();

        if (!outcome.isHit()) {
            if (attacker instanceof ServerPlayer p)
                CombatRollNotification.send(p, label, attackResult, null, null, List.of());
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

        DamageFlags[] baseFlags = isMagical
                ? new DamageFlags[]{ DamageFlags.MAGICAL }
                : new DamageFlags[0];

        // Merge base flags with any extra flags passed by the caller
        DamageFlags[] allFlags;
        if (extraFlags.length == 0) {
            allFlags = baseFlags;
        } else {
            allFlags = new DamageFlags[baseFlags.length + extraFlags.length];
            System.arraycopy(baseFlags, 0, allFlags, 0, baseFlags.length);
            System.arraycopy(extraFlags, 0, allFlags, baseFlags.length, extraFlags.length);
        }

        TotalityDamage.hurt(target, attacker, damageType, dmg.total(), allFlags);

        if (attacker instanceof ServerPlayer p)
            CombatRollNotification.send(p, label, attackResult, dmg, abilityScore, extraBonuses);
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