package zcylas.totality.api.equipment;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.item.TotalityArmorItem;

import java.util.function.Predicate;

/**
 * A Slot backed by PlayerEquipmentComponent (server) or a SimpleContainer (client).
 * Accepts only TotalityRingItem items (for ring slots).
 */
public class TotalityAccessorySlot extends Slot {

    private final Player player;
    private final Identifier noItemIcon;
    private final Predicate<ItemStack> filter;

    public TotalityAccessorySlot(Container container, Player player, int slotIndex, int x, int y, Identifier noItemIcon) {
        this(container, player, slotIndex, x, y, noItemIcon, stack -> stack.getItem() instanceof TotalityRingItem);
    }

    public TotalityAccessorySlot(Container container, Player player, int slotIndex, int x, int y, Identifier noItemIcon, Predicate<ItemStack> filter) {
        super(container, slotIndex, x, y);
        this.player = player;
        this.noItemIcon = noItemIcon;
        this.filter = filter;
    }

    @Override
    public Identifier getNoItemIcon() { return noItemIcon; }

    @Override
    public boolean mayPlace(ItemStack stack) {
        return !stack.isEmpty() && filter.test(stack);
    }

    @Override
    public int getMaxStackSize() { return 1; }

    @Override
    public int getMaxStackSize(ItemStack stack) { return 1; }

    @Override
    public void set(ItemStack stack) {
        if (player.level() instanceof ServerLevel) {
            ItemStack old = getItem();
            if (!old.isEmpty() && old.getItem() instanceof TotalityArmorItem armor) {
                armor.onUnequip(old, (ServerPlayer) player);
            }
            super.set(stack);
            if (!stack.isEmpty() && stack.getItem() instanceof TotalityArmorItem armor) {
                armor.onEquip(stack, (ServerPlayer) player);
            }
            EquipmentComponents.get((ServerPlayer) player).sync();
        } else {
            super.set(stack);
        }
    }

    @Override
    public ItemStack remove(int amount) {
        if (player.level() instanceof ServerLevel) {
            ItemStack old = getItem();
            if (!old.isEmpty() && old.getItem() instanceof TotalityArmorItem armor) {
                armor.onUnequip(old, (ServerPlayer) player);
            }
            ItemStack removed = super.remove(amount);
            if (!removed.isEmpty()) {
                EquipmentComponents.get((ServerPlayer) player).sync();
            }
            return removed;
        }
        return super.remove(amount);
    }
}