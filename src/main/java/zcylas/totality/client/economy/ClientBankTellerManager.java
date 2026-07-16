package zcylas.totality.client.economy;

import net.minecraft.client.Minecraft;
import zcylas.totality.networking.economy.ShowBankTellerPayload;
import zcylas.totality.screen.economy.BankTellerScreen;

public final class ClientBankTellerManager {

    private ClientBankTellerManager() {}

    public static void handle(ShowBankTellerPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof BankTellerScreen screen) {
            screen.applyUpdate(payload);
        } else {
            mc.gui.setScreen(new BankTellerScreen(payload));
        }
    }
}
