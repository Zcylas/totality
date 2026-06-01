package zcylas.totality.api.rpg.combat.weapon;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import zcylas.totality.api.dice.Dice;

/**
 * Abstract base for all thrown weapons.
 * Holds common fields — subclasses only need to declare their stats.
 *
 * Subclass must implement:
 *   - getDamageType()
 *   - getDefaultAbilityScore()
 *   - use() — throwing logic (spawns the projectile entity)
 */
public abstract class TotalityThrownWeaponItem extends Item implements TotalityWeaponItem {

    protected final Dice damageDie;
    protected final int  diceCount;
    protected final int  bonusDamage;

    protected TotalityThrownWeaponItem(Properties properties,
                                       Dice damageDie,
                                       int diceCount,
                                       int bonusDamage) {
        super(properties);
        this.damageDie   = damageDie;
        this.diceCount   = diceCount;
        this.bonusDamage = bonusDamage;
    }

    // ── TotalityWeaponItem defaults ───────────────────────────────────────────

    @Override public Dice getDamageDie()  { return damageDie; }
    @Override public int  getDiceCount()  { return diceCount; }
    @Override public WeaponType getWeaponType()       { return WeaponType.THROWN; }
    @Override public WeaponCategory getWeaponCategory() { return WeaponCategory.THROWN; }

    @Override
    public int getBonusDamage(LivingEntity attacker, LivingEntity target) {
        return bonusDamage;
    }
}