package zcylas.totality.networking.rest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.rest.RestSessionManager;

/** Server-side handler for the popup's "Cancel Rest" option while a session is active. */
public final class CancelRestHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(CancelRestPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> RestSessionManager.cancel(player));
        });
    }

    private CancelRestHandler() {}
}
