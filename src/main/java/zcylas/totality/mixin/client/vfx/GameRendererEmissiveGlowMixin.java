package zcylas.totality.mixin.client.vfx;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.vfx.glow.EmissiveGlow;

/**
 * Runs the experimental emissive glow layer once per frame, right after the level is rendered: the world's colour and
 * depth are complete, and the first-person hand, screen effects and GUI are drawn afterwards (so they never glow).
 * See {@link EmissiveGlow}.
 */
@Mixin(GameRenderer.class)
public class GameRendererEmissiveGlowMixin {

    @Inject(method = "renderLevel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/LevelRenderer;render(Lcom/mojang/blaze3d/resource/GraphicsResourceAllocator;Lnet/minecraft/client/DeltaTracker;ZLnet/minecraft/client/renderer/state/level/CameraRenderState;Lorg/joml/Matrix4fc;Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lorg/joml/Vector4f;Z)V",
            shift = At.Shift.AFTER))
    private void totality$emissiveGlow(DeltaTracker deltaTracker, CallbackInfo ci) {
        EmissiveGlow.afterLevel((GameRenderer) (Object) this);
    }
}
