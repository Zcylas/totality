package zcylas.totality.networking.shop;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import zcylas.totality.api.shop.BuyResult;
import zcylas.totality.api.shop.TradeSessionManager;

public final class BuyItemHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private BuyItemHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                BuyItemPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    BuyResult result = TradeSessionManager.handleBuy(context.player(), payload.index(), payload.quantity());
                    // Ordinary rejections (can't afford it, stale session, an NPC that wandered
                    // out of range) are normal gameplay outcomes, not worth logging. An
                    // out-of-range catalog index or an overflow attempt is something the real BUY
                    // UI can never produce on its own.
                    if (!result.success()
                            && (result.reason() == BuyResult.Reason.INVALID_INDEX || result.reason() == BuyResult.Reason.OVERFLOW)) {
                        LOGGER.warn("Malformed BUY packet from {}: index={}, quantity={}, reason={}",
                                context.player().getName().getString(), payload.index(), payload.quantity(), result.reason());
                    }
                })
        );
    }
}
