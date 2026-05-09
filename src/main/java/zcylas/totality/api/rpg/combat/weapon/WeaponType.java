package zcylas.totality.api.rpg.combat.weapon;

/**
 * Defines weapon categories for stamina cost calculation.
 * Each type has a normal attack stamina cost.
 * Power attack costs are handled separately when that system is implemented.
 */
public enum WeaponType {
    ONE_HANDED(10),  // Swords, daggers, axes (one-handed)
    TWO_HANDED(15),  // Greatswords, battleaxes, warhammers
    UNARMED(5),      // Bare hands, gloves like Titanstone Knuckles
    THROWN(10);

    private final int normalAttackCost;

    WeaponType(int normalAttackCost) {
        this.normalAttackCost = normalAttackCost;
    }

    public int normalAttackCost() {
        return normalAttackCost;
    }
}