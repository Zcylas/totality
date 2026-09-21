package zcylas.totality.mixin;

import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Totality's accepted Health-recovery policy: ordinary passive Health regeneration caused by
 * vanilla Food/hunger mechanics — including Peaceful difficulty's own automatic path — is
 * disabled. See {@code TOTALITY_NATURAL_FOOD_REGENERATION_CORRECTION_IMPLEMENTATION_REPORT.md}.
 *
 * <p>{@code ServerPlayer#tickRegeneration()} (the server override of {@code Player}'s empty hook,
 * called from {@code Player#aiStep()} every tick) is Peaceful difficulty's own, separate automatic
 * Health path — entirely independent of {@code FoodData#tick} (see
 * {@link FoodDataNaturalRegenerationMixin}). While {@code Difficulty.PEACEFUL} and the real
 * {@code NATURAL_HEALTH_REGENERATION} gamerule are both true, every 20 ticks it calls
 * {@code this.heal(1.0F)} and, as two separate sibling statements in the same block, restores
 * saturation; every 10 ticks, in a separate outer statement, it restores Food level. Only the
 * {@code heal(F)} call site is redirected here (to a no-op) — the saturation and Food-level
 * restoration statements are untouched, so Peaceful hunger/saturation restoration keeps working
 * exactly as before. No exhaustion is charged by this vanilla path in the first place, so nothing
 * else needs suppressing to keep this correction narrow.
 */
@Mixin(ServerPlayer.class)
public abstract class ServerPlayerPeacefulRegenerationMixin {

    @Redirect(method = "tickRegeneration", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;heal(F)V"))
    private void totality$suppressPeacefulAutomaticHeal(ServerPlayer player, float amount) {
        // Intentionally does not call player.heal(amount) — Peaceful's automatic Health
        // restoration is suppressed; every other statement in tickRegeneration() still runs.
    }
}
