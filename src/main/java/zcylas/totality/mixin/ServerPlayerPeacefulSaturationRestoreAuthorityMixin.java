package zcylas.totality.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Suppresses {@code ServerPlayer#tickRegeneration}'s Peaceful-difficulty automatic Saturation
 * restore (real decompiled 26.2 source, inside the same {@code tickCount % 20 == 0} block as the
 * already-suppressed automatic heal):
 * <pre>{@code
 * float saturation = this.foodData.getSaturationLevel();
 * if (saturation < 20.0F) {
 *     this.foodData.setSaturation(saturation + 1.0F);
 * }
 * }</pre>
 *
 * <p><b>Root cause (2026-09-17 Pizza/Saturation correction pass):</b> when Peaceful's automatic Food
 * refill was disabled ({@link ServerPlayerPeacefulFoodRestoreAuthorityMixin}), this sibling
 * Saturation-restore statement in the very same method was deliberately left untouched at the time —
 * but real-client testing later found Peaceful still passively regenerating Saturation, which
 * conflicts with Totality's physical Food model just as much as the Food refill did: Saturation is
 * the temporary compatibility buffer standing in for physical fullness until the future Metabolic
 * Reserve system exists, and a player should not passively regain it merely because the difficulty is
 * Peaceful. This call bypasses {@code FoodData#add}'s own {@code Mth.clamp(..., 0.0F, this.foodLevel)}
 * ceiling entirely (a direct field write via {@code setSaturation}), which is exactly why vanilla can
 * push Saturation all the way to 20.0F on Peaceful regardless of the current Food/mirror value —
 * another reason this specific behavior does not belong in Totality's temporary compatibility model.
 *
 * <p>Only this one {@code setSaturation(float)} call site, inside {@code tickRegeneration}, is
 * redirected — normal eating-created Saturation (vanilla {@code FoodData#add}, still running for
 * every real-nutrition food), the Saturation mob effect, and {@code TotalityFoodItem}'s own authored
 * temporary Saturation contribution (added directly in {@code TotalityFoodItem#finishUsingItem}, a
 * completely different call site) are all untouched. Sibling of
 * {@link ServerPlayerPeacefulRegenerationMixin} (suppresses this same method's automatic Health heal)
 * and {@link ServerPlayerPeacefulFoodRestoreAuthorityMixin} (suppresses this same method's automatic
 * Food refill) — all three redirect a different call site inside the same vanilla method, independent
 * of one another.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPeacefulSaturationRestoreAuthorityMixin {

    @Redirect(method = "tickRegeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/food/FoodData;setSaturation(F)V"))
    private void totality$suppressPeacefulSaturationRestore(FoodData foodData, float saturation) {
        // Intentionally does not call setSaturation — Peaceful's automatic Saturation restore is
        // disabled, exactly like its automatic Food restore.
    }
}
