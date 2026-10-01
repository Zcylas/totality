package zcylas.totality.mixin.client.camera;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.photo.PhotoCapture;

/**
 * Camera app photography points in the frame: after the world pass, after the post effect (the moment before
 * {@code FogRenderer.endFrame}, i.e. before the GUI pass) for clean photographs, and at the very end of the frame for
 * development full-screen captures. See {@link PhotoCapture}.
 */
@Mixin(GameRenderer.class)
public class CameraCaptureMixin {

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void totality$worldRendered(DeltaTracker deltaTracker, CallbackInfo ci) {
        PhotoCapture.onWorldRendered();
    }

    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
    private void totality$beforeGui(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        PhotoCapture.beforeGui(((GameRenderer) (Object) this).mainRenderTarget());
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void totality$endOfFrame(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        PhotoCapture.endOfFrame(((GameRenderer) (Object) this).mainRenderTarget());
    }
}
