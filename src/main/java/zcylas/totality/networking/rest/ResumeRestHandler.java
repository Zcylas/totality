package zcylas.totality.networking.rest;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.rpg.rest.RestSessionManager;

/** Server-side handler for the popup's "Keep Resting" option during the post-interrupt grace window. */
public final class ResumeRestHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(ResumeRestPayload.TYPE, (payload, ctx) -> {
            ServerPlayer player = ctx.player();
            ctx.server().execute(() -> RestSessionManager.resume(player));
        });
    }

    private ResumeRestHandler() {}
}
