package zcylas.totality.mixin;

import net.minecraft.world.Difficulty;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Makes vanilla hunger-exhaustion's Food-decrement branch treat Peaceful the same as every other
 * difficulty, so physical Food depletion no longer depends on difficulty.
 *
 * <p><b>Root cause (real-client-adjacent review, 2026-09-17):</b> the real decompiled 26.2
 * {@code FoodData#tick}'s exhaustion-threshold block reads:
 * <pre>{@code
 * if (this.exhaustionLevel > 4.0F) {
 *     this.exhaustionLevel -= 4.0F;
 *     if (this.saturationLevel > 0.0F) {
 *         this.saturationLevel = Math.max(this.saturationLevel - 1.0F, 0.0F);
 *     } else if (difficulty != Difficulty.PEACEFUL) {
 *         this.foodLevel = Math.max(this.foodLevel - 1, 0);
 *     }
 * }
 * }</pre>
 * Once Saturation is exhausted, vanilla itself never even reaches the
 * {@code this.foodLevel = Math.max(this.foodLevel - 1, 0);} statement on Peaceful — the
 * {@code difficulty != Difficulty.PEACEFUL} guard skips the whole branch. Since
 * {@link FoodDataExhaustionAuthorityMixin} redirects the {@code Math.max(int, int)} call *inside*
 * that branch, its redirect body simply never runs on Peaceful either — not because of anything in
 * this codebase, but because vanilla's own bytecode never reaches that call site at all. This
 * conflicts with Totality's canonical Food model: Food represents physical fullness, and normal
 * activity (running, jumping, acting) must deplete it identically regardless of difficulty — only
 * the undesired auto-refill/Health-healing side of Peaceful is meant to differ, not physical
 * depletion itself.
 *
 * <p><b>Fix:</b> {@code difficulty} is read into a single local at the top of {@code tick} (
 * {@code Difficulty difficulty = level.getDifficulty();}) and used in exactly two places: this
 * exhaustion guard, and the {@code foodLevel <= 0} starvation branch's decision of whether to call
 * {@code player.hurtServer(...)} (gated on {@code difficulty == Difficulty.HARD}/{@code NORMAL}).
 * This mixin substitutes {@link Difficulty#EASY} for {@link Difficulty#PEACEFUL} at that single
 * local's STORE — the same {@code @ModifyVariable(at = @At("STORE"))} idiom
 * {@link FoodDataNaturalRegenerationMixin} already uses for its own local boolean. This unblocks the
 * exhaustion branch (the actual goal) and incidentally changes what value the starvation branch's
 * {@code hurtServer}-gating condition sees — but that call is unconditionally redirected to a no-op
 * by {@link FoodDataStarvationDamageAuthorityMixin} regardless of the condition's outcome, and the
 * surrounding {@code tickTimer} bookkeeping in that branch does not depend on {@code difficulty} at
 * all, so this substitution has zero observable effect there. No other read of {@code difficulty}
 * exists anywhere in {@code tick}.
 *
 * <p>Only this one local's value changes — the actual Saturation/Food decrement math, the
 * {@code exhaustionLevel > 4.0F} threshold, and vanilla Saturation's own absorption-first behavior
 * ({@code if (this.saturationLevel > 0.0F) { ...decrement saturation... }}) are completely untouched
 * and still run exactly as on any other difficulty: Saturation still absorbs the depletion event
 * first, and only once Saturation is exhausted does the (now-unblocked) Food decrement — translated
 * by {@link FoodDataExhaustionAuthorityMixin}, exactly as it already does for every other
 * difficulty — take effect. Peaceful's automatic Food refill
 * ({@link ServerPlayerPeacefulFoodRestoreAuthorityMixin}) and vanilla's direct starvation HP damage
 * ({@link FoodDataStarvationDamageAuthorityMixin}) remain suppressed, unaffected by this class.
 */
@Mixin(FoodData.class)
public abstract class FoodDataPeacefulExhaustionDepletionAuthorityMixin {

    @ModifyVariable(method = "tick", at = @At("STORE"), ordinal = 0)
    private Difficulty totality$treatPeacefulAsNonPeacefulForExhaustionDepletion(Difficulty difficulty) {
        return difficulty == Difficulty.PEACEFUL ? Difficulty.EASY : difficulty;
    }
}
