package zcylas.totality.api.combat.weapon;

/**
 * Interface for custom weapon items to declare their weapon type
 * for stamina cost calculation and future power attack behavior.
 *
 * Vanilla weapons are handled via VanillaWeaponTypes lookup.
 * Custom weapons should implement this interface and return their type.
 *
 * Example:
 * public class GreathammerItem extends Item implements TotalityWeaponItem {
 *     public WeaponType getWeaponType() { return WeaponType.TWO_HANDED; }
 * }
 */
public interface TotalityWeaponItem {

    /**
     * Returns the weapon type of this item.
     */
    WeaponType getWeaponType();

    /**
     * Override to customize the normal attack stamina cost.
     * Defaults to the WeaponType's base cost.
     */
    default int getNormalAttackCost() {
        return getWeaponType().normalAttackCost();
    }

    /**
     * Returns the stamina cost for throwing this weapon.
     * Only relevant for THROWN type weapons.
     * Defaults to the WeaponType's base cost.
     */
    default int getThrownAttackCost() {
        return getWeaponType().normalAttackCost();
    }
}