package zcylas.totality.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import zcylas.totality.client.mining.ClientMiningController;
import zcylas.totality.client.mining.MiningHandAnimation;

/**
 * Layers Totality's first-person mining pose over vanilla's first-person hand/item rendering. It replaces
 * nothing: vanilla still builds the arm/item transform, and this only
 * <ul>
 *   <li>zeroes the vanilla main-hand swing progress while a mining animation owns the hand,</li>
 *   <li>wraps the item render call to push/pop one extra transform for an item held in the MAIN hand, and</li>
 *   <li>wraps the bare-arm render call (empty main hand) to push/pop the same kind of transform about the
 *       shoulder pivot: ONE arm, the player's main arm, mirrored for left-handed players.</li>
 * </ul>
 * Everything is gated on {@link MiningHandAnimation#isActive()}, which is only true while the Totality mining
 * controller owns a swing/power presentation - never merely because LMB is down. The off-hand, maps, eating,
 * bows, shields and spear/piercing items are never touched.
 */
@Mixin(ItemInHandRenderer.class)
public class MiningHandRenderMixin {

    private static final String SUBMIT_ARM = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem"
            + "(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V";

    /** ordinal 0 = the MAIN hand call in submitHandsWithItems (Totality's ItemInHandRendererMixin owns ordinal 1, the offhand). */
    @ModifyArg(method = "submitHandsWithItems", at = @At(value = "INVOKE", target = SUBMIT_ARM, ordinal = 0), index = 4)
    private float totality$mainHandSwing(float vanillaAttack, @Local(argsOnly = true) float frameInterp) {
        // Any active mining presentation owns the main hand: vanilla's own swing must not stack on it.
        return ClientMiningController.ANIM.isActive() ? 0f : vanillaAttack;
    }

    /** Bare main hand: the arm itself is what animates. Pivot ~ where vanilla anchors the arm (0.64, -0.6, -0.72). */
    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderPlayerArm(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;IFFLnet/minecraft/world/entity/HumanoidArm;)V"))
    private void totality$miningBareArmPose(ItemInHandRenderer self, PoseStack poseStack, SubmitNodeCollector collector, int light,
                                            float inverseArmHeight, float attack, HumanoidArm arm, Operation<Void> original,
                                            @Local(argsOnly = true, ordinal = 0) float frameInterp) {
        MiningHandAnimation anim = ClientMiningController.ANIM;
        if (!anim.isActive()) {
            original.call(self, poseStack, collector, light, inverseArmHeight, attack, arm);
            return;
        }
        MiningHandAnimation.Transform t = anim.sample(frameInterp);
        int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
        poseStack.pushPose();
        try {
            poseStack.translate(invert * 0.64F, -0.6F, -0.72F);                 // shoulder-ish pivot
            poseStack.translate(invert * t.x(), t.y(), t.z());
            poseStack.mulPose(Axis.YP.rotationDegrees(invert * t.yaw()));
            poseStack.mulPose(Axis.ZP.rotationDegrees(invert * t.roll()));
            poseStack.mulPose(Axis.XP.rotationDegrees(t.pitch()));
            poseStack.translate(invert * -0.64F, 0.6F, 0.72F);
            original.call(self, poseStack, collector, light, inverseArmHeight, attack, arm);
        } finally {
            poseStack.popPose();
        }
    }

    @WrapOperation(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
    private void totality$miningItemPose(ItemInHandRenderer self, LivingEntity mob, ItemStack stack, ItemDisplayContext ctx,
                                         PoseStack poseStack, SubmitNodeCollector collector, int light, Operation<Void> original,
                                         @Local(argsOnly = true) AbstractClientPlayer player,
                                         @Local(argsOnly = true) InteractionHand hand,
                                         @Local(argsOnly = true, ordinal = 0) float frameInterp) {
        MiningHandAnimation anim = ClientMiningController.ANIM;
        if (hand != InteractionHand.MAIN_HAND || !anim.isActive()) {
            original.call(self, mob, stack, ctx, poseStack, collector, light);
            return;
        }
        MiningHandAnimation.Transform t = anim.sample(frameInterp);
        int invert = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;      // left-handed players are mirrored
        poseStack.pushPose();
        try {
            poseStack.translate(invert * t.x(), t.y(), t.z());
            poseStack.mulPose(Axis.YP.rotationDegrees(invert * (45.0F + t.yaw())));
            poseStack.mulPose(Axis.ZP.rotationDegrees(invert * t.roll()));
            poseStack.mulPose(Axis.XP.rotationDegrees(t.pitch()));
            poseStack.mulPose(Axis.YP.rotationDegrees(invert * -45.0F));
            original.call(self, mob, stack, ctx, poseStack, collector, light);
        } finally {
            poseStack.popPose();
        }
    }
}
