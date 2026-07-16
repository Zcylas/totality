package zcylas.totality.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.InteractionHand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import zcylas.totality.client.combat.DualWieldTracker;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {

    // First-person rendering keys the block/eat/charge arm transform off getUsedItemHand(),
    // which vanilla only ever tracks for ONE hand at a time (set to MAIN_HAND by our block
    // keybind). Without this, the offhand sword renders as a normal idle hold while blocking.
    @ModifyExpressionValue(method = "submitArmWithItem",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;"))
    private InteractionHand totality$forceDualBlockUsedHand(InteractionHand original,
            @Local(argsOnly = true) InteractionHand hand) {
        return DualWieldTracker.isDualBlocking ? hand : original;
    }

    // Independent offhand swing in first person: renderHandsWithItems() calls renderArmWithItem()
    // once per hand, passing 0 as the swing-progress argument for whichever hand isn't vanilla's
    // single swingingArm. Ordinal 1 = the OFF_HAND call — override its swing-progress argument
    // with our own tracked progress so it animates independently of the mainhand's real swing.
    @ModifyArg(method = "submitHandsWithItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;submitArmWithItem(Lnet/minecraft/client/player/AbstractClientPlayer;FFLnet/minecraft/world/InteractionHand;FLnet/minecraft/world/item/ItemStack;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V",
                    ordinal = 1),
            index = 4)
    private float totality$offhandSwingProgress(float original, @Local(argsOnly = true) float partialTick) {
        float custom = DualWieldTracker.getOffhandAttackAnim(partialTick);
        return custom > 0f ? custom : original;
    }
}
