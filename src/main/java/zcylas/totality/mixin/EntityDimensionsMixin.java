package zcylas.totality.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import zcylas.totality.api.rpg.ancestry.AncestryComponents;
import zcylas.totality.api.rpg.ancestry.ClientAncestryManager;

@Mixin(Entity.class)
public class EntityDimensionsMixin {

    @ModifyReturnValue(method = "getDimensions", at = @At("RETURN"))
    private EntityDimensions totality$scaleDimensionsForAncestry(EntityDimensions original) {
        if (!((Entity)(Object)this instanceof Player player)) return original;

        float scale;
        if (player.level().isClientSide()) {
            if (!player.equals(net.minecraft.client.Minecraft.getInstance().player)) return original;
            scale = ClientAncestryManager.getHeightScale();
        } else {
            if (!(player instanceof ServerPlayer serverPlayer)) return original;
            scale = AncestryComponents.get(serverPlayer).getHeightScale();
        }

        return scale != 1.0f ? original.scale(scale) : original;
    }
}