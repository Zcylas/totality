package zcylas.totality.networking.economy;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.economy.currency.WalletComponent;
import zcylas.totality.init.items.CurrencyItems;

import java.util.List;

/**
 * Server-side deposit/withdraw at the Banker's teller — moves value between physical
 * {@code totality:credits} items and the account (Wallet) balance. Not a stateful session
 * like {@link zcylas.totality.api.shop.TradeSessionManager} — the teller has no catalog or
 * NPC-specific state, so each request is validated and applied independently, then the
 * fresh balances are echoed back to refresh the screen.
 */
public final class BankTellerHandler {

    private BankTellerHandler() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(
                DepositCreditsPayload.TYPE,
                (payload, context) -> context.server().execute(() -> deposit(context.player(), payload.amount()))
        );
        ServerPlayNetworking.registerGlobalReceiver(
                WithdrawCreditsPayload.TYPE,
                (payload, context) -> context.server().execute(() -> withdraw(context.player(), payload.amount()))
        );
    }

    private static void deposit(ServerPlayer player, long amount) {
        if (amount > 0 && CreditPaymentHelper.payPhysical(player, amount)) {
            CurrencyComponents.WALLET.get((ComponentProvider) player).modify(amount);
        }
        sendState(player);
    }

    private static void withdraw(ServerPlayer player, long amount) {
        if (amount <= 0) { sendState(player); return; }

        WalletComponent wallet = CurrencyComponents.WALLET.get((ComponentProvider) player);
        if (!wallet.trySpend(amount)) { sendState(player); return; }

        List<ItemStack> stacks = CurrencyItems.CREDITS.createStacks(amount);
        for (ItemStack stack : stacks) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        sendState(player);
    }

    private static void sendState(ServerPlayer player) {
        long wallet = CurrencyComponents.WALLET.get((ComponentProvider) player).getValue();
        long physical = CreditPaymentHelper.physicalCredits(player);
        ServerPlayNetworking.send(player, new ShowBankTellerPayload(wallet, physical));
    }
}
