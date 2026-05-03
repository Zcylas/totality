package zcylas.totality.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.component.ComponentContainer;
import zcylas.totality.api.component.ComponentProvider;
import zcylas.totality.api.component.PlayerComponentEvents;

@Mixin(LocalPlayer.class)
public abstract class MixinLocalPlayer implements ComponentProvider {

    @Unique
    private final ComponentContainer totality$componentContainer = new ComponentContainer();

    @Inject(method = "<init>", at = @At("RETURN"))
    private void totality$initComponents(CallbackInfo ci) {
        PlayerComponentEvents.attachClientComponentsTo(totality$componentContainer);
    }

    @Override
    public ComponentContainer getComponentContainer() {
        return totality$componentContainer;
    }
}