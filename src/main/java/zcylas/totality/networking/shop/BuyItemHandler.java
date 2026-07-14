package zcylas.totality.networking.shop;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import zcylas.totality.api.shop.BuyResult;
import zcylas.totality.api.shop.TradeRejectionKeys;
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
                    // out of range, a corrupt merchant balance) are normal gameplay/data outcomes,
                    // not worth logging. An out-of-range catalog index, an out-of-range quantity,
                    // or an overflow attempt is something the real BUY UI (which already clamps
                    // quantity client-side) can never produce on its own.
                    if (!result.success()
                            && (result.reason() == BuyResult.Reason.INVALID_INDEX
                                    || result.reason() == BuyResult.Reason.INVALID_QUANTITY
                                    || result.reason() == BuyResult.Reason.OVERFLOW)) {
                        LOGGER.warn("Malformed BUY packet from {}: index={}, quantity={}, reason={}",
                                context.player().getName().getString(), payload.index(), payload.quantity(), result.reason());
                    }
                    // Phase 4, Part D: a failed BUY previously produced NO client-visible feedback
                    // at all — the player just saw nothing happen. Success is already fully
                    // communicated by the state refresh TradeSessionManager sends itself.
                    if (!result.success()) {
                        ServerPlayNetworking.send(context.player(), new TradeRejectionPayload(
                                true, TradeRejectionKeys.forBuy(result.reason())));
                    }
                })
        );
    }
}
