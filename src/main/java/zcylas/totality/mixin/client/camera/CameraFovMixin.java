package zcylas.totality.mixin.client.camera;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.client.camera.CameraSession;

/**
 * Camera app zoom: while the viewfinder is open, the view's FOV is the player's FOV setting (1×) magnified by the
 * Camera's zoom — a real projection change, so 0.5× renders a genuinely wider view. Nothing is written to the options,
 * and vanilla's culling already uses {@code max(fov, setting)}, so wider views are culled correctly.
 */
@Mixin(Camera.class)
public class CameraFovMixin {

    @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
    private void totality$cameraZoom(float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (!CameraSession.isActive()) return;
        cir.setReturnValue(CameraSession.fov(Minecraft.getInstance().options.fov().get().floatValue()));
    }
}
