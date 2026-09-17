package zcylas.totality.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.api.rpg.resources.food.FoodVanillaCompatibilityBridge;

/**
 * Redirects {@code FoodProperties#onConsume}'s vanilla nutrition application
 * ({@code player.getFoodData().eat(this)}) so eating any ordinary vanilla-backed food (apples,
 * bread, any item registered with real {@code FoodProperties} nutrition) restores the true,
 * authoritative {@code totality:food} resource instead of only vanilla's legacy field — the one
 * central compatibility point for every vanilla food item, rather than a per-item translation
 * table. {@code totality:pizza_margherita}/{@code totality:pizza_margherita_slice}
 * ({@code TotalityFoodItem}) still reach this class (they deliberately carry a zero-nutrition
 * {@code FoodProperties} so {@code InventoryActionHandler}/{@code Consumable#canConsume} still
 * recognize/allow them), but {@link FoodVanillaCompatibilityBridge#interceptFoodDataEat} now skips
 * {@code applyVanillaEat} entirely for a zero intended nutrition (Pizza/Saturation correction pass,
 * 2026-09-17) rather than still running vanilla's own zero-value {@code eat} — see that method's own
 * Javadoc for the real Saturation-clamping bug this closes. {@code TotalityFoodItem} restores the
 * true Food resource, and its own authored temporary Saturation, directly in
 * {@code TotalityFoodItem#finishUsingItem} instead.
 *
 * <p>{@code CakeBlock} and {@code SaturationMobEffect} call vanilla's other {@code eat(int, float)}
 * overload directly, bypassing this item-based path entirely — closed separately by
 * {@link CakeBlockEatAuthorityMixin}/{@link SaturationMobEffectEatAuthorityMixin} (2026-09-17
 * correction pass), reusing this class's own {@code FoodVanillaCompatibilityBridge
 * #interceptFoodDataEat} apply/translate/resync sequence.
 *
 * <p>Passes {@code self.nutrition()} — the real, authored nutrition value — as the intended
 * restoration amount (2026-09-17 second correction pass): the true authoritative restore is now
 * derived directly from this value, never measured by diffing vanilla's own {@code foodLevel}
 * field, which would silently under-report near vanilla's 20-point ceiling. See
 * {@code FoodVanillaCompatibilityBridge}'s own Javadoc for the exact bug this replaces.
 */
@Mixin(FoodProperties.class)
public abstract class FoodPropertiesEatAuthorityMixin {

    @Redirect(method = "onConsume", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;eat(Lnet/minecraft/world/food/FoodProperties;)V"))
    private void totality$translateVanillaEat(
            FoodData foodData, FoodProperties self, Level level, LivingEntity user, ItemStack stack, Consumable consumable) {
        FoodVanillaCompatibilityBridge.interceptFoodDataEat(foodData, user, self.nutrition(), () -> foodData.eat(self));
    }
}
