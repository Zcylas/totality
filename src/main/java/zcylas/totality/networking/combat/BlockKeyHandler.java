package zcylas.totality.networking.combat;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ShieldItem;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockKeyHandler {

    private static final Set<UUID> BLOCKING = ConcurrentHashMap.newKeySet();

    public static boolean isBlocking(UUID playerId) {
        return BLOCKING.contains(playerId);
    }

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(BlockKeyPayload.TYPE,
                (payload, ctx) -> ctx.server().execute(() -> handle(ctx.player(), payload)));

        // Re-apply startUsingItem every tick while blocking, in case it was cancelled server-side
        ServerTickEvents.END_SERVER_TICK.register(BlockKeyHandler::tick);

        // Clean up on disconnect to avoid phantom blocking on reconnect
        ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> BLOCKING.remove(handler.getPlayer().getUUID()));
    }

    private static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (BLOCKING.contains(player.getUUID()) && !player.isUsingItem()) {
                applyBlock(player);
            }
        }
    }

    private static void handle(ServerPlayer player, BlockKeyPayload payload) {
        if (payload.blocking()) {
            BLOCKING.add(player.getUUID());
            applyBlock(player);
        } else {
            BLOCKING.remove(player.getUUID());
            player.stopUsingItem();
        }
    }

    private static void applyBlock(ServerPlayer player) {
        if (player.getOffhandItem().getItem() instanceof ShieldItem) {
            player.startUsingItem(InteractionHand.OFF_HAND);
        } else {
            var main = player.getMainHandItem();
            if (main.is(ItemTags.SWORDS) || main.getItem() instanceof TotalityMeleeWeaponItem) {
                player.startUsingItem(InteractionHand.MAIN_HAND);
            }
        }
    }

    private BlockKeyHandler() {}
}
