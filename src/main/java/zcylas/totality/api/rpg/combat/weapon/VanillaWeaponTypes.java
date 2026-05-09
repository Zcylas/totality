package zcylas.totality.api.rpg.combat.weapon;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.init.ModTags;

public class VanillaWeaponTypes {

    /**
     * Returns the WeaponType for the given item.
     * Checks TotalityWeaponItem interface first, then falls back to tags.
     * Returns null if not a recognized weapon.
     */
    public static WeaponType getType(Item item) {
        if (item instanceof TotalityWeaponItem w) {
            return w.getWeaponType();
        }
        // Use a dummy stack just for tag checking
        ItemStack stack = item.getDefaultInstance();
        if (stack.is(ModTags.TWO_HANDED_WEAPONS)) return WeaponType.TWO_HANDED;
        if (stack.is(ModTags.ONE_HANDED_WEAPONS))  return WeaponType.ONE_HANDED;
        return null;
    }

    /**
     * Returns the stamina cost for a normal attack with the given stack.
     * Falls back to unarmed cost if not a recognized weapon.
     */
    public static int getAttackCost(ItemStack stack) {
        if (stack.isEmpty()) {
            return WeaponType.UNARMED.normalAttackCost();
        }
        Item item = stack.getItem();
        if (item instanceof TotalityWeaponItem w) {
            return w.getNormalAttackCost();
        }
        if (stack.is(ModTags.TWO_HANDED_WEAPONS)) return WeaponType.TWO_HANDED.normalAttackCost();
        if (stack.is(ModTags.ONE_HANDED_WEAPONS))  return WeaponType.ONE_HANDED.normalAttackCost();
        // Not a recognized weapon — treat as unarmed
        return WeaponType.UNARMED.normalAttackCost();
    }

    /**
     * Returns true if the given stack is a two-handed weapon.
     */
    public static boolean isTwoHanded(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof TotalityWeaponItem w) {
            return w.getWeaponType() == WeaponType.TWO_HANDED;
        }
        return stack.is(ModTags.TWO_HANDED_WEAPONS);
    }

    private VanillaWeaponTypes() {}
}