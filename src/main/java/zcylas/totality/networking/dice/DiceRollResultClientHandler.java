// networking/dice/DiceRollResultClientHandler.java
package zcylas.totality.networking.dice;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import zcylas.totality.screen.dice.DiceRollScreen;

/** Client-side handler for S2C dice roll results. Forwards to the open DiceRollScreen. */
public final class DiceRollResultClientHandler {

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(
                DiceRollResultPayload.TYPE,
                (payload, ctx) -> {
                    ctx.client().execute(() -> {
                        if (Minecraft.getInstance().screen instanceof DiceRollScreen screen) {
                            screen.receiveResult(payload.result());
                        }
                    });
                }
        );
    }

    public static void registerRequest() {
        ClientPlayNetworking.registerGlobalReceiver(
                DiceCheckRequestPayload.TYPE,
                (payload, ctx) -> {
                    ctx.client().execute(() ->
                            Minecraft.getInstance().setScreen(
                                    new DiceRollScreen(payload.sessionId(), payload.context())
                            )
                    );
                }
        );
    }

    private DiceRollResultClientHandler() {}
}