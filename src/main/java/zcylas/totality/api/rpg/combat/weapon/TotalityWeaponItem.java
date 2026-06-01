package zcylas.totality.api.rpg.combat.weapon;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.classes.ClassComponents;
import zcylas.totality.api.rpg.classes.ClassRegistry;
import zcylas.totality.api.rpg.combat.DamageRollResult;
import zcylas.totality.api.rpg.stats.AbilityScore;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

public interface TotalityWeaponItem {

    // ── Existing ──────────────────────────────────────────────────────────────

    WeaponType getWeaponType();

    default int getNormalAttackCost() {
        return getWeaponType().normalAttackCost();
    }

    default int getThrownAttackCost() {
        return getWeaponType().normalAttackCost();
    }
    /** Returns the effective ability score for attack and damage rolls.
     *  Finesse weapons pick whichever of STR/DEX gives the higher modifier. */
    default AbilityScore getEffectiveAbilityScore(LivingEntity attacker) {
        if (!isFinesse()) return getDefaultAbilityScore();
        if (attacker instanceof ServerPlayer p) {
            PlayerStats stats = StatsComponents.getStats(p);
            if (stats != null) {
                return stats.getModifier(AbilityScore.STR) >= stats.getModifier(AbilityScore.DEX)
                        ? AbilityScore.STR : AbilityScore.DEX;
            }
        }
        return getDefaultAbilityScore();
    }
    // ── Combat stats ──────────────────────────────────────────────────────────

    Dice getDamageDie();
    int getDiceCount();
    TotalityDamageType getDamageType();
    AbilityScore getDefaultAbilityScore();
    WeaponCategory getWeaponCategory();

    // ── Overridable hooks ─────────────────────────────────────────────────────

    /** Whether this weapon can use DEX instead of STR for attack/damage. */
    default boolean isFinesse() { return false; }

    /** Flat bonus added to damage rolls — for variants, enchantments, etc. */
    default int getBonusDamage(LivingEntity attacker, LivingEntity target) { return 0; }

    /** Called on a confirmed hit — for on-hit effects, sounds, particles. */
    default void onHit(LivingEntity attacker, LivingEntity target, DamageRollResult dmg) {}

    /** Override to impose advantage or disadvantage from weapon-specific conditions. */
    default RollType modifyRollType(LivingEntity attacker, LivingEntity target, RollType base) {
        return base;
    }

    /** Whether this weapon has the Light property. */
    default boolean isLight()     { return false; }
    /** Whether this weapon has the Heavy property. */
    default boolean isHeavy()     { return false; }
    /** Whether this weapon has the Reach property. */
    default boolean isReach()     { return false; }
    /** Whether this weapon has the Versatile property. */
    default boolean isVersatile() { return false; }

    /**
     * Throw range in blocks {normal, long}.
     * Returns null for non-thrown weapons.
     * e.g. return new int[]{20, 60};
     */
    default int[] getThrowRange()  { return null; }

    /**
     * Whether the attacker is proficient with this weapon.
     * TODO: query PlayerClass.get(player).isProficientWith(getWeaponCategory())
     */
    default boolean isProficient(LivingEntity attacker) {
        if (attacker instanceof ServerPlayer player) {
            Identifier primary = ClassComponents.get(player).getPrimaryClassId();
            if (primary == null) return false;
            return ClassRegistry.get(primary)
                    .map(data -> data.weaponProficiencies().contains(getWeaponCategory()))
                    .orElse(false);
        }
        return true; // mobs always proficient for now
    }


}