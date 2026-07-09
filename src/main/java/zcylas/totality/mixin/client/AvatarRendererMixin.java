package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.combat.DualWieldTracker;

@Mixin(AvatarRenderer.class)
public class AvatarRendererMixin {

    // extractRenderState has partialTick (unlike setupAnim, where the render state's fields are
    // already pre-interpolated) — cache a smoothly-interpolated offhand swing value here, once per
    // frame, for the local player only, so HumanoidModelMixin's third-person pose isn't stepped.
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void totality$cacheOffhandSwingAnim(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || entity.getId() != mc.player.getId()) return;
        DualWieldTracker.renderOffhandAttackAnim = DualWieldTracker.getOffhandAttackAnim(partialTick);
    }
}
