package zcylas.totality.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.client.handler.FluidTankScrollHandler;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
        if (FluidTankScrollHandler.onScroll(yoffset)) {
            ci.cancel();
        }
    }
}