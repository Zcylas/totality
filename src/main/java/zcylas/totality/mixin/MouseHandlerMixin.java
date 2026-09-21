package zcylas.totality.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.handler.FluidTankScrollHandler;
import zcylas.totality.client.tooltip.TotalityTooltipScrollHandler;

/**
 * Injects into {@code MouseHandler.onScroll(J DD)V} — the private method GLFW's scroll callback
 * ultimately invokes (via {@code Minecraft.execute(...)}, confirmed by decompiling
 * {@code minecraft-merged.jar} for MC 26.2) before any screen ever sees the wheel event. The
 * injection is {@code @At("HEAD")} and {@code cancellable = true}; {@link CallbackInfo#cancel()}
 * is called only when the corresponding handler actually consumed the event (returned
 * {@code true}), never unconditionally — cancelling here aborts {@code onScroll}'s entire body,
 * so {@code Screen.mouseScrolled(...)} (called partway through that body) is never reached at all
 * for a consumed event, and is reached completely normally — for the background screen (a
 * container's item list, Creative's own scrolling, etc.) — for one that isn't.
 */
@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (FluidTankScrollHandler.onScroll(yoffset)) {
            ci.cancel();
            return;
        }
        if (TotalityTooltipScrollHandler.onMouseScroll(yoffset)) {
            ci.cancel();
        }
    }
}