package zcylas.totality.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses {@code ServerPlayer#tickRegeneration}'s Peaceful-difficulty automatic Food restore
 * ({@code this.foodData.setFoodLevel(this.foodData.getFoodLevel() + 1);}, every 10 ticks while
 * {@code needsFood()}) entirely.
 *
 * <p><b>Corrected 2026-09-17 (real-client manual test correction):</b> this mixin originally
 * translated the +1 into a true +5 on the authoritative resource (see the removed
 * {@code FoodVanillaCompatibilityBridge} call below). Real-client testing on Peaceful found Food
 * silently climbing back up on its own, which is explicitly rejected for Totality: Food represents
 * physical fullness/hunger and must not regenerate merely because the difficulty is Peaceful — only
 * a real authored Food effect (eating, a future Diet/Metabolism mechanic) may change it. The redirect
 * target is unchanged ({@code setFoodLevel(I)V} inside {@code tickRegeneration}), but the body is now
 * a pure no-op: neither vanilla's own field nor the true resource is touched, so Food (mirror and
 * true value alike) stays exactly where it was through this tick.
 *
 * <p>Sibling of {@link ServerPlayerPeacefulRegenerationMixin}, which already suppresses this same
 * method's automatic Health heal in the same way — that correction and this one are independent and
 * untouched by each other; Peaceful's own saturation restore statement (a plain field write, no
 * redirect target needed here) is also untouched.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPeacefulFoodRestoreAuthorityMixin {

    @Redirect(method = "tickRegeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;setFoodLevel(I)V"))
    private void totality$suppressPeacefulFoodRestore(FoodData foodData, int newFoodLevel) {
        // Intentionally does not call setFoodLevel — Peaceful's automatic Food restore is disabled.
    }
}
