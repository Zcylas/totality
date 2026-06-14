package zcylas.totality.item.weapon;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.bleach.zanpakuto.ZanpakutoType;
import zcylas.totality.api.combat.damage.TotalityDamageType;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.api.rpg.combat.weapon.*;
import zcylas.totality.api.rpg.stats.AbilityScore;

public class ZanpakutoItem extends TotalityMeleeWeaponItem {

    private final TotalityWeaponStats stats;

    public ZanpakutoItem(Item.Properties properties, TotalityWeaponStats stats) {
        super(properties);
        this.stats = stats;
    }

    /** Returns the current evolution stage stored on this stack. */
    public static ZanpakutoType getType(ItemStack stack) {
        return stack.getOrDefault(TotalityItemComponents.ZANPAKUTO_TYPE, ZanpakutoType.ASAUCHI);
    }

    @Override public Dice getDamageDie()                   { return stats.damageDie(); }
    @Override public int getDiceCount()                    { return stats.diceCount(); }
    @Override public TotalityDamageType getDamageType()    { return stats.damageType(); }
    @Override public AbilityScore getDefaultAbilityScore() { return stats.abilityScore(); }
    @Override public WeaponCategory getWeaponCategory()    { return stats.weaponCategory(); }
    @Override public WeaponType getWeaponType()            { return stats.weaponType(); }
    @Override public boolean isFinesse()                   { return stats.finesse(); }
    @Override public boolean isLight()                     { return stats.light(); }
    @Override public boolean isHeavy()                     { return stats.heavy(); }
    @Override public boolean isReach()                     { return stats.reach(); }
}
