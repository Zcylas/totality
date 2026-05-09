package zcylas.totality.api.industrial.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import zcylas.totality.api.industrial.energy.base.SimpleUEItem;

import java.util.function.Consumer;

public abstract class UEArmorItem extends Item implements SimpleUEItem {

    private final long energyCapacity;
    private final long maxInput;
    private final long maxOutput;
    private final EquipmentSlot slot;

    public UEArmorItem(Item.Properties properties, EquipmentSlot slot,
                       long energyCapacity, long maxInput, long maxOutput) {
        super(properties
                .stacksTo(1)
                .equippable(slot)
                .component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
                .component(DataComponents.TOOLTIP_DISPLAY, new TooltipDisplay(
                        false, new java.util.LinkedHashSet<>(
                        java.util.Set.of(DataComponents.UNBREAKABLE)))));
        this.slot = slot;
        this.energyCapacity = energyCapacity;
        this.maxInput = maxInput;
        this.maxOutput = maxOutput;
    }

    @Override
    public long getEnergyCapacity(ItemStack stack) { return energyCapacity; }

    @Override
    public long getEnergyMaxInput(ItemStack stack) { return maxInput; }

    @Override
    public long getEnergyMaxOutput(ItemStack stack) { return maxOutput; }

    @Override
    public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) { return getEnergyBarWidth(stack); }

    @Override
    public int getBarColor(ItemStack stack) { return getEnergyBarColor(stack); }

    public EquipmentSlot getSlotType() {
        return slot;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);

        long stored = getStoredEnergy(stack);
        long cap = getEnergyCapacity(stack);
        int percent = cap == 0 ? 0 : (int) (stored * 100 / cap);
        boolean advanced = flag.isAdvanced();

        if (advanced) {
            builder.accept(Component.literal(stored + " / " + cap + " UE")
                    .withStyle(ChatFormatting.GRAY));
        } else {
            builder.accept(Component.literal(UEFormat.energy(stored) + " / " + UEFormat.energy(cap) + " UE")
                    .withStyle(ChatFormatting.GRAY));
        }

        builder.accept(Component.literal(percent + "%")
                .withStyle(percent <= 5 ? ChatFormatting.RED
                        : percent <= 50 ? ChatFormatting.GOLD
                        : ChatFormatting.GREEN));
    }
}