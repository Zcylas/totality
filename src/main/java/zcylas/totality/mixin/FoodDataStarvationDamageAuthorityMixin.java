package zcylas.totality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Disables vanilla's direct starvation Health damage
 * ({@code player.hurtServer(level, player.damageSources().starve(), 1.0F);}, inside
 * {@code FoodData#tick}'s {@code foodLevel <= 0} branch, verified against the real decompiled 26.2
 * vanilla source). Totality's Food model (2026-09-17 correction pass) allows Food to reach 0 without
 * any direct HP damage — future sustained-underfeeding consequences belong to the later Diet /
 * Metabolism / Fatigue design, not to this pass reintroducing or replacing vanilla's own mechanic.
 *
 * <p>Only the {@code hurtServer(...)} call itself is redirected to a no-op; the surrounding
 * {@code tickTimer}/difficulty bookkeeping in the same branch is left completely untouched (no new
 * starvation timer, stage, or state is introduced — this purely removes vanilla's damage effect).
 * Sibling of {@link FoodDataNaturalRegenerationMixin} (which already disables vanilla Food-driven
 * Health regeneration in this same method) and {@link FoodDataExhaustionAuthorityMixin} (which
 * redirects this method's exhaustion-driven Food decrement) — none of the three interfere with each
 * other, each targets a structurally distinct part of {@code tick}.
 */
@Mixin(FoodData.class)
public abstract class FoodDataStarvationDamageAuthorityMixin {

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"))
    private boolean totality$suppressStarvationDamage(ServerPlayer player, ServerLevel level, DamageSource source, float amount) {
        // Intentionally does not call hurtServer — starvation direct HP damage is disabled.
        return false;
    }
}
