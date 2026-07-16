package zcylas.totality.mixin.client;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.combat.CombatTextRenderer;

/**
 * MC 26.2 moved the {@code graphics} local (and the call to {@code Hud.extractRenderState})
 * out of {@code GameRenderer} (formerly {@code extractGui}, now folded into {@code extract})
 * and into {@code Gui.extractRenderState} itself — that's the only place {@code graphics} is
 * still a real local we can capture, so the injection moved here with it. Semantics are
 * unchanged: only fires when a level is actually being rendered (mirrors the old
 * {@code shouldRenderLevel}-gated {@code Hud.extractRenderState} call), same as before.
 */
@Mixin(Gui.class)
public class GuiExtractRenderStateMixin {

    @Shadow @Final private Minecraft minecraft;

    @Inject(
            method = "extractRenderState",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"
            )
    )
    private void totality$captureWorldMatrices(
            DeltaTracker deltaTracker,
            boolean shouldRenderLevel,
            boolean resourcesLoaded,
            CallbackInfo ci,
            @Local(name = "graphics") GuiGraphicsExtractor graphics
    ) {
        var gameRenderState = this.minecraft.gameRenderer.gameRenderState();
        if (gameRenderState.levelRenderState == null) return;

        var cameraState = gameRenderState.levelRenderState.cameraRenderState;
        if (cameraState == null) return;

        CombatTextRenderer.onExtractGui(
                cameraState.viewRotationMatrix,
                cameraState.projectionMatrix,
                cameraState,
                graphics
        );
    }
}
