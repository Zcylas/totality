package zcylas.totality.networking.shop;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.shop.TradeSessionManager;

public final class CloseTradeHandler {

    private CloseTradeHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                CloseTradePayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> TradeSessionManager.endTrade(context.player())
                )
        );
    }
}
