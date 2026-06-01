// networking/dice/DiceRollClickHandler.java
package zcylas.totality.networking.dice;

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
    }

    private DiceRollClickHandler() {}
}