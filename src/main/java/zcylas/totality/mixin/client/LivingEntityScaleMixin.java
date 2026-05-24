package zcylas.totality.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.ClientAncestryManager;

@Mixin(LivingEntity.class)
public class LivingEntityScaleMixin {

    @ModifyReturnValue(method = "getScale", at = @At("RETURN"))
    private float totality$scaleForAncestry(float original) {
        if (!((LivingEntity)(Object)this instanceof Player player)) return original;

        float scale;
        if (player.level().isClientSide()) {
            if (!player.equals(net.minecraft.client.Minecraft.getInstance().player)) return original;
            scale = ClientAncestryManager.getHeightScale();
        } else {
            if (!(player instanceof ServerPlayer serverPlayer)) return original;
            scale = AncestryComponents.get(serverPlayer).getHeightScale();
        }

        return scale != 1.0f ? original * scale : original;
    }
}