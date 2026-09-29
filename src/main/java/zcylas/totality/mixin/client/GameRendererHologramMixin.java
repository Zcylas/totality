package zcylas.totality.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.hologram.HologramRenderer;
import zcylas.totality.client.hologram.TargetNameplate;

/**
 * Notification V2 hologram passes.
 * <ul>
 *   <li>First person — between the finished world and the first-person hand: vanilla has set its
 *       fixed-FOV hud projection and cleared depth, so the panel is a true 3D object that no world
 *       geometry can cut, with the hand still drawn in front.</li>
 *   <li>Third person — right after the world is rendered, while the world projection and depth are
 *       still current: the window projected in front of the character is really occluded by the
 *       character and terrain. The Mob HUD V1 target nameplate (all camera modes) is drawn here too.</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public class GameRendererHologramMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V"))
    private void totality$renderThirdPersonHolograms(DeltaTracker deltaTracker, CallbackInfo ci) {
        TargetNameplate.render((GameRenderer) (Object) this);
        HologramRenderer.renderWorldPass((GameRenderer) (Object) this);
    }

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V"))
    private void totality$renderSystemHolograms(DeltaTracker deltaTracker, CallbackInfo ci) {
        HologramRenderer.renderPass((GameRenderer) (Object) this);
    }
}
