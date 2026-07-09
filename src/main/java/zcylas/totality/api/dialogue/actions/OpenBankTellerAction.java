package zcylas.totality.api.dialogue.actions;

import com.mojang.serialization.MapCodec;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.dialogue.DialogueAction;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.networking.economy.ShowBankTellerPayload;

/** Opens the Banker Teller screen client-side, seeded with the player's current Wallet
 *  balance and physical Credits on hand. Pairs with a silent (empty-text) end state so
 *  the dialogue closes without replacing the screen this action just opened. */
public record OpenBankTellerAction() implements DialogueAction {
    public static final MapCodec<OpenBankTellerAction> MAP_CODEC = MapCodec.unit(new OpenBankTellerAction());

    @Override
    public void execute(ServerPlayer player) {
        long wallet = CurrencyComponents.WALLET.get((ComponentProvider) player).getValue();
        long physical = CreditPaymentHelper.physicalCredits(player);
        ServerPlayNetworking.send(player, new ShowBankTellerPayload(wallet, physical));
    }

    @Override
    public String type() { return "open_bank_teller"; }
}
