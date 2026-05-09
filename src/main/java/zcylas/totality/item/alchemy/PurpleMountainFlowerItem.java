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
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffectInstance;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyEffects;
import zcylas.totality.api.rpg.skills.alchemy.AlchemyIngredient;

import java.util.List;
import java.util.function.Consumer;

/**
 * Purple Mountain Flower — alchemy ingredient, also placeable.
 *
 * Effects (Skyrim-faithful slot order):
 *   0 — Restore Stamina      (BENEFICIAL) — revealed by eating
 *   1 — Fortify Sneak        (BENEFICIAL)
 *   2 — Lingering Damage Mana (HARMFUL)
 *   3 — Resist Frost         (BENEFICIAL)
 */
public class PurpleMountainFlowerItem extends BlockItem implements AlchemyIngredient {

    private static final Identifier INGREDIENT_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "purple_mountain_flower");

    private static final List<AlchemyEffectInstance> EFFECTS = List.of(
            AlchemyEffectInstance.of(AlchemyEffects.RESTORE_STAMINA,        0),
            AlchemyEffectInstance.of(AlchemyEffects.FORTIFY_SNEAK,          1),
            AlchemyEffectInstance.of(AlchemyEffects.LINGERING_DAMAGE_MANA,  2),
            AlchemyEffectInstance.of(AlchemyEffects.RESIST_FROST,           3)
    );

    public PurpleMountainFlowerItem(Block block, Properties properties) {
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