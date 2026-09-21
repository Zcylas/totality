package zcylas.totality.api.mining;

import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * The checks vanilla performs when a player STARTS destroying a block (range, build height, spawn
 * protection, mayInteract, blockActionRestricted) plus Fabric's {@link AttackBlockCallback} — the
 * hook protection/claim mods use. Vanilla's START path is disabled for Totality mining, so these
 * must run at every impact instead. There is deliberately no "force" variant.
 */
public final class MiningPermissions {

    private MiningPermissions() {}

    public static boolean mayStrike(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        if (!player.isWithinBlockInteractionRange(pos, 1.0)) return false;
        if (pos.getY() > level.getMaxY()) return deny(player, level, pos);
        if (level.getServer().isUnderSpawnProtection(level, pos, player)) {
            player.sendSpawnProtectionMessage(pos);
            return false;
        }
        if (!level.mayInteract(player, pos)) return deny(player, level, pos);
        if (player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())) return deny(player, level, pos);
        InteractionResult result = AttackBlockCallback.EVENT.invoker().interact(player, level, InteractionHand.MAIN_HAND, pos, face);
        if (result != InteractionResult.PASS) return deny(player, level, pos);
        return true;
    }

    private static boolean deny(ServerPlayer player, ServerLevel level, BlockPos pos) {
        player.connection.send(new ClientboundBlockUpdatePacket(level, pos));
        return false;
    }
}
