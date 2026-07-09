package zcylas.totality.api.economy.currency;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.item.tools.CreditsItem;

/**
 * Pays a Credits amount: Wallet (account) balance first, then physical
 * {@code totality:credits} items in the player's inventory for any remainder.
 * Checks total affordability before mutating anything, so a failed payment
 * never partially deducts.
 */
public final class CreditPaymentHelper {

    private CreditPaymentHelper() {}

    public static boolean canAfford(ServerPlayer player, long amount) {
        WalletComponent wallet = CurrencyComponents.WALLET.get((ComponentProvider) player);
        long remaining = Math.max(0, amount - wallet.getValue());
        return remaining <= physicalCredits(player);
    }

    /** Attempts to pay {@code amount}. Returns false (no state changed) if unaffordable. */
    public static boolean pay(ServerPlayer player, long amount) {
        if (!canAfford(player, amount)) return false;

        WalletComponent wallet = CurrencyComponents.WALLET.get((ComponentProvider) player);
        long fromWallet = Math.min(wallet.getValue(), amount);
        long remaining = amount - fromWallet;

        if (fromWallet > 0) wallet.modify(-fromWallet);
        if (remaining > 0) shrinkPhysicalCredits(player, remaining);
        return true;
    }

    /** Physical-only affordability check — deliberately ignores the Wallet/account balance.
     *  Used for costs that must be paid before an account exists (e.g. the Banker's
     *  account-opening fee), where checking the account would be circular. */
    public static boolean canAffordPhysical(ServerPlayer player, long amount) {
        return physicalCredits(player) >= amount;
    }

    /** Attempts to pay {@code amount} using physical Credits items only. Returns false
     *  (no state changed) if unaffordable. */
    public static boolean payPhysical(ServerPlayer player, long amount) {
        if (!canAffordPhysical(player, amount)) return false;
        shrinkPhysicalCredits(player, amount);
        return true;
    }

    public static long physicalCredits(ServerPlayer player) {
        long total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof CreditsItem) {
                total += CreditsItem.getAmount(stack);
            }
        }
        return total;
    }

    private static void shrinkPhysicalCredits(ServerPlayer player, long amount) {
        long remaining = amount;
        for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!(stack.getItem() instanceof CreditsItem)) continue;

            long stackAmount = CreditsItem.getAmount(stack);
            long take = Math.min(stackAmount, remaining);
            long left = stackAmount - take;
            if (left <= 0) {
                player.getInventory().removeItem(i, stack.getCount());
            } else {
                CreditsItem.setAmount(stack, left);
            }
            remaining -= take;
        }
    }
}
