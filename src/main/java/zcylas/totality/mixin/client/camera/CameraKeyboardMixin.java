package zcylas.totality.mixin.client.camera;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.camera.CameraSession;

/**
 * Camera app keys, before vanilla's: ESC goes back to the Phone (instead of the pause menu), the Phone key (TAB)
 * closes the session, arrows switch mode and zoom, and the perspective key is ignored. Releases always pass through,
 * so movement keys never get stuck.
 */
@Mixin(KeyboardHandler.class)
public class CameraKeyboardMixin {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void totality$cameraKeys(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (action == 0 || handle != Minecraft.getInstance().getWindow().handle()) return;
        if (CameraSession.onKey(event)) ci.cancel();
    }
}
