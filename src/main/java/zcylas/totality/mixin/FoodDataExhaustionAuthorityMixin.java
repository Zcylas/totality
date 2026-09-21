package zcylas.totality.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;

/**
 * Redirects {@code FoodData#tick}'s one exhaustion-driven Food decrement
 * ({@code this.foodLevel = Math.max(this.foodLevel - 1, 0);}, reached only once exhaustion crosses
 * its 4.0 threshold and saturation is already exhausted) so the real change lands on the true,
 * authoritative {@code totality:food} resource instead of only this legacy field. See
 * {@link FoodVanillaCompatibilityBridge} and
 * {@code TOTALITY_FOOD_0_100_AND_TOTALITY_FOOD_ITEM_IMPLEMENTATION_REPORT_2026-09-17.md}.
 *
 * <p><b>Vanilla hunger-exhaustion is only a temporary Food-depletion trigger</b> — it is not, and
 * must never be conflated with, Totality's own separate Stamina/Winded system, nor with the future
 * Fatigue design. This redirect always translates the semantic event "-1 old Food" into a real,
 * absolute "-5 true Food" (never a value measured from vanilla's own field, which would be lossy —
 * see {@code FoodVanillaCompatibilityBridge}'s own Javadoc), then writes back the corrected,
 * *proportional* mirror ({@link FoodVanillaCompatibilityBridge#mirrorOf}, current/max mapped into
 * vanilla's 0-20 domain — <b>not</b> a fixed {@code floor(trueValue / 5)}, which would silently
 * assume the resolved maximum is eternally 100).
 *
 * <p>{@code Math.max(int, int)} appears exactly once in {@code tick} (the sibling saturation branch
 * a few lines above uses the {@code Math.max(float, float)} overload, a structurally different
 * target), so this redirect cannot accidentally hit the wrong call site. Vanilla's own starvation
 * check ({@code foodLevel <= 0}) is untouched by this class, but its direct HP-damage *effect* is
 * disabled elsewhere by {@code FoodDataStarvationDamageAuthorityMixin} — Food reaching 0 causes no
 * damage. Sprint no longer depends on Food at all: {@code PlayerFoodSprintGateAuthorityMixin} makes
 * vanilla's own Food-based sprint check always pass, so Totality's existing Stamina system is the
 * sole sprint-endurance authority, independent of anything this class does. See
 * {@link FoodDataNaturalRegenerationMixin} for the sibling correction that already disables vanilla
 * Food-driven Health regeneration in this same method, untouched by this class.
 */
@Mixin(FoodData.class)
public abstract class FoodDataExhaustionAuthorityMixin {

    @Shadow private int foodLevel;

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Ljava/lang/Math;max(II)I"))
    private int totality$translateExhaustionDecrement(int decrementedValue, int floorValue, ServerPlayer player) {
        int uncorrectedResult = Math.max(decrementedValue, floorValue);
        int vanillaDelta = uncorrectedResult - this.foodLevel;
        return FoodVanillaCompatibilityBridge.translateAndResync(player, vanillaDelta, uncorrectedResult);
    }
}
