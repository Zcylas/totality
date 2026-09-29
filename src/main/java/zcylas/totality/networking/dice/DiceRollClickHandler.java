// networking/dice/DiceRollClickHandler.java
package zcylas.totality.networking.dice;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.dice.PendingDiceRollManager;

/** Server-side handler for C2S dice roll clicks. */
public final class DiceRollClickHandler {

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                DiceRollClickPayload.TYPE,
                (payload, ctx) -> {
                    var player = ctx.player();
                    ctx.server().execute(() ->
                            PendingDiceRollManager.resolve(player, payload.sessionId()));
                }
        );
        // A player who leaves with a check still pending never rolls it: drop it (its callback never fires).
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PendingDiceRollManager.cancelAll(handler.getPlayer()));
    }

    private DiceRollClickHandler() {}
}