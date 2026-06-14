package zcylas.totality.api.rpg.combat.weapon;

import zcylas.totality.item.weapon.SkyrimSwordItem;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.rpg.stats.AbilityScore;

/**
 * Immutable stat block for a {@link SkyrimSwordItem}.
 * Pass one of these to the constructor so each sword variant only
 * needs to declare its numbers — all logic lives in SkyrimSwordItem.
 *
 * Example:
 * <pre>
 *   public static final SkyrimSwordStats DRAGONBONE = new SkyrimSwordStats(
 *       Dice.D8, 1, DamageTypes.PHYSICAL, AbilityScore.STR,
 *       WeaponCategory.MARTIAL_MELEE, WeaponType.ONE_HANDED,
 *       false, false, false, false);
 * </pre>
 */
public record TotalityWeaponStats(
        Dice             damageDie,
        int              diceCount,
        TotalityDamageType damageType,
        AbilityScore     abilityScore,   // default ability score for attack/damage
        WeaponCategory   weaponCategory,
        WeaponType       weaponType,
        boolean          finesse,        // can use DEX instead of STR
        boolean          light,          // can be used in off-hand
        boolean          heavy,          // disadvantage with small creatures
        boolean          reach           // +1 block range
) {}