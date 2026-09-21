package zcylas.totality.mixin;

import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Totality's accepted Health-recovery policy: ordinary passive Health regeneration caused by
 * vanilla Food/hunger mechanics is disabled — Health is instead restored only through authored
 * systems (Rest, spells, runes, rituals, potions, medicine, conditions, abilities). See
 * {@code TOTALITY_NATURAL_FOOD_REGENERATION_CORRECTION_IMPLEMENTATION_REPORT.md}.
 *
 * <p>{@code FoodData#tick(ServerPlayer)} reads the real {@code NATURAL_HEALTH_REGENERATION}
 * gamerule into a single local boolean, {@code naturalRegen}, which gates both automatic Health
 * branches (fast/saturated: every 10 ticks while saturated, fully fed, and hurt; slow/well-fed:
 * every 80 ticks while food &gt;= 18 and hurt) — each branch calls {@code player.heal(...)} and
 * {@code this.addExhaustion(...)} together. Forcing only this method's local copy of that boolean
 * to {@code false} makes both branches structurally unreachable, which — since {@code heal()} and
 * {@code addExhaustion()} live inside the same now-dead branch bodies — suppresses the
 * regeneration-specific hunger exhaustion for free, without a second interception. The real
 * gamerule value in {@code GameRules} is never written; every other system that reads
 * {@code NATURAL_HEALTH_REGENERATION} is unaffected. Nothing above this local (the exhaustion
 * &gt; 4.0 saturation/food-level conversion) and nothing in the untouched starvation branch
 * (gated only on {@code foodLevel &lt;= 0}, not on {@code naturalRegen}) is affected either.
 */
@Mixin(FoodData.class)
public abstract class FoodDataNaturalRegenerationMixin {

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private boolean totality$suppressVanillaFoodDrivenHealthRegeneration(boolean naturalRegen) {
        return false;
    }
}
