package zcylas.totality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;

/**
 * Closes the Saturation-effect authority hole: {@code SaturationMobEffect#applyEffectTick} calls
 * {@code player.getFoodData().eat(amplification + 1, 1.0F)} directly (e.g. from
 * {@code /effect give ... minecraft:saturation}) — the {@code FoodData#eat(int, float)} overload,
 * the same one {@link CakeBlockEatAuthorityMixin} closes for Cake, a completely different call site
 * than {@code FoodProperties#onConsume} (verified against the real decompiled 26.2 vanilla source —
 * a known, documented gap in the original Food 0-100 pass, closed in the 2026-09-17 correction pass).
 *
 * <p>{@code net.minecraft.world.effect.SaturationMobEffect} is package-private, so it is targeted by
 * fully-qualified string name (Mixin weaves at the bytecode level and is unaffected by source-level
 * visibility) rather than an ordinary import/{@code .class} reference.
 *
 * <p>Passes the literal {@code food} argument (the effect's real intended restoration,
 * {@code amplification + 1}) as the intended restoration amount (2026-09-17 second correction
 * pass) — never measured from vanilla's own lossy {@code foodLevel} field, which would silently
 * under-report near vanilla's 20-point ceiling.
 */
@Mixin(targets = "net.minecraft.world.effect.SaturationMobEffect")
public abstract class SaturationMobEffectEatAuthorityMixin {

    @Redirect(method = "applyEffectTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;eat(IF)V"))
    private void totality$translateSaturationEffectEat(
            FoodData foodData, int food, float saturationModifier,
            ServerLevel level, LivingEntity mob, int amplification) {
        FoodVanillaCompatibilityBridge.interceptFoodDataEat(foodData, mob, food, () -> foodData.eat(food, saturationModifier));
    }
}
