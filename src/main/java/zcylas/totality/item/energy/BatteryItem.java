package zcylas.totality.item.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.industrial.energy.UEComponents;
import zcylas.totality.api.industrial.energy.UEItem;

import java.util.List;

/**
 * Tooltip presentation is contributed by
 * {@link zcylas.totality.client.tooltip.contributor.EnergyContributor} (capability-driven off
 * {@link UEItem}) — the old hand-written legacy vanilla-tooltip override on this class has been
 * retired now that all five battery tiers explicitly opt into Totality tooltip presentation (see
 * {@code EnergyItems}), rather than being left behind as dead code.
 */
public class BatteryItem extends Item implements UEItem {

    private final long capacity;
    private final long maxInput;
    private final long maxOutput;

    public BatteryItem(long capacity, long maxInput, long maxOutput, Properties properties) {
        super(properties.stacksTo(1));
        this.capacity = capacity;
        this.maxInput = maxInput;
        this.maxOutput = maxOutput;
    }

    // --- UEItem ---

    @Override
    public long getEnergyCapacity(ItemStack stack) { return capacity; }

    @Override
    public long getEnergyMaxInput(ItemStack stack) { return maxInput; }

    @Override
    public long getEnergyMaxOutput(ItemStack stack) { return maxOutput; }

    // --- Active state ---

    public static boolean isActive(ItemStack stack) {
        return stack.has(UEComponents.BATTERY_ACTIVE);
    }

    public static void setActive(ItemStack stack, boolean active) {
        if (active) {
            stack.set(UEComponents.BATTERY_ACTIVE, true);
        } else {
            stack.remove(UEComponents.BATTERY_ACTIVE);
        }
    }

    // --- Enchantment glint ---

    @Override
    public boolean isFoil(ItemStack stack) {
        return isActive(stack);
    }

    // --- Toggle on shift right click ---

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                boolean newState = !isActive(stack);
                setActive(stack, newState);
                player.sendSystemMessage(Component.literal(
                                newState ? "Battery activated" : "Battery deactivated")
                        .withStyle(newState ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (!(owner instanceof Player player)) return;
        if (!isActive(stack)) return;
        if (getStoredEnergy(stack) <= 0) return;

        // Collect all chargeable stacks (excluding self)
        List<ItemStack> targets = new java.util.ArrayList<>();

        // Main inventory + hotbar
        // Armor slots first
        for (EquipmentSlot equipSlot : EquipmentSlot.values()) {
            if (equipSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR) {
                ItemStack target = player.getItemBySlot(equipSlot);
                if (target != stack && !target.isEmpty() && target.getItem() instanceof UEItem ueItem) {
                    if (!ueItem.isFull(target)) {
                        targets.add(target);
                    }
                }
            }
        }

// Then main inventory + hotbar
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack target = player.getInventory().getItem(i);
            if (target != stack && !target.isEmpty() && target.getItem() instanceof UEItem ueItem) {
                if (!ueItem.isFull(target)) {
                    targets.add(target);
                }
            }
        }

        if (targets.isEmpty()) return;

        // Charge the first uncharged item
        ItemStack target = targets.get(0);
        UEItem ueItem = (UEItem) target.getItem();

        long canReceive = Math.min(
                ueItem.getEnergyMaxInput(target),
                ueItem.getEnergyCapacity(target) - ueItem.getStoredEnergy(target));
        long canGive = Math.min(getEnergyMaxOutput(stack), getStoredEnergy(stack));
        long toTransfer = Math.min(canReceive, canGive);

        if (toTransfer > 0) {
            ueItem.setStoredEnergy(target, ueItem.getStoredEnergy(target) + toTransfer);
            setStoredEnergy(stack, getStoredEnergy(stack) - toTransfer);
        }
    }

    // --- Energy bar ---

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return getStoredEnergy(stack) < capacity;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return getEnergyBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return getEnergyBarColor(stack);
    }

}