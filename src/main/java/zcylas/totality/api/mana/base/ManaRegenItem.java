package zcylas.totality.api.mana.base;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface ManaRegenItem {
    float getManaRegenMultiplier(ItemStack stack, ManaSource source);

    default void appendManaRegenTooltip(ItemStack stack, Consumer<Component> builder) {
        float bonus = getManaRegenMultiplier(stack, ManaSource.ITEM);
        if (bonus > 0) {
            builder.accept(Component.literal("+" + (int)(bonus * 100) + "% Mana Regen")
                    .withStyle(style -> style.withColor(0x44AAFF)));
        }
    }
}
