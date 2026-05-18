package zcylas.totality.item.alchemy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffectInstance;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffects;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyIngredient;
import zcylas.totality.client.tooltip.TooltipExtension;

import java.util.List;
import java.util.function.Consumer;

public class GarlicItem extends Item implements AlchemyIngredient, TooltipExtension {
    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "garlic");

    public GarlicItem(Properties properties) {
        super(properties);
    }

    @Override
    public Identifier getIngredientId() {
        return INGREDIENT_ID;
    }

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESIST_POISON,       0),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_STAMINA,  1),
            AlchemyEffectInstance.of(AlchemyEffects.REGENERATE_MANA,       2),
            AlchemyEffectInstance.of(AlchemyEffects.REGENERATE_HEALTH, 3)
    );

    @Override
    public List<AlchemyEffectInstance> getAlchemyEffects() {
        return EFFECTS;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        ItemStack result = super.finishUsingItem(stack, level, user);
        onAlchemyEat(stack, level, user);
        return result;
    }
    @Override
    public void addTooltipLines(ItemStack stack, Font font, List<Component> lines) {
        AlchemyIngredient.appendAlchemyTooltip(
                lines::add,
                getIngredientId(),
                getAlchemyEffects()
        );
    }
}
