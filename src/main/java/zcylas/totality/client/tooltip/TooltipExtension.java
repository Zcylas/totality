package zcylas.totality.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public interface TooltipExtension {
    void addTooltipLines(ItemStack stack, Font font, List<Component> lines);
}