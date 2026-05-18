package zcylas.totality.api.rpg.skills.alchemy;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import zcylas.totality.networking.alchemy.ClientAlchemyKnowledgeManager;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for any item that acts as an alchemy ingredient.
 *
 * Custom items implement this directly.
 * Vanilla items (e.g. Wheat) are registered via AlchemyIngredientRegistry instead,
 * and get their tooltip via AlchemyTooltipMixin.
 */
public interface AlchemyIngredient {

    Identifier getIngredientId();
    List<AlchemyEffectInstance> getAlchemyEffects();

    // -------------------------------------------------------------------------
    // Eating — call from finishUsingItem()
    // -------------------------------------------------------------------------

    default ItemStack onAlchemyEat(ItemStack stack, Level level, LivingEntity user) {
        if (!level.isClientSide() && user instanceof ServerPlayer player) {
            AlchemyKnowledgeComponent knowledge = AlchemyComponents.KNOWLEDGE.get(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );

            // Always reveal slot 0
            boolean isNewDiscovery = knowledge.revealEffect(getIngredientId(), 0);

            // Experimenter — also reveal slot 1
            int experimenterRank = zcylas.totality.api.rpg.skills.core.MasteriesComponents
                    .get(player).getMasteries().getUnlockedRank("experimenter");
            if (experimenterRank > 0 && getAlchemyEffects().size() > 1) {
                boolean slot1New = knowledge.revealEffect(getIngredientId(), 1);
                isNewDiscovery = isNewDiscovery || slot1New;
            }

            if (isNewDiscovery) {
                knowledge.sync();
            }

            AlchemySkillEvents.onIngredientEaten(player, isNewDiscovery);
        }
        return stack;
    }


    // -------------------------------------------------------------------------
    // Tooltip — call from appendHoverText()
    // -------------------------------------------------------------------------

    default void appendAlchemyTooltip(
            ItemStack stack,
            Consumer<Component> builder,
            TooltipFlag flag,
            Player player
    ) {
        appendAlchemyTooltip(builder, getIngredientId(), getAlchemyEffects());
    }

    /**
     * Static version — used by AlchemyTooltipMixin for vanilla items.
     * Reads from ClientAlchemyKnowledgeManager (client-side cache) so tooltips
     * always reflect the latest synced state without needing a component reference.
     */
    static void appendAlchemyTooltip(
            Consumer<Component> builder,
            Identifier ingredientId,
            List<AlchemyEffectInstance> effects
    ) {
        builder.accept(
                Component.literal("Alchemy Ingredient")
                        .withStyle(ChatFormatting.DARK_PURPLE)
        );

        for (int i = 0; i < 4; i++) {
            if (i >= effects.size()) break;
            AlchemyEffectInstance instance = effects.get(i);

            if (ClientAlchemyKnowledgeManager.isRevealed(ingredientId, i)) {
                AlchemyEffect effect = instance.effect();
                builder.accept(
                        Component.literal("  " + effect.getDisplayName())
                                .withStyle(effect.getType().color)
                );
            } else {
                builder.accept(Component.literal("  ???").withStyle(ChatFormatting.GRAY));
            }
        }
    }
}