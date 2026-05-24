package zcylas.totality.item.alchemy;


import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import zcylas.totality.Totality;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffectInstance;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffects;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyIngredient;
import zcylas.totality.client.tooltip.TooltipExtension;

import java.util.List;


/**
 * Purple Mountain Flower — alchemy ingredient, also placeable.
 *
 * Effects (Skyrim-faithful slot order):
 *   0 — Restore Stamina      (BENEFICIAL) — revealed by eating
 *   1 — Fortify Sneak        (BENEFICIAL)
 *   2 — Lingering Damage Mana (HARMFUL)
 *   3 — Resist Frost         (BENEFICIAL)
 */
public class PurpleMountainFlowerItem extends Item implements AlchemyIngredient, TooltipExtension {

    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "purple_mountain_flower");

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESTORE_STAMINA,        0),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_SNEAK,          1),
            AlchemyEffectInstance.of(AlchemyEffects.LINGERING_DAMAGE_MANA,  2),
            AlchemyEffectInstance.of(AlchemyEffects.RESIST_FROST,           3)
    );

    public PurpleMountainFlowerItem(Properties properties) {
        super(properties);
    }

    @Override
    public Identifier getIngredientId() {
        return INGREDIENT_ID;
    }

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