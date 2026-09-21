package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.mining.HarvestGrant;
import zcylas.totality.api.mining.PlayerMiningManager;

/**
 * Server authority over mining, in two independent parts:
 * <ol>
 *   <li>For Survival players the vanilla continuous-destroy packets (START/STOP_DESTROY_BLOCK) are refused on
 *       blocks Totality mining owns, so a client that never heard of Totality mining cannot mine the vanilla
 *       way. Creative, Adventure, Spectator, instant-break/unbreakable blocks and PIERCING_WEAPON items are
 *       untouched. ABORT is left alone (it only clears state).</li>
 *   <li>The exact-position {@link HarvestGrant} bridge: inside {@code destroyBlock}, where the break position is
 *       known, a Tier-qualified bare-hand Totality break may be told it is harvest-eligible.</li>
 * </ol>
 */
@Mixin(ServerPlayerGameMode.class)
public class ServerPlayerGameModeMixin {

    @Final @Shadow protected ServerPlayer player;
    @Shadow protected ServerLevel level;

    @Inject(method = "handleBlockBreakAction", at = @At("HEAD"), cancellable = true)
    private void totality$refuseVanillaSurvivalMining(BlockPos pos, ServerboundPlayerActionPacket.Action action,
                                                      Direction direction, int maxY, int sequence, CallbackInfo ci) {
        if (action != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK
                && action != ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) return;
        if (!PlayerMiningManager.ownsMining(player, level, pos)) return;
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        ci.cancel();
    }

    /** Wraps ONLY the eligibility query made by {@code destroyBlock} itself, handing it {@code destroyBlock}'s own position. */
    @WrapOperation(method = "destroyBlock", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;hasCorrectToolForDrops(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean totality$exactPositionHarvestGrant(ServerPlayer who, BlockState state, Operation<Boolean> original,
                                                       @Local(argsOnly = true) BlockPos pos) {
        return HarvestGrant.grants(who, pos, state) || original.call(who, state);
    }
}
