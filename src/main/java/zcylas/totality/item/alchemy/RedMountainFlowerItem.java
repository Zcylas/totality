package zcylas.totality.item.alchemy;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import zcylas.totality.Totality;
import zcylas.totality.api.alchemy.AlchemyEffectInstance;
import zcylas.totality.api.alchemy.AlchemyEffects;
import zcylas.totality.api.alchemy.AlchemyIngredient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Red Mountain Flower — alchemy ingredient, also placeable.
 *
 * Effects (Skyrim-faithful slot order):
 *   0 — Restore Mana   (BENEFICIAL) — revealed by eating
 *   1 — Ravage Mana    (HARMFUL)
 *   2 — Fortify Mana   (BENEFICIAL)
 *   3 — Damage Health  (HARMFUL)
 */
public class RedMountainFlowerItem extends BlockItem implements AlchemyIngredient {

    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "red_mountain_flower");

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESTORE_MANA,   0),
            AlchemyEffectInstance.of(AlchemyEffects.RAVAGE_MANA,    1),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_MANA,   2),
            AlchemyEffectInstance.of(AlchemyEffects.DAMAGE_HEALTH,  3)
    );

    public RedMountainFlowerItem(Block block, Properties properties) {
        super(block, properties);
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
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        Player player = Minecraft.getInstance().player;
        appendAlchemyTooltip(stack, builder, flag, player);
    }
}