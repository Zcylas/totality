package zcylas.totality.item.equipment;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.equipment.TotalityRingItem;
import zcylas.totality.api.item.TotalityItemComponents;

import java.util.List;
import java.util.UUID;

public class RingOfProtectionItem extends TotalityRingItem {

    public RingOfProtectionItem(Item.Properties properties) {
        super(properties);
    }

    @Override public int getAcBonus()   { return 1; }
    @Override public int getSaveBonus() { return 1; }

    @Override
    public void addTooltipLines(ItemStack stack, Font font, List<Component> lines) {
        lines.add(Component.literal("+1 bonus to AC and saving throws")
                .withStyle(ChatFormatting.GRAY));
        lines.add(Component.literal("Requires Attunement")
                .withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.empty());

        // Attunement status line (mirrors TotalityItem default)
        UUID attunedTo = stack.get(TotalityItemComponents.ATTUNED_TO);
        Minecraft mc = Minecraft.getInstance();
        boolean attuned = attunedTo != null && mc.player != null
                && attunedTo.equals(mc.player.getUUID());
        if (attuned) {
            lines.add(Component.literal("ATTUNEMENT : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("ATTUNED").withStyle(ChatFormatting.GOLD)));
        } else {
            lines.add(Component.literal("ATTUNEMENT : ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("NOT ATTUNED").withStyle(ChatFormatting.RED)));
        }
    }
}