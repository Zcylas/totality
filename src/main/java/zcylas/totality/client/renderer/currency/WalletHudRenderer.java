package zcylas.totality.client.renderer.currency;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.currency.CurrencyHelper;
import zcylas.totality.networking.currency.ClientWalletManager;

public final class WalletHudRenderer {

    public static final Identifier WALLET_HUD_ID =
            Identifier.fromNamespaceAndPath(Totality.MOD_ID, "wallet_hud");

    private WalletHudRenderer() {}

    public static void register() {
        HudElementRegistry.addLast(WALLET_HUD_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || client.options.hideGui) return;
            if (!(client.screen instanceof InventoryScreen)) return;

            long value = ClientWalletManager.getValue();
            if (value == 0) return;

            var breakdown = CurrencyHelper.breakdown(value);
            if (breakdown.isEmpty()) return;

            int lineHeight = 10;
            for (int i = 0; i < breakdown.size(); i++) {
                CurrencyHelper.CoinCount cc = breakdown.get(i);
                String text = cc.count() + " " + cc.denomination().displayName;
                int x = context.guiWidth() - 4 - client.font.width(text);
                int y = 4 + i * lineHeight;
                context.text(client.font, text, x, y, cc.denomination().color | 0xFF000000, true);
            }
        });
    }
}