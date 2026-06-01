package zcylas.totality.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.combat.CombatTextRenderer;
import zcylas.totality.client.renderer.ability.HeatVisionBeamRenderer;
import zcylas.totality.client.renderer.energy.SidedOverlayRenderer;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Shadow @Final private GameRenderState gameRenderState;

    @Inject(method = "close", at = @At("RETURN"))
    private void onClose(CallbackInfo ci) {
        SidedOverlayRenderer.close();
        HeatVisionBeamRenderer.close();
    }

    @Inject(
            method = "extractGui",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Gui;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
            ),
            remap = false
    )
    private void totality$captureWorldMatrices(
            DeltaTracker deltaTracker,
            boolean shouldRenderLevel,
            boolean resourcesLoaded,
            CallbackInfo ci,
            @Local(name = "graphics") GuiGraphicsExtractor graphics
    ) {
        if (this.gameRenderState.levelRenderState == null) return;

        var cameraState = this.gameRenderState.levelRenderState.cameraRenderState;
        if (cameraState == null) return;

        CombatTextRenderer.onExtractGui(
                cameraState.viewRotationMatrix,
                cameraState.projectionMatrix,
                cameraState,
                graphics
        );
    }
}