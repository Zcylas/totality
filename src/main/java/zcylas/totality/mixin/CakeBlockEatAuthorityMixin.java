package zcylas.totality.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;

/**
 * Closes the Cake authority hole: {@code CakeBlock#eat(...)} calls
 * {@code player.getFoodData().eat(2, 0.1F)} directly — the {@code FoodData#eat(int, float)} overload,
 * a completely different call site than {@code FoodProperties#onConsume}'s
 * {@code FoodData#eat(FoodProperties)}, so {@link FoodPropertiesEatAuthorityMixin} never sees it
 * (verified against the real decompiled 26.2 vanilla source — this was a known, documented gap in
 * the original Food 0-100 pass, closed in the 2026-09-17 correction pass).
 *
 * <p>Passes the literal {@code food} argument (Cake's real nutrition, {@code 2}) as the intended
 * restoration amount (2026-09-17 second correction pass) — never measured from vanilla's own
 * lossy {@code foodLevel} field, which would silently under-report near vanilla's 20-point ceiling.
 */
@Mixin(CakeBlock.class)
public abstract class CakeBlockEatAuthorityMixin {

    @Redirect(method = "eat", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"))
    private static void totality$translateCakeEat(
            FoodData foodData, int food, float saturationModifier,
            LevelAccessor level, BlockPos pos, BlockState state, Player player) {
        FoodVanillaCompatibilityBridge.interceptFoodDataEat(foodData, player, food, () -> foodData.eat(food, saturationModifier));
    }
}
