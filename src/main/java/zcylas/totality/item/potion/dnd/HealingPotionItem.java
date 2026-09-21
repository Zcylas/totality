package zcylas.totality.item.potion.dnd;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import zcylas.totality.networking.potion.HealingRollNotification;

import java.util.Objects;

/**
 * Standalone D&D-style healing potion, independent of Totality's Alchemy API.
 * Healing formula and use duration are supplied at registration so this same class can later
 * back Greater/Superior/Supreme Healing tiers with different formulas and durations.
 */
public class HealingPotionItem extends Item {

    private final HealingAmount healingAmount;
    private final int useDurationTicks;

    public HealingPotionItem(HealingAmount healingAmount, int useDurationTicks, Properties properties) {
        super(properties);
        this.healingAmount = Objects.requireNonNull(healingAmount, "healingAmount");
        if (useDurationTicks <= 0) {
            throw new IllegalArgumentException("useDurationTicks must be greater than zero: " + useDurationTicks);
        }
        this.useDurationTicks = useDurationTicks;
    }

    /** The reusable healing formula this stack was registered with — read by the Tooltip API. */
    public HealingAmount getHealingAmount() {
        return healingAmount;
    }

    /** The configured drink duration in ticks this stack was registered with. */
    public int getUseDurationTicks() {
        return useDurationTicks;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return useDurationTicks;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        String displayName = stack.getHoverName().getString(); // read BEFORE super consumes the stack
        ItemStack result = super.finishUsingItem(stack, level, user);
        if (!level.isClientSide()) {
            RandomSource random = user.getRandom();
            HealingRollResult rollResult = healingAmount.rollDetailed(random);
            int amount = rollResult.total();
            if (amount > 0) {
                float healthBefore = user.getHealth();
                user.heal(amount);
                float actualHealing = Math.max(0.0F, user.getHealth() - healthBefore);
                if (actualHealing > 0 && user instanceof ServerPlayer serverPlayer) {
                    HealingRollNotification.send(serverPlayer, displayName, rollResult, actualHealing);
                }
            }
        }
        return result;
    }
}
