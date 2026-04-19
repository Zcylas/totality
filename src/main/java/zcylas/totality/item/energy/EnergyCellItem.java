package zcylas.totality.item.energy;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
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
        int percent = capacity == 0 ? 0 : (int) (stored * 100 / capacity);

        if (isShiftDown()) {
            builder.accept(Component.literal(stored + " / " + capacity + " UE")
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.literal(percent + "%")
                    .withStyle(percent <= 5 ? ChatFormatting.RED
                            : percent <= 50 ? ChatFormatting.GOLD
                            : ChatFormatting.GREEN));
            builder.accept(Component.literal("I/O: " + getMaxInput() + " / " + getMaxOutput() + " UE/t")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            builder.accept(Component.literal(UEFormat.energy(stored) + " / " + UEFormat.energy(capacity) + " UE")
                    .withStyle(ChatFormatting.GRAY));
            builder.accept(Component.literal(percent + "%")
                    .withStyle(percent <= 5 ? ChatFormatting.RED
                            : percent <= 50 ? ChatFormatting.GOLD
                            : ChatFormatting.GREEN));
            builder.accept(Component.literal("Hold SHIFT for details")
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
}