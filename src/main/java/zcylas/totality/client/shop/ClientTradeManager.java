package zcylas.totality.client.shop;

import net.minecraft.client.Minecraft;
import zcylas.totality.networking.shop.ShowShopStatePayload;
import zcylas.totality.screen.shop.TradingScreen;

public final class ClientTradeManager {

    private ClientTradeManager() {}

    public static void handle(ShowShopStatePayload payload) {
        Minecraft mc = Minecraft.getInstance();

        if (payload.ended()) {
            if (mc.screen instanceof TradingScreen) mc.setScreen(null);
            return;
        }

        if (mc.screen instanceof TradingScreen ts) {
            ts.applyUpdate(payload);
        } else {
            mc.setScreen(new TradingScreen(payload));
        }
    }
}
