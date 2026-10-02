package zcylas.totality.mixin.client.vfx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.vfx.screen.ScreenFx;

/**
 * Shared Screen FX camera shake, appended to the hurt-bob pose at every exit of {@code bobHurt} (it returns early when
 * the player is not hurt). Minecraft multiplies that pose into the level projection and the first-person hand pose, so
 * only the rendered view moves; the player's rotation and aim do not. See {@link ScreenFx#applyShake}.
 */
@Mixin(GameRenderer.class)
public class GameRendererScreenFxMixin {

    @Inject(method = "bobHurt", at = @At("RETURN"))
    private void totality$screenFxShake(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        ScreenFx.applyShake(poseStack);
    }
}
