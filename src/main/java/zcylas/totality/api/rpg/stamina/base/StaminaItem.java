package zcylas.totality.api.rpg.stamina.base;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface StaminaItem {
    int getMaxStamina(ItemStack stack, StaminaSource source);

    default void appendMaxStaminaTooltip(ItemStack stack, Consumer<Component> builder) {
        int bonus = getMaxStamina(stack, StaminaSource.ITEM);
        if (bonus > 0) {
            builder.accept(Component.literal("+" + bonus + " Max Stamina")
                    .withStyle(style -> style.withColor(0x44FF44)));
        }
    }
}