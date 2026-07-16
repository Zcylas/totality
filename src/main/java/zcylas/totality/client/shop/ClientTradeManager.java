package zcylas.totality.client.shop;

import net.minecraft.client.Minecraft;
import zcylas.totality.networking.shop.SellQuoteResultPayload;
import zcylas.totality.networking.shop.ShowShopStatePayload;
import zcylas.totality.networking.shop.TradeRejectionPayload;
import zcylas.totality.screen.shop.TradingScreen;

public final class ClientTradeManager {

    private ClientTradeManager() {}

    public static void handle(ShowShopStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();

        if (payload.ended()) {
            // Part F: if the SERVER ends the session (NPC invalidated, etc.) while the screen is
            // open, the client must close/disable it rather than silently keep showing stale data.
            if (mc.gui.screen() instanceof TradingScreen) mc.gui.setScreen(null);
            return;
        }

        if (mc.gui.screen() instanceof TradingScreen ts) {
            ts.applyUpdate(payload);
        } else {
            mc.gui.setScreen(new TradingScreen(payload));
        }
    }

    public static void handleSellQuote(SellQuoteResultPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TradingScreen ts) {
            ts.applySellQuote(payload);
        }
    }

    public static void handleRejection(TradeRejectionPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof TradingScreen ts) {
            ts.showRejection(payload.buy(), payload.reasonKey());
        }
    }
}
