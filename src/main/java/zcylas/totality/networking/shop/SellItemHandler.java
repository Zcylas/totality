package zcylas.totality.networking.shop;

import com.mojang.logging.LogUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import org.slf4j.Logger;
import zcylas.totality.api.shop.SellResult;
import zcylas.totality.api.shop.TradeRejectionKeys;
import zcylas.totality.api.shop.TradeSessionManager;

public final class SellItemHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

    private SellItemHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                SellItemPayload.TYPE,
                (payload, context) -> context.server().execute(() -> {
                    SellResult result = TradeSessionManager.handleSell(context.player(), payload.slotIndex(), payload.quantity(),
                            payload.confirmedReducedPayout(), payload.confirmedTotalValue(), payload.confirmedPayableAmount());
                    // Ordinary rejections (not accepted, no value, merchant has no Credits, a
                    // stack that changed since the client's last quote, confirmation required/
                    // stale) are normal gameplay outcomes, not worth logging. An out-of-range
                    // slot or a non-positive quantity is something the real SELL UI can never send.
                    if (!result.success()
                            && (result.reason() == SellResult.Reason.INVALID_SLOT || result.reason() == SellResult.Reason.INVALID_QUANTITY)) {
                        LOGGER.warn("Malformed SELL packet from {}: slot={}, quantity={}, reason={}",
                                context.player().getName().getString(), payload.slotIndex(), payload.quantity(), result.reason());
                    }
                    // Phase 4, Part D: give the player a real, visible reason instead of nothing
                    // happening — success is already fully communicated by the state refresh.
                    if (!result.success()) {
                        ServerPlayNetworking.send(context.player(), new TradeRejectionPayload(
                                false, TradeRejectionKeys.forSell(result.reason(), result.quoteReason())));
                    }
                })
        );
    }
}
