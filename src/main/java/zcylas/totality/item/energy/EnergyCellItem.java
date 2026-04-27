package zcylas.totality.item.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.api.energy.UEFormat;
import zcylas.totality.block.energy.EnergyCellBlock;

import java.util.function.Consumer;

public class EnergyCellItem extends BlockItem {

    public EnergyCellItem(EnergyCellBlock block, Properties properties) {
        super(block, properties);
    }

    private long getCapacity() {
        return ((EnergyCellBlock) getBlock()).getCapacity();
    }

    private long getMaxInput() {
        return ((EnergyCellBlock) getBlock()).getMaxInput();
    }

    private long getMaxOutput() {
        return ((EnergyCellBlock) getBlock()).getMaxOutput();
    }

    private long getStoredEnergy(ItemStack stack) {
        var beData = stack.get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA);
        if (beData == null) return 0L;
        return beData.copyTagWithoutId().getLongOr("Energy", 0L);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        long capacity = getCapacity();
        if (capacity == 0) return 0;
        return (int) (getStoredEnergy(stack) * 13 / capacity);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        long capacity = getCapacity();
        if (capacity == 0) return 0xFF00FF00;
        float fraction = (float) getStoredEnergy(stack) / capacity;
        if (fraction > 0.5f) return 0xFF00AA00;
        if (fraction > 0.25f) return 0xFFFF8800;
        return 0xFFFF0000;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);

        long stored = getStoredEnergy(stack);
        long capacity = getCapacity();

        if (isShiftDown()) {
            builder.accept(Component.literal("Energy: " + stored + " / " + capacity + " UE")
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.literal("I/O: " + getMaxInput() + " / " + getMaxOutput() + " UE/t")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.accept(Component.literal("Energy: ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(UEFormat.energy(stored) + " / " + UEFormat.energy(capacity) + " UE")
                            .withStyle(ChatFormatting.GOLD)));
        }

        builder.accept(buildEnergyBar(stored, capacity));

        if (!isShiftDown()) {
            builder.accept(Component.literal("Hold SHIFT for details")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static MutableComponent buildEnergyBar(long stored, long max) {
        int totalBars = 20;
        int filledBars = max > 0 ? (int)((stored * totalBars) / max) : 0;

        float percent = max > 0 ? (float) stored / max : 0f;
        ChatFormatting filledColor;
        if (percent <= 0.05f) filledColor = ChatFormatting.RED;
        else if (percent <= 0.50f) filledColor = ChatFormatting.GOLD;
        else filledColor = ChatFormatting.GREEN;

        MutableComponent bar = Component.literal("");
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append(Component.literal("█").withStyle(filledColor));
            } else {
                bar.append(Component.literal("█").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return bar;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}