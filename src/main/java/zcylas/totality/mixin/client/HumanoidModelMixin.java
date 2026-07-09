package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Ease;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.combat.DualWieldTracker;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {

    @Shadow @Final public ModelPart head;
    @Shadow @Final public ModelPart body;
    @Shadow @Final public ModelPart leftArm;

    @Invoker("poseBlockingArm")
    abstract void totality$poseBlockingArm(ModelPart arm, boolean isOffhand);

    // Inject after all arm posing (including setupAttackAnimation) has finished
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("RETURN"))
    private void totality$dualWieldLeftArm(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || avatarState.id != mc.player.getId()) return;

        if (DualWieldTracker.isDualBlocking) {
            totality$poseBlockingArm(leftArm, true);
            return;
        }

        float t = DualWieldTracker.renderOffhandAttackAnim;
        if (t <= 0f) return;

        // Replicates HumanoidModel.setupAttackAnimation's WHACK-type arm pose (decompiled from
        // minecraft-merged-deobf-26.1.2.jar), applied independently to the left arm using our
        // own progress instead of vanilla's shared attackTime — see project_dual_wield memory.
        float eased = Ease.outQuart(t);
        float sinEased = Mth.sin(eased * (float) Math.PI);
        float sinT = Mth.sin(t * (float) Math.PI);
        float headOffset = sinT * -(head.xRot - 0.7f) * 0.75f;
        leftArm.xRot -= sinEased * 1.2f + headOffset;
        leftArm.yRot += body.yRot * 2f;
        leftArm.zRot += sinT * -0.4f;
    }
}
