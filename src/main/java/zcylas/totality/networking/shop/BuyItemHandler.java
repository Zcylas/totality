package zcylas.totality.networking.shop;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.shop.TradeSessionManager;

public final class BuyItemHandler {

    private BuyItemHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                BuyItemPayload.TYPE,
                (payload, context) -> context.server().execute(
                        () -> TradeSessionManager.handleBuy(context.player(), payload.index(), payload.quantity())
                )
        );
    }
}
