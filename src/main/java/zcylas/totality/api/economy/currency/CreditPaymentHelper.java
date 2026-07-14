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
        if (amount < 0) return false;
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
        if (amount < 0) return false;
        return physicalCredits(player) >= amount;
    }

    /** Attempts to pay {@code amount} using physical Credits items only. Returns false
     *  (no state changed) if unaffordable. */
    public static boolean payPhysical(ServerPlayer player, long amount) {
        if (!canAffordPhysical(player, amount)) return false;
        shrinkPhysicalCredits(player, amount);
        return true;
    }

    /**
     * Credits {@code amount} straight to the player's account (Wallet) balance — the same
     * mechanism {@code BankTellerHandler.deposit} already uses to give a player Credits it
     * didn't take from anywhere else in the same operation (as opposed to {@link #pay}, which
     * SPENDS the player's existing funds). This is the merchant-pays-player path for a SELL
     * transaction: the merchant's balance is decremented separately by the caller.
     *
     * @return true if the payment was applied; false (no state changed) if {@code amount} is
     *         negative or would overflow the player's Wallet balance.
     */
    public static boolean receive(ServerPlayer player, long amount) {
        if (!canReceive(player, amount)) return false;
        if (amount == 0) return true;
        CurrencyComponents.WALLET.get((ComponentProvider) player).modify(amount);
        return true;
    }

    /** True if {@link #receive} would succeed for {@code amount} right now — negative amounts
     *  are always rejected, and zero is always accepted as a trivial no-op. */
    public static boolean canReceive(ServerPlayer player, long amount) {
        if (amount < 0) return false;
        if (amount == 0) return true;
        long current = CurrencyComponents.WALLET.get((ComponentProvider) player).getValue();
        return current <= Long.MAX_VALUE - amount;
    }

    /** Sums every physical {@code totality:credits} stack in the player's inventory. Saturates at
     *  {@link Long#MAX_VALUE} instead of wrapping negative — several large stacks summing past
     *  that ceiling must never flip the total into an apparently-negative balance. A stored
     *  amount that is zero or negative (a malformed/corrupted stack — {@link CreditsItem#setAmount}
     *  itself does not validate its input) is ignored outright rather than added, so it can never
     *  reduce or underflow the calculated total. */
    public static long physicalCredits(ServerPlayer player) {
        long total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof CreditsItem) {
                long amount = CreditsItem.getAmount(stack);
                if (amount <= 0) continue;
                total = total > Long.MAX_VALUE - amount ? Long.MAX_VALUE : total + amount;
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
            if (stackAmount <= 0) {
                continue;
            }
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
