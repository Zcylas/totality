package zcylas.totality.item.potion;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import zcylas.totality.api.alchemy.AlchemyEffect;
import zcylas.totality.api.potions.EffectEntry;
import zcylas.totality.api.potions.PotionData;
import zcylas.totality.api.potions.PotionDataComponent;

import java.util.List;
import java.util.function.Consumer;

/**
 * Drinkable alchemy potion item.
 * PotionData is stored as a DataComponentType on the stack,
 * so a single registered item can represent any brewed or pre-defined potion.
 */
public class AlchemyPotionItem extends Item {

    // Default data used for registered staple potions (PotionItems)
    private final PotionData defaultData;

    public AlchemyPotionItem(PotionData defaultData, Properties properties) {
        super(properties);
        this.defaultData = defaultData;
    }

    /** Get the PotionData from the stack component, falling back to the item's default. */
    public PotionData getPotionData(ItemStack stack) {
        PotionData fromStack = stack.get(PotionDataComponent.POTION_DATA);
        return fromStack != null ? fromStack : defaultData;
    }

    // ── Drinking ──────────────────────────────────────────────────────────────

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 32;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.DRINK;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        ItemStack result = super.finishUsingItem(stack, level, user);

        if (!level.isClientSide()) {
            PotionData data = getPotionData(stack);
            for (EffectEntry entry : data.effects()) {
                entry.effect().applyConsume(user, entry.magnitude(), entry.durationTicks());
            }
        }

        return result;
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        PotionData data = getPotionData(stack);
        for (EffectEntry entry : data.effects()) {
            AlchemyEffect effect = entry.effect();
            int col = switch (effect.getType()) {
                case BENEFICIAL -> 0xFF66FF66;
                case HARMFUL    -> 0xFFFF6666;
                case NEUTRAL    -> 0xFFCCCCCC;
            };
            builder.accept(Component.literal(effect.getDisplayName())
                    .withStyle(s -> s.withColor(col)));
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal(getPotionData(stack).displayName());
    }
}