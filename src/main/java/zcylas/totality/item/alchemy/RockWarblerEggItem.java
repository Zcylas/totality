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

/**
 * Rock Warbler Egg — an alchemy ingredient.
 *
 * Effects (slot order):
 *   0 — Restore Health      (BENEFICIAL) — revealed by eating
 *   1 — Fortify One-Handed  (BENEFICIAL) — no callback yet
 *   2 — Damage Stamina      (HARMFUL)    — no callback yet
 *   3 — Weakness to Magic   (HARMFUL)    — no callback yet
 */
public class RockWarblerEggItem extends Item implements AlchemyIngredient, TooltipExtension {

    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "rock_warbler_egg");

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESTORE_HEALTH,     0),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_ONE_HANDED, 1),
            AlchemyEffectInstance.of(AlchemyEffects.DAMAGE_STAMINA,     2),
            AlchemyEffectInstance.of(AlchemyEffects.WEAKNESS_TO_MAGIC,  3)
    );

    public RockWarblerEggItem(Properties properties) {
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