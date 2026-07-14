package zcylas.totality.networking.shop;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import zcylas.totality.api.shop.TradeSessionManager;

public final class RequestSellQuoteHandler {

    private RequestSellQuoteHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                RequestSellQuotePayload.TYPE,
                (payload, context) -> context.server().execute(() ->
                        TradeSessionManager.requestSellQuote(context.player(), payload.slotIndex()))
        );
    }
}
