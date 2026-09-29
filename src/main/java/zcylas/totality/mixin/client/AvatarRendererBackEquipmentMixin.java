package zcylas.totality.mixin.client;

import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.equipment.BackEquipmentAppearance;
import zcylas.totality.client.renderer.equipment.BackEquipmentLayer;

/**
 * Hands each avatar's Back item (from the render-only mirror synced to every watching client) to
 * {@link BackEquipmentLayer} through the render state. A player wearing something on the back does not also get
 * vanilla's cape drawn in the same place.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererBackEquipmentMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void totality$extractBackEquipment(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        ItemStack back = BackEquipmentAppearance.get(entity);
        state.setData(BackEquipmentLayer.BACK_ITEM, back.isEmpty() ? null : back.getItem());
        if (!back.isEmpty()) state.showCape = false;
    }
}
