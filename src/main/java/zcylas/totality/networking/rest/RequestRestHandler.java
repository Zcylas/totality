package zcylas.totality.networking.rest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.rest.RestDurations;
import zcylas.totality.api.rpg.rest.RestManager;
import zcylas.totality.api.rpg.rest.RestSessionManager;
import zcylas.totality.api.rpg.rest.RestType;
import zcylas.totality.api.rpg.rest.ShortRestLength;
import zcylas.totality.networking.notification.SendNotificationPayload;

/** Server-side handler for a confirmed Short/Long Rest choice. */
public final class RequestRestHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(RequestRestPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> handle(player, payload));
        });
    }

    private static void handle(ServerPlayer player, RequestRestPayload payload) {
        if (payload.restType() == RestType.SHORT && !RestManager.hasShortRestAvailable(player)) {
            // Authoritative re-check — the client already hides this option once capped,
            // but a stale popup (opened before the cap was hit) could still submit it.
            SendNotificationPayload.send(player,
                    "No Short Rests remaining — Long Rest to recover them.", SendNotificationPayload.RED);
            return;
        }

        BlockPos bedPos = payload.bedPos();
        boolean realVanillaSleep = false;

        if (payload.restType() == RestType.LONG && bedPos != null) {
            var result = player.startSleepInBed(bedPos);
            // If real vanilla sleep refuses (daytime, monsters nearby, etc.) we keep bedPos —
            // RestSessionManager still fake-lies the player into the same bed cosmetically
            // instead of discarding the anchor and leaving them standing next to it.
            realVanillaSleep = result.left().isEmpty();
        }

        int totalTicks = payload.restType() == RestType.SHORT
                ? (payload.length() != null ? payload.length().getTicks() : ShortRestLength.ONE_HOUR.getTicks())
                : RestDurations.getLongRestTicks(player);

        RestSessionManager.start(player, payload.restType(), totalTicks, bedPos, payload.activity(), realVanillaSleep);
    }

    private RequestRestHandler() {}
}
