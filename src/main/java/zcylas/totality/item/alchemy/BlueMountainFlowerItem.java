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
import java.util.function.Consumer;

/**
 * Blue Mountain Flower — alchemy ingredient that is also placeable.
 * Extends BlockItem so right-clicking ground places the flower block.
 * Implements AlchemyIngredient for discovery, tooltip and brewing.
 *
 * Effects (slot order, faithful to Skyrim):
 *   0 — Restore Health        (BENEFICIAL) — revealed by eating
 *   1 — Fortify Conjuration   (BENEFICIAL)
 *   2 — Fortify Health        (BENEFICIAL)
 *   3 — Damage Magicka Regen  (HARMFUL)
 */
public class BlueMountainFlowerItem extends Item implements AlchemyIngredient, TooltipExtension {

    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "blue_mountain_flower");

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESTORE_HEALTH,       0),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_CONJURATION,  1),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_HEALTH,       2),
            AlchemyEffectInstance.of(AlchemyEffects.DAMAGE_MANA_REGEN, 3)
    );

    public BlueMountainFlowerItem(Properties  properties) {
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