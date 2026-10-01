package zcylas.totality.mixin.client.camera;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.client.camera.CameraSession;
import zcylas.totality.client.camera.CameraViewfinder;

/**
 * Camera app viewfinder. While it is open the HUD counts as hidden (like F1): vanilla then draws no held item, block
 * outline or name tags in the world pass, and the HUD layer draws only the viewfinder (no hotbar, crosshair, Totality
 * HUDs). A development full-screen capture keeps the HUD and adds the viewfinder on top instead.
 */
@Mixin(Hud.class)
public class CameraHudMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void totality$viewfinderOnly(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!CameraSession.hidesHud()) return;
        Minecraft.getInstance().gameRenderer.gameRenderState().guiRenderState.isHudHidden = true;
        CameraViewfinder.draw(graphics);
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void totality$viewfinderOverHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (CameraSession.isActive() && !CameraSession.hidesHud()) CameraViewfinder.draw(graphics);
    }

    @Inject(method = "isHidden", at = @At("HEAD"), cancellable = true)
    private void totality$hiddenWhileCamera(CallbackInfoReturnable<Boolean> cir) {
        if (CameraSession.hidesHud()) cir.setReturnValue(true);
    }
}
