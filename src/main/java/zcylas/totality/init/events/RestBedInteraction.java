package zcylas.totality.init.events;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.BedBlock;
import zcylas.totality.api.rpg.rest.RestManager;
import zcylas.totality.networking.rest.OpenRestChoicePayload;

/**
 * Intercepts vanilla bed right-clicks — vanilla's instant "you are now sleeping"
 * never fires on its own; this always opens the Short/Long/Cancel Rest popup instead
 * (the same popup the Rest ability opens, just with a bed position attached).
 */
public final class RestBedInteraction {

    public static void register() {
        UseBlockCallback.EVENT.register((player, level, hand, hitResult) -> {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (!(level.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock)) {
                return InteractionResult.PASS;
            }
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (player instanceof ServerPlayer serverPlayer) {
                ServerPlayNetworking.send(serverPlayer, new OpenRestChoicePayload(
                        hitResult.getBlockPos(), RestManager.getShortRestRemaining(serverPlayer)));
            }
            return InteractionResult.SUCCESS;
        });
    }

    private RestBedInteraction() {}
}
