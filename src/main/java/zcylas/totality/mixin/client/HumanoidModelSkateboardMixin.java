package zcylas.totality.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.renderer.entity.skateboard.SkateboardRiderPose;

/** The skateboarding stance on top of vanilla's standing pose (players and their armor layers alike). */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelSkateboardMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void totality$skateboardStance(HumanoidRenderState state, CallbackInfo ci) {
        if (state.getData(SkateboardRiderPose.RIDING) == null) return;
        SkateboardRiderPose.apply((HumanoidModel<?>) (Object) this);
    }
}
