package zcylas.totality.api.energy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.core.Holder;

/**
 * Implement on items that store UnifiedEnergy (UE).
 * Provides standard energy storage behavior including:
 * - Configurable I/O rates
 * - Unbreaking enchantment support for energy consumption
 * - Durability bar rendering for charge level
 * - Component-based energy persistence
 */
public interface UEItem {

    /**
     * Return the maximum energy this item can store.
     */
    long getEnergyCapacity(ItemStack stack);

    /**
     * Return the maximum energy that can be inserted per operation.
     */
    long getEnergyMaxInput(ItemStack stack);

    /**
     * Return the maximum energy that can be extracted per operation.
     */
    long getEnergyMaxOutput(ItemStack stack);

    /**
     * Return the energy stored in this stack.
     */
    default long getStoredEnergy(ItemStack stack) {
        long stored = stack.getOrDefault(UEComponents.ENERGY, 0L);
        return Math.min(stored, getEnergyCapacity(stack));
    }

    /**
     * Directly set the energy stored in this stack.
     */
    default void setStoredEnergy(ItemStack stack, long newAmount) {
        if (newAmount <= 0) {
            stack.remove(UEComponents.ENERGY);
        } else {
            stack.set(UEComponents.ENERGY, newAmount);
        }
    }

    /**
     * Try to consume exactly amount energy, respecting Unbreaking enchantment.
     * Returns true if successful, false if not enough energy.
     */
    default boolean tryUseEnergy(ItemStack stack, long amount) {
        if (stack.getCount() != 1) {
            throw new IllegalArgumentException("Stack count must be 1, got: " + stack.getCount());
        }

        int unbreakingLevel = getUnbreakingLevel(stack);
        if (unbreakingLevel > 0) {
            amount = amount / (RandomSource.create().nextInt(unbreakingLevel) + 1);
        }

        long newAmount = getStoredEnergy(stack) - amount;
        if (newAmount < 0) return false;

        setStoredEnergy(stack, newAmount);
        return true;
    }

    /**
     * Return true if this stack has enough energy for the given amount.
     */
    default boolean hasEnergy(ItemStack stack, long amount) {
        return getStoredEnergy(stack) >= amount;
    }

    /**
     * Return true if this stack is fully charged.
     */
    default boolean isFull(ItemStack stack) {
        return getStoredEnergy(stack) >= getEnergyCapacity(stack);
    }

    /**
     * Return true if this stack has no energy.
     */
    default boolean isEmpty(ItemStack stack) {
        return getStoredEnergy(stack) <= 0;
    }

    /**
     * Return a value between 0 and 13 for the durability bar width.
     */
    default int getEnergyBarWidth(ItemStack stack) {
        return Math.round((getStoredEnergy(stack) * 100f / getEnergyCapacity(stack)) * 13) / 100;
    }

    /**
     * Return the color of the energy durability bar.
     * Green at 100%, orange at 50%, red at 5%.
     */
    default int getEnergyBarColor(ItemStack stack) {
        float percent = (float) getStoredEnergy(stack) / getEnergyCapacity(stack);
        if (percent <= 0.05f) return 0xFF0000; // red
        if (percent <= 0.50f) return 0xFF6600; // orange
        return 0x00AA00;                        // green
    }

    /**
     * Return true if two stacks are equal ignoring their stored energy.
     */
    static boolean isEqualIgnoreEnergy(ItemStack a, ItemStack b) {
        if (a == b) return true;
        if (a.getCount() != b.getCount()) return false;
        if (ItemStack.isSameItemSameComponents(a, b)) return true;

        ItemStack aCopy = a.copy();
        ItemStack bCopy = b.copy();
        aCopy.remove(UEComponents.ENERGY);
        bCopy.remove(UEComponents.ENERGY);

        return ItemStack.isSameItemSameComponents(aCopy, bCopy);
    }

    private int getUnbreakingLevel(ItemStack stack) {
        ItemEnchantments enchantments = stack.getOrDefault(
                DataComponents.ENCHANTMENTS,
                ItemEnchantments.EMPTY
        );
        for (Holder<Enchantment> entry : enchantments.keySet()) {
            if (entry.unwrapKey().equals(Enchantments.UNBREAKING)) {
                return enchantments.getLevel(entry);
            }
        }
        return 0;
    }


}