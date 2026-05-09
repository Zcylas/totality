package zcylas.totality.api.industrial.energy.base;

import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.api.industrial.energy.UEStorage;

/**
 * Extend UEItem with actual storage behavior backed by a data component.
 * Implement this on your item class to get a full energy storage item.
 *
 * Example usage:
 * <pre>{@code
 * public class EnergyBatteryItem extends Item implements SimpleUEItem {
 *
 *     public EnergyBatteryItem(Properties properties) {
 *         super(properties);
 *     }
 *
 *     @Override
 *     public long getEnergyCapacity(ItemStack stack) { return 100_000L; }
 *
 *     @Override
 *     public UETier getTier() { return UETier.ADVANCED; }
 *
 *     @Override
 *     public boolean isBarVisible(ItemStack stack) { return true; }
 *
 *     @Override
 *     public int getBarWidth(ItemStack stack) { return getEnergyBarWidth(stack); }
 *
 *     @Override
 *     public int getBarColor(ItemStack stack) { return getEnergyBarColor(); }
 * }
 * }</pre>
 */
public interface SimpleUEItem extends UEItem {

    /**
     * Return a UEStorage view of the given stack.
     * The returned storage is tied to this specific stack instance.
     */
    default UEStorage getEnergyStorage(ItemStack stack) {
        return new UEItemStorage(stack, this);
    }
}