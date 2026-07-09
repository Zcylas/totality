package zcylas.totality.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.rpg.rest.RestSessionManager;

@Mixin(Player.class)
public abstract class PlayerRestPoseMixin {

    // Player#updatePlayerPose() runs every tick regardless of riding state, and since our fake
    // (non-isSleeping()) rest never satisfies any of getDesiredPose()'s conditions, it always
    // recomputes STANDING and calls setPose(STANDING) — fighting our own forced Pose.SLEEPING
    // every single tick. Reactively re-forcing it back at end-of-tick (see RestSessionManager)
    // still lets vanilla's STANDING write reach the network for part of that tick, which reads
    // as a constant camera/pose flicker client-side. Cancelling this entirely while we're forcing
    // a lying-down rest pose is the only way to stop the fight instead of reactively losing it.
    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void totality$skipPoseUpdateWhileRestLyingDown(CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (RestSessionManager.isForcingLyingPose(player.getUUID())) {
            ci.cancel();
        }
    }
}
