package zcylas.totality.mixin.client;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.renderer.entity.skateboard.SkateboardRiderPose;
import zcylas.totality.entity.vehicle.SkateboardEntity;

/**
 * A player on a skateboard is drawn standing, not seated: vanilla poses every passenger seated
 * ({@code HumanoidRenderState.isPassenger}), and there is no vehicle hook for a standing rider. Render state only; the
 * server's view of the passenger is untouched. The head's turn is kept within what a side-on rider can look over.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererSkateboardMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void totality$standOnSkateboard(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        boolean riding = entity.getVehicle() instanceof SkateboardEntity;
        state.setData(SkateboardRiderPose.RIDING, riding ? Boolean.TRUE : null);
        if (!riding) return;
        state.isPassenger = false;
        state.yRot = Mth.clamp(state.yRot, SkateboardRiderPose.HEAD_TURN_MIN, SkateboardRiderPose.HEAD_TURN_MAX);
    }
}
