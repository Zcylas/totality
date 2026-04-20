package zcylas.totality.api.mana.base;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface ManaItem {
    int getMaxMana(ItemStack stack, ManaSource source);

    default void appendMaxManaTooltip(ItemStack stack, Consumer<Component> builder) {
        int bonus = getMaxMana(stack, ManaSource.ITEM);
        if (bonus > 0) {
            builder.accept(Component.literal("+" + bonus + " Max Mana")
                    .withStyle(style -> style.withColor(0x4444FF)));
        }
    }
}
