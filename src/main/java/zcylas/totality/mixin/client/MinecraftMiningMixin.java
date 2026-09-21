package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.client.mining.ClientMiningController;

/**
 * Stops vanilla's continuous destroy state machine (and its own click/hold swing) for blocks
 * Totality mining owns. Vanilla entity attacks, Power Attack (MinecraftAttackMixin) and dual-wield
 * are unaffected: this only acts when the crosshair is on a mineable block.
 */
@Mixin(Minecraft.class)
public class MinecraftMiningMixin {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void totality$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (ClientMiningController.ownsCrosshairBlock((Minecraft) (Object) this)) cir.setReturnValue(false);
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void totality$continueAttack(boolean down, CallbackInfo ci) {
        if (down && ClientMiningController.ownsCrosshairBlock((Minecraft) (Object) this)) ci.cancel();
    }
}
