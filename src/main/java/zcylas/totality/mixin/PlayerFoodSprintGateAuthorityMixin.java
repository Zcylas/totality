package zcylas.totality.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Removes vanilla's Food-based sprint gate. {@code Player#hasEnoughFoodToDoExhaustiveManoeuvres()}
 * ({@code return this.getFoodData().hasEnoughFood() || this.getAbilities().mayfly;}, verified against
 * the real decompiled 26.2 vanilla source) is called from exactly one place in the entire vanilla
 * codebase: the client-only {@code LocalPlayer#isSprintingPossible}, which gates whether the local
 * player's client will even attempt to start/continue sprinting. There is no server-side food-sprint
 * validation anywhere in vanilla — the client-side gate this method feeds is the entire mechanism.
 *
 * <p>Totality's design decision (2026-09-17 Food correction pass): Stamina is the sole sprint-
 * endurance authority (see {@code StaminaServerTick}/{@code TotalityMovementHandler}/
 * {@code PowerSprintStateHandler}, all untouched by this mixin) — low Food must never, by itself,
 * prevent or stop sprinting. Forcing this method to always return {@code true} (the same value
 * vanilla itself uses for a mayfly-enabled player) makes vanilla's own food check permanently
 * satisfied, leaving Totality's Stamina rules as the only remaining sprint gate. This mixin changes
 * nothing else about sprinting — jumping, riding, shallow water, and every other
 * {@code isSprintingPossible}/{@code canStartSprinting} condition are completely untouched.
 *
 * <p>Registered in the common (not client-only) mixin list even though the real caller is
 * client-only ({@code LocalPlayer}): {@code Player} is a common class present on both sides, nothing
 * server-side ever calls this method (confirmed by a full search of the decompiled 26.2 vanilla
 * source), so applying it universally is harmless and lets {@code FoodSystemVerification} (a
 * dedicated-server dev verification, which never loads client-only mixins) exercise this mixin's
 * real effect directly via reflection.
 */
@Mixin(Player.class)
public abstract class PlayerFoodSprintGateAuthorityMixin {

    @Inject(method = "hasEnoughFoodToDoExhaustiveManoeuvres", at = @At("HEAD"), cancellable = true)
    private void totality$bypassFoodSprintGate(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
