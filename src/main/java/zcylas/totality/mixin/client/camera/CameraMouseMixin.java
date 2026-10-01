package zcylas.totality.mixin.client.camera;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.camera.CameraSession;

/**
 * Camera app mouse: while the viewfinder is open (and no other screen is), every button goes to the Camera (left =
 * shutter, or the on-screen controls with the Alt cursor) and none reaches attack, block breaking, item use or
 * pick-block; the wheel zooms instead of changing the hotbar slot; mouse-look slows down when magnified.
 */
@Mixin(MouseHandler.class)
public class CameraMouseMixin {

    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void totality$cameraButton(long handle, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle()) return;
        if (CameraSession.onMouseButton(button.button(), action == 1)) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void totality$cameraZoom(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (handle != Minecraft.getInstance().getWindow().handle() || yoffset == 0) return;
        if (CameraSession.onScroll(yoffset)) ci.cancel();
    }

    @WrapOperation(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void totality$cameraLookSensitivity(LocalPlayer player, double xo, double yo, Operation<Void> original) {
        double s = CameraSession.lookSensitivity();
        original.call(player, xo * s, yo * s);
    }
}
