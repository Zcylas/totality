package zcylas.totality.api.rpg.stamina.base;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public interface StaminaRegenItem {
    float getStaminaRegenMultiplier(ItemStack stack, StaminaSource source);

    default void appendStaminaRegenTooltip(ItemStack stack, Consumer<Component> builder) {
        float bonus = getStaminaRegenMultiplier(stack, StaminaSource.ITEM);
        if (bonus > 0) {
            builder.accept(Component.literal("+" + (int)(bonus * 100) + "% Stamina Regen")
                    .withStyle(style -> style.withColor(0x88FF88)));
        }
    }
}