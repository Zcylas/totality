package zcylas.totality.api.rpg.combat.weapon;

import net.minecraft.world.item.Item;

/**
 * Abstract base for melee weapons.
 * Implements TotalityWeaponItem — subclass only needs to declare stats.
 *
 * Combat hook: a mixin on Player.attack() will route through CombatResolver
 * when the held item is an instance of this class.
 *
 * TODO: add PlayerAttackMixin once melee weapons exist.
 */
public abstract class TotalityMeleeWeaponItem extends Item implements TotalityWeaponItem {

    protected TotalityMeleeWeaponItem(Properties properties) {
        super(properties);
    }

    @Override
    public WeaponType getWeaponType() { return WeaponType.ONE_HANDED; }

    @Override
    public boolean isFinesse() { return false; }
}