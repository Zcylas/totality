package zcylas.totality.item.alchemy;

import net.minecraft.client.Minecraft;
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

import java.util.List;
import java.util.function.Consumer;

/**
 * Salmon Roe — an alchemy ingredient.
 *
 * Effects (slot order, faithful to Skyrim):
 *   0 — Restore Stamina    (BENEFICIAL) — revealed by eating
 *   1 — Waterbreathing     (NEUTRAL)
 *   2 — Fortify Magicka    (BENEFICIAL)
 *   3 — Regenerate Magicka (BENEFICIAL)
 */
public class SalmonRoeItem extends Item implements AlchemyIngredient {

    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "salmon_roe");

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESTORE_STAMINA,    0),
            AlchemyEffectInstance.of(AlchemyEffects.WATERBREATHING,     1),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_MANA,    2),
            AlchemyEffectInstance.of(AlchemyEffects.REGENERATE_MANA, 3)
    );

    public SalmonRoeItem(Properties properties) {
        super(properties);
    }

    @Override
    public Identifier getIngredientId() { return INGREDIENT_ID; }

    @Override
    public List<AlchemyEffectInstance> getAlchemyEffects() { return EFFECTS; }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity user) {
        ItemStack result = super.finishUsingItem(stack, level, user);
        onAlchemyEat(stack, level, user);
        return result;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        Player player = Minecraft.getInstance().player;
        appendAlchemyTooltip(stack, builder, flag, player);
    }
}