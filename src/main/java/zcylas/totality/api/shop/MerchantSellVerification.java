package zcylas.totality.api.shop;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import zcylas.totality.Totality;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.dialogue.DialogueComponents;
import zcylas.totality.api.economy.currency.CreditPaymentHelper;
import zcylas.totality.api.economy.currency.CurrencyComponents;
import zcylas.totality.api.economy.value.ItemPricingService;
import zcylas.totality.api.economy.value.PriceQuote;
import zcylas.totality.api.economy.value.PricingContext;
import zcylas.totality.api.economy.value.PricingDirection;
import zcylas.totality.entity.npc.TotalityNpcEntity;
import zcylas.totality.init.ModEntities;
import zcylas.totality.init.ModTags;
import zcylas.totality.init.items.CurrencyItems;
import zcylas.totality.item.tools.CreditsItem;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for the merchant runtime / SELL foundation and the subsequent
 * economy hardening pass, run once at {@code SERVER_STARTED} — same convention as
 * {@code ItemValueVerification} (no JUnit infrastructure exists in this project; see that
 * class's Javadoc for why).
 *
 * <p>Checks 1-14 exercise {@link MerchantSellQuoteView}/{@link ItemPricingService} directly
 * against isolated, hand-built {@link InMemoryMerchantRuntime} instances — no trade session
 * needed. Checks 15-32 drive the REAL {@link TradeSessionManager} session/commit path, but
 * exclusively through {@link TradeSessionManager#startTradeForVerification}, which accepts an
 * explicitly supplied, isolated {@link ShopTemplate} and {@link MerchantRuntime} — this suite
 * never calls {@link ShopRegistry} or {@link MerchantRuntimeRegistry}, so it can never create or
 * mutate any production shop/merchant state (economy hardening pass, Part 2). Every session
 * check cleans up its session/inventory/wallet mutation in a {@code finally} block, even if the
 * check body itself throws.
 */
public final class MerchantSellVerification {

    /** Referenced ONLY by {@link #checkVerificationNeverTouchesProductionRuntime} to prove this
     *  suite never created a production entry for the real shop under this id — every actual
     *  session check below uses its own isolated {@link #VERIFICATION_SHOP_ID} fixture instead. */
    private static final Identifier TEST_TRADER_SHOP_ID = Identifier.fromNamespaceAndPath("totality", "test_trader");

    private static final Identifier VERIFICATION_SHOP_ID = Identifier.fromNamespaceAndPath("totality", "selftest/shop");

    private MerchantSellVerification() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(MerchantSellVerification::runSelfTestIfDev);
    }

    static void runSelfTestIfDev(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "MerchantSellVerification");
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), "[MerchantSellVerification]");
        // Established account holder by default — matches every pre-existing SELL/Wallet check
        // below, which already assumed a SELL payout reaches the Wallet. The Part A checks flip
        // this flag off temporarily (and restore it) to exercise the no-account physical-payout
        // path (correction pass, Part A).
        setHasAccount(player, true);

        checkAcceptedValuedItemSellable(r, player);
        checkValuedItemOutsideAcceptedTagsRejected(r, player);
        checkAcceptedTagItemWithNoValueRejected(r, player);
        checkToolRejectedByPolicy(r, player);
        checkPotionRejectedByPolicy(r, player);
        checkBaseValue12ProducesPayout6(r, player);
        checkOddBaseValueFloors(r);
        checkMinimumPositivePayout(r);
        checkZeroBaseValuePayoutZero(r);
        checkQuantityMultiplication(r, player);
        checkQuantityOverflowRejected(r, player);
        checkMerchantWithExactCreditsCanSell(r, player);
        checkMerchantWithInsufficientCreditsRequiresConfirmation(r, player);
        checkMerchantWithZeroCreditsRejectsOutright(r, player);
        checkSellQuantityNotCappedByMerchantAffordability(r, player);
        checkNegativeBaseValueRejectedBySellUnitPayout(r);

        checkSuccessfulSellRemovesExactQuantity(r, player);
        checkSuccessfulSellPaysExactAmount(r, player);
        checkSuccessfulSellDecreasesMerchantCreditsExactly(r, player);
        checkFailedSellChangesNothing(r, player);
        checkSuccessfulBuyIncreasesMerchantCredits(r, player);
        checkFailedBuyDoesNotChangeMerchantCredits(r, player);
        checkInvalidSessionSlotQuantityStaleStackRejected(r, player);
        checkSellingPartOfStackLeavesRemainder(r, player);
        checkExtraIrrelevantComponentsStillUseCorrectBaseValue(r, player);

        checkUnderfundedSellWithoutConfirmationRejected(r, player);
        checkUnderfundedSellWithConfirmationCompletesAtomically(r, player);
        checkStaleConfirmationRejected(r, player);
        checkZeroCreditMerchantCannotSell(r, player);

        checkAccountlessSellPaysPhysicalCreditsNotWallet(r, player);
        checkAccountlessSellDoesNotOpenAccount(r, player);
        checkReceivePhysicalRejectsNegativeAmount(r, player);
        checkReceivePhysicalHandlesLargeAmountSafely(r, player);

        checkNegativePaymentRejectedWithoutMutation(r, player);
        checkNegativePhysicalPaymentRejectedWithoutMutation(r, player);
        checkMalformedNegativePhysicalCreditsStackIgnoredNotSubtracted(r, player);
        checkShrinkPhysicalCreditsIgnoresMalformedNegativeStack(r, player);
        checkBuyOverflowRejectedWithoutMutation(r, player);
        checkNegativeShopPriceCannotCompleteBuy(r, player);

        checkDiscardedNpcEndsSessionAndBlocksFurtherTransaction(r, player, server);
        checkOutOfRangeNpcEntityBackedSessionRejectedByRevalidation(r, player, server);
        checkReusedEntityIdWithWrongUuidLockNotReleased(r, player, server);

        // Runs last so it observes the cumulative effect of every check above.
        checkVerificationNeverTouchesProductionRuntime(r, player, server);

        r.summarize();
    }

    // ─────────────────────────────────────────────────────────────────────
    // Pure MerchantSellQuoteView / ItemPricingService checks — no session required
    // ─────────────────────────────────────────────────────────────────────

    private static void checkAcceptedValuedItemSellable(VerificationReporter r, ServerPlayer player) {
        safe(r, "Accepted item with a value is sellable", () -> {
            MerchantRuntime merchant = freshMerchant(300);
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.BREAD), 6, 1);
            return result(quote.sellable() && quote.accepted() && quote.hasValue(), "quote=" + quote);
        });
    }

    private static void checkValuedItemOutsideAcceptedTagsRejected(VerificationReporter r, ServerPlayer player) {
        safe(r, "Valued item outside accepted tags is rejected", () -> {
            MerchantRuntime merchant = freshMerchant(300);
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.IRON_SWORD), 1, 1);
            return result(!quote.sellable() && !quote.accepted() && quote.hasValue(), "quote=" + quote);
        });
    }

    private static void checkAcceptedTagItemWithNoValueRejected(VerificationReporter r, ServerPlayer player) {
        safe(r, "Accepted-tag item with no central value is rejected", () -> {
            MerchantRuntime merchant = freshMerchant(300);
            // Carrot is in #totality:provisioner_buys but has no item_values rule authored.
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.CARROT), 1, 1);
            return result(!quote.sellable() && quote.accepted() && !quote.hasValue(), "quote=" + quote);
        });
    }

    private static void checkToolRejectedByPolicy(VerificationReporter r, ServerPlayer player) {
        safe(r, "Tool is rejected by the Provisioner policy", () -> {
            MerchantRuntime merchant = freshMerchant(300);
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.STONE_AXE), 1, 1);
            return result(!quote.sellable() && !quote.accepted(), "quote=" + quote);
        });
    }

    private static void checkPotionRejectedByPolicy(VerificationReporter r, ServerPlayer player) {
        safe(r, "Potion is rejected by the Provisioner policy", () -> {
            MerchantRuntime merchant = freshMerchant(300);
            ItemStack healingPotion = PotionContents.createItemStack(Items.POTION, Potions.HEALING);
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, healingPotion, 1, 1);
            return result(!quote.sellable() && !quote.accepted() && quote.hasValue(), "quote=" + quote);
        });
    }

    private static void checkBaseValue12ProducesPayout6(VerificationReporter r, ServerPlayer player) {
        safe(r, "Base value 12 (Bread) produces unit SELL payout 6", () -> {
            PricingContext context = PricingContext.sell(player);
            Optional<PriceQuote> quote = ItemPricingService.quoteSell(new ItemStack(Items.BREAD), 1, context);
            return result(quote.isPresent() && quote.get().unitPrice() == 6L, "quote=" + quote);
        });
    }

    private static void checkOddBaseValueFloors(VerificationReporter r) {
        safe(r, "Odd base value 5 produces unit SELL payout 2 under floor division",
                () -> result(ItemPricingService.sellUnitPayout(5L) == 2L, "got " + ItemPricingService.sellUnitPayout(5L)));
    }

    private static void checkMinimumPositivePayout(VerificationReporter r) {
        safe(r, "Positive base value 1 produces minimum payout 1",
                () -> result(ItemPricingService.sellUnitPayout(1L) == 1L, "got " + ItemPricingService.sellUnitPayout(1L)));
    }

    private static void checkZeroBaseValuePayoutZero(VerificationReporter r) {
        safe(r, "Base value 0 produces payout 0",
                () -> result(ItemPricingService.sellUnitPayout(0L) == 0L, "got " + ItemPricingService.sellUnitPayout(0L)));
    }

    private static void checkQuantityMultiplication(VerificationReporter r, ServerPlayer player) {
        safe(r, "Quantity multiplication is correct (Bread x4 -> 24)", () -> {
            PricingContext context = PricingContext.sell(player);
            Optional<PriceQuote> quote = ItemPricingService.quoteSell(new ItemStack(Items.BREAD), 4, context);
            return result(quote.isPresent() && quote.get().unitPrice() == 6L && quote.get().total() == 24L, "quote=" + quote);
        });
    }

    private static void checkQuantityOverflowRejected(VerificationReporter r, ServerPlayer player) {
        safe(r, "Quantity overflow is rejected (ArithmeticException), not wrapped", () -> {
            PricingContext overflowContext = new PricingContext(
                    player, Optional.empty(), PricingDirection.SELL, Optional.of(Long.MAX_VALUE));
            boolean threw;
            try {
                ItemPricingService.quoteSell(new ItemStack(Items.BREAD), 2, overflowContext);
                threw = false;
            } catch (ArithmeticException expected) {
                threw = true;
            }
            return result(threw, "expected ArithmeticException, none thrown");
        });
    }

    private static void checkMerchantWithExactCreditsCanSell(VerificationReporter r, ServerPlayer player) {
        safe(r, "Merchant with exact Credits can buy the item, with nothing forfeited", () -> {
            MerchantRuntime merchant = freshMerchant(6); // exactly one Bread's payout
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.BREAD), 1, 1);
            boolean pass = quote.sellable() && quote.totalValue() == 6L && quote.payableAmount() == 6L
                    && quote.forfeitedValue() == 0L && !quote.requiresConfirmation();
            return result(pass, "quote=" + quote);
        });
    }

    /**
     * Phase 4 correction pass, Part A: a merchant that can't fully afford the requested value no
     * longer REJECTS the quote outright — it stays {@code sellable()} (quantity is bounded by the
     * stack/server cap only, never by affordability) and instead surfaces
     * {@code requiresConfirmation}/{@code payableAmount}/{@code forfeitedValue} for the Trading
     * Screen's underfunded confirmation popup. {@link TradeSessionManager#handleSell} itself is
     * what refuses to commit without explicit confirmation — exercised separately below.
     */
    private static void checkMerchantWithInsufficientCreditsRequiresConfirmation(VerificationReporter r, ServerPlayer player) {
        safe(r, "Merchant with insufficient (but nonzero) Credits stays sellable and requires confirmation", () -> {
            MerchantRuntime merchant = freshMerchant(5); // one short of Bread's payout of 6
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.BREAD), 1, 1);
            boolean pass = quote.sellable() && quote.totalValue() == 6L && quote.payableAmount() == 5L
                    && quote.forfeitedValue() == 1L && quote.requiresConfirmation();
            return result(pass, "quote=" + quote);
        });
    }

    private static void checkMerchantWithZeroCreditsRejectsOutright(VerificationReporter r, ServerPlayer player) {
        safe(r, "Merchant with exactly zero Credits rejects the sale outright, never requiring confirmation", () -> {
            MerchantRuntime merchant = freshMerchant(0);
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.BREAD), 1, 1);
            boolean pass = !quote.sellable() && !quote.requiresConfirmation()
                    && quote.rejectionReason() == SellRejectionReason.MERCHANT_ZERO_CREDITS;
            return result(pass, "quote=" + quote);
        });
    }

    private static void checkSellQuantityNotCappedByMerchantAffordability(VerificationReporter r, ServerPlayer player) {
        safe(r, "SELL quantity selection is bounded by stack count only, never by merchant affordability", () -> {
            MerchantRuntime merchant = freshMerchant(6); // affords only 1 of 4 Bread
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, new ItemStack(Items.BREAD, 4), 4, 4);
            boolean pass = quote.sellable() && quote.effectiveMaxQuantity() == 4
                    && quote.totalValue() == 24L && quote.payableAmount() == 6L && quote.forfeitedValue() == 18L
                    && quote.requiresConfirmation();
            return result(pass, "quote=" + quote);
        });
    }

    private static void checkNegativeBaseValueRejectedBySellUnitPayout(VerificationReporter r) {
        safe(r, "Negative base value is rejected by sellUnitPayout, not turned into a positive payout", () -> {
            boolean threw;
            try {
                ItemPricingService.sellUnitPayout(-4L);
                threw = false;
            } catch (IllegalArgumentException expected) {
                threw = true;
            }
            return result(threw, "expected IllegalArgumentException, none thrown");
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Real session/commit checks — drive TradeSessionManager against isolated fixtures only
    // ─────────────────────────────────────────────────────────────────────

    private static void checkSuccessfulSellRemovesExactQuantity(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Successful SELL removes the exact item quantity", player, standardShop(), 300,
                new ItemStack(Items.BREAD, 10), (merchant, slot) -> {
                    TradeSessionManager.handleSell(player, slot, 4);
                    ItemStack remaining = player.getInventory().getItem(slot);
                    boolean pass = remaining.getCount() == 6 && remaining.is(Items.BREAD);
                    return result(pass, "remaining=" + remaining);
                });
    }

    private static void checkSuccessfulSellPaysExactAmount(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Successful SELL pays the exact amount", player, standardShop(), 300,
                new ItemStack(Items.BREAD, 4), (merchant, slot) -> {
                    long before = wallet(player);
                    TradeSessionManager.handleSell(player, slot, 4);
                    long after = wallet(player);
                    boolean pass = after - before == 24L; // 4 * 6
                    return result(pass, "before=" + before + ", after=" + after);
                });
    }

    private static void checkSuccessfulSellDecreasesMerchantCreditsExactly(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Successful SELL decreases merchant Credits by the exact amount", player, standardShop(), 300,
                new ItemStack(Items.BREAD, 4), (merchant, slot) -> {
                    TradeSessionManager.handleSell(player, slot, 4);
                    boolean pass = merchant.currentCredits() == 300 - 24;
                    return result(pass, "currentCredits=" + merchant.currentCredits());
                });
    }

    private static void checkFailedSellChangesNothing(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Failed SELL changes neither inventory, player Credits, nor merchant Credits", player,
                standardShop(), 300, new ItemStack(Items.IRON_SWORD, 1), (merchant, slot) -> {
                    long walletBefore = wallet(player);
                    SellResult sellResult = TradeSessionManager.handleSell(player, slot, 1);
                    ItemStack after = player.getInventory().getItem(slot);
                    boolean pass = !sellResult.success() && sellResult.reason() == SellResult.Reason.NOT_SELLABLE
                            && after.getCount() == 1 && after.is(Items.IRON_SWORD)
                            && wallet(player) == walletBefore
                            && merchant.currentCredits() == 300;
                    return result(pass, "sellResult=" + sellResult + ", after=" + after
                            + ", merchantCredits=" + merchant.currentCredits());
                });
    }

    private static void checkSuccessfulBuyIncreasesMerchantCredits(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Successful BUY increases merchant Credits by the exact amount", player, standardShop(), 300,
                ItemStack.EMPTY, (merchant, slot) -> {
                    giveWallet(player, 10_000);
                    BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 1); // Iron Sword, price 450
                    boolean pass = buyResult.success() && merchant.currentCredits() == 300 + 450;
                    return result(pass, "buyResult=" + buyResult + ", currentCredits=" + merchant.currentCredits());
                });
    }

    private static void checkFailedBuyDoesNotChangeMerchantCredits(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Failed BUY does not change merchant Credits", player, standardShop(), 300,
                ItemStack.EMPTY, (merchant, slot) -> {
                    setWallet(player, 0); // can't afford anything -> BUY must fail
                    BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 1);
                    boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.CANNOT_AFFORD
                            && merchant.currentCredits() == 300;
                    return result(pass, "buyResult=" + buyResult + ", currentCredits=" + merchant.currentCredits());
                });
    }

    private static void checkInvalidSessionSlotQuantityStaleStackRejected(VerificationReporter r, ServerPlayer player) {
        safe(r, "Invalid session, slot, quantity, or stale/empty stack is rejected", () -> {
            int slot = 0;

            // No active session at all.
            player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 4));
            long walletBefore = wallet(player);
            SellResult noSessionResult = TradeSessionManager.handleSell(player, slot, 1);
            boolean noSessionSafe = noSessionResult.reason() == SellResult.Reason.NO_SESSION
                    && wallet(player) == walletBefore && player.getInventory().getItem(slot).getCount() == 4;

            MerchantRuntime merchant = freshMerchant(300);
            try {
                TradeSessionManager.startTradeForVerification(player, VERIFICATION_SHOP_ID, standardShop(), merchant);

                // Invalid (out-of-range) slot.
                SellResult invalidSlot = TradeSessionManager.handleSell(player, player.getInventory().getContainerSize() + 5, 1);
                boolean invalidSlotSafe = invalidSlot.reason() == SellResult.Reason.INVALID_SLOT
                        && wallet(player) == walletBefore && merchant.currentCredits() == 300;

                // Invalid (zero) quantity.
                SellResult invalidQuantity = TradeSessionManager.handleSell(player, slot, 0);
                boolean invalidQuantitySafe = invalidQuantity.reason() == SellResult.Reason.INVALID_QUANTITY
                        && wallet(player) == walletBefore
                        && player.getInventory().getItem(slot).getCount() == 4 && merchant.currentCredits() == 300;

                // Stale/empty slot (nothing there to sell).
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                SellResult emptyStack = TradeSessionManager.handleSell(player, slot, 1);
                boolean emptySlotSafe = emptyStack.reason() == SellResult.Reason.EMPTY_STACK
                        && wallet(player) == walletBefore && merchant.currentCredits() == 300;

                boolean pass = noSessionSafe && invalidSlotSafe && invalidQuantitySafe && emptySlotSafe;
                return result(pass, "noSessionSafe=" + noSessionSafe + ", invalidSlotSafe=" + invalidSlotSafe
                        + ", invalidQuantitySafe=" + invalidQuantitySafe + ", emptySlotSafe=" + emptySlotSafe);
            } finally {
                TradeSessionManager.endTrade(player);
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        });
    }

    private static void checkSellingPartOfStackLeavesRemainder(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Selling part of a stack leaves the correct remainder", player, standardShop(), 300,
                new ItemStack(Items.BREAD, 10), (merchant, slot) -> {
                    TradeSessionManager.handleSell(player, slot, 3);
                    ItemStack remaining = player.getInventory().getItem(slot);
                    boolean pass = remaining.is(Items.BREAD) && remaining.getCount() == 7;
                    return result(pass, "remaining=" + remaining);
                });
    }

    private static void checkExtraIrrelevantComponentsStillUseCorrectBaseValue(VerificationReporter r, ServerPlayer player) {
        safe(r, "Selling an item with extra irrelevant components still uses the correct base value", () -> {
            MerchantRuntime merchant = freshMerchant(300);
            ItemStack namedBread = new ItemStack(Items.BREAD);
            namedBread.set(DataComponents.CUSTOM_NAME, Component.literal("Fancy Bread"));
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, merchant, namedBread, 1, 1);
            return result(quote.sellable() && quote.unitPayout() == 6L, "quote=" + quote);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Phase 4 correction pass — underfunded-merchant SELL confirmation (Part A)
    // ─────────────────────────────────────────────────────────────────────

    private static void checkUnderfundedSellWithoutConfirmationRejected(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Underfunded SELL without confirmation is rejected and changes nothing", player, standardShop(), 100,
                new ItemStack(Items.BREAD, 20), (merchant, slot) -> {
                    long walletBefore = wallet(player);
                    SellResult sellResult = TradeSessionManager.handleSell(player, slot, 17); // 17*6=102 > 100
                    ItemStack after = player.getInventory().getItem(slot);
                    boolean pass = !sellResult.success() && sellResult.reason() == SellResult.Reason.CONFIRMATION_REQUIRED
                            && after.getCount() == 20
                            && wallet(player) == walletBefore
                            && merchant.currentCredits() == 100;
                    return result(pass, "sellResult=" + sellResult + ", after=" + after);
                });
    }

    private static void checkUnderfundedSellWithConfirmationCompletesAtomically(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "Underfunded SELL with matching confirmation completes atomically at the payable amount", player,
                standardShop(), 100, new ItemStack(Items.BREAD, 20), (merchant, slot) -> {
                    long walletBefore = wallet(player);
                    SellResult sellResult = TradeSessionManager.handleSell(player, slot, 17, true, 102L, 100L);
                    ItemStack after = player.getInventory().getItem(slot);
                    boolean pass = sellResult.success() && sellResult.payout() == 100L && sellResult.quantitySold() == 17
                            && after.getCount() == 3
                            && wallet(player) - walletBefore == 100L
                            && merchant.currentCredits() == 0L;
                    return result(pass, "sellResult=" + sellResult + ", after=" + after
                            + ", merchantCredits=" + merchant.currentCredits());
                });
    }

    private static void checkStaleConfirmationRejected(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "A confirmation whose terms no longer match the current quote is rejected as stale, changing nothing",
                player, standardShop(), 100, new ItemStack(Items.BREAD, 20), (merchant, slot) -> {
                    long walletBefore = wallet(player);
                    // Confirmed terms (102/100) no longer match — merchant Credits changed
                    // underneath the confirmation (simulating another transaction landing between
                    // popup-open and click, or a manipulated client).
                    merchant.setCurrentCredits(50);
                    SellResult sellResult = TradeSessionManager.handleSell(player, slot, 17, true, 102L, 100L);
                    ItemStack after = player.getInventory().getItem(slot);
                    boolean pass = !sellResult.success() && sellResult.reason() == SellResult.Reason.STALE_CONFIRMATION
                            && after.getCount() == 20
                            && wallet(player) == walletBefore
                            && merchant.currentCredits() == 50;
                    return result(pass, "sellResult=" + sellResult + ", after=" + after);
                });
    }

    private static void checkZeroCreditMerchantCannotSell(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "A merchant with zero Credits rejects SELL outright — no confirmation, no free disposal", player,
                standardShop(), 0, new ItemStack(Items.BREAD, 4), (merchant, slot) -> {
                    long walletBefore = wallet(player);
                    SellResult sellResult = TradeSessionManager.handleSell(player, slot, 4);
                    ItemStack after = player.getInventory().getItem(slot);
                    boolean pass = !sellResult.success() && sellResult.reason() == SellResult.Reason.NOT_SELLABLE
                            && sellResult.quoteReason() == SellRejectionReason.MERCHANT_ZERO_CREDITS
                            && after.getCount() == 4
                            && wallet(player) == walletBefore
                            && merchant.currentCredits() == 0;
                    return result(pass, "sellResult=" + sellResult + ", after=" + after);
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Correction pass — accountless SELL payout (Part A)
    // ─────────────────────────────────────────────────────────────────────

    private static void checkAccountlessSellPaysPhysicalCreditsNotWallet(VerificationReporter r, ServerPlayer player) {
        safe(r, "SELL for a player with no open bank account pays physical Credits, not the Wallet", () -> {
            int slot = 7;
            ItemStack slotBefore = player.getInventory().getItem(slot).copy();
            MerchantRuntime merchant = freshMerchant(300);
            long physicalBefore = physical(player);
            try {
                setHasAccount(player, false);
                player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 4));
                long walletBefore = wallet(player);

                TradeSessionManager.startTradeForVerification(player, VERIFICATION_SHOP_ID, standardShop(), merchant);
                SellResult sellResult = TradeSessionManager.handleSell(player, slot, 4);

                long walletAfter = wallet(player);
                long physicalAfter = physical(player);
                boolean pass = sellResult.success() && sellResult.payout() == 24L // 4 * 6
                        && walletAfter == walletBefore
                        && physicalAfter - physicalBefore == 24L
                        && merchant.currentCredits() == 300 - 24;
                return result(pass, "sellResult=" + sellResult + ", wallet=" + walletBefore + "->" + walletAfter
                        + ", physical=" + physicalBefore + "->" + physicalAfter
                        + ", merchantCredits=" + merchant.currentCredits());
            } finally {
                TradeSessionManager.endTrade(player);
                setHasAccount(player, true);
                player.getInventory().setItem(slot, slotBefore);
                restorePhysicalCredits(player, physicalBefore);
            }
        });
    }

    private static void checkAccountlessSellDoesNotOpenAccount(VerificationReporter r, ServerPlayer player) {
        safe(r, "A no-account SELL never implicitly sets the has_account flag", () -> {
            int slot = 7;
            ItemStack slotBefore = player.getInventory().getItem(slot).copy();
            MerchantRuntime merchant = freshMerchant(300);
            long physicalBefore = physical(player);
            try {
                setHasAccount(player, false);
                player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 2));

                TradeSessionManager.startTradeForVerification(player, VERIFICATION_SHOP_ID, standardShop(), merchant);
                SellResult sellResult = TradeSessionManager.handleSell(player, slot, 2);

                boolean pass = sellResult.success() && !CreditPaymentHelper.hasOpenAccount(player);
                return result(pass, "sellResult=" + sellResult
                        + ", hasOpenAccount=" + CreditPaymentHelper.hasOpenAccount(player));
            } finally {
                TradeSessionManager.endTrade(player);
                setHasAccount(player, true);
                player.getInventory().setItem(slot, slotBefore);
                restorePhysicalCredits(player, physicalBefore);
            }
        });
    }

    private static void checkReceivePhysicalRejectsNegativeAmount(VerificationReporter r, ServerPlayer player) {
        safe(r, "CreditPaymentHelper.receivePhysical rejects a negative amount without mutation "
                + "(failed physical delivery leaves state unchanged)", () -> {
            long before = physical(player);
            boolean received = CreditPaymentHelper.receivePhysical(player, -50);
            long after = physical(player);
            boolean pass = !received && after == before;
            return result(pass, "received=" + received + ", before=" + before + ", after=" + after);
        });
    }

    private static void checkReceivePhysicalHandlesLargeAmountSafely(VerificationReporter r, ServerPlayer player) {
        safe(r, "receivePhysical delivers a large payout across multiple stacks without overflow/exception", () -> {
            long before = physical(player);
            long amount = 250_000L; // spans 25 MAX_PER_STACK(10,000)-sized stacks
            try {
                boolean received = CreditPaymentHelper.receivePhysical(player, amount);
                long after = physical(player);
                boolean pass = received && after - before == amount;
                return result(pass, "received=" + received + ", before=" + before + ", after=" + after);
            } finally {
                restorePhysicalCredits(player, before);
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hardening pass — BUY overflow / negative price / negative payment (Part 1)
    // ─────────────────────────────────────────────────────────────────────

    private static void checkNegativePaymentRejectedWithoutMutation(VerificationReporter r, ServerPlayer player) {
        safe(r, "CreditPaymentHelper.pay rejects a negative amount without mutation", () -> {
            long walletBefore = wallet(player);
            try {
                setWallet(player, 100);
                long before = wallet(player);
                boolean paid = CreditPaymentHelper.pay(player, -50);
                boolean pass = !paid && wallet(player) == before;
                return result(pass, "paid=" + paid + ", before=" + before + ", after=" + wallet(player));
            } finally {
                setWallet(player, walletBefore);
            }
        });
    }

    private static void checkNegativePhysicalPaymentRejectedWithoutMutation(VerificationReporter r, ServerPlayer player) {
        safe(r, "CreditPaymentHelper.payPhysical rejects a negative amount without mutation", () -> {
            int slot = 1;
            ItemStack slotBefore = player.getInventory().getItem(slot).copy();
            try {
                ItemStack physical = new ItemStack(CurrencyItems.CREDITS);
                CreditsItem.setAmount(physical, 100);
                player.getInventory().setItem(slot, physical);
                long before = CreditPaymentHelper.physicalCredits(player);

                boolean paid = CreditPaymentHelper.payPhysical(player, -50);

                long after = CreditPaymentHelper.physicalCredits(player);
                boolean pass = !paid && after == before;
                return result(pass, "paid=" + paid + ", before=" + before + ", after=" + after);
            } finally {
                player.getInventory().setItem(slot, slotBefore);
            }
        });
    }

    private static void checkMalformedNegativePhysicalCreditsStackIgnoredNotSubtracted(
            VerificationReporter r, ServerPlayer player) {
        safe(r, "A malformed (negative-amount) physical Credits stack is ignored, not subtracted, by physicalCredits", () -> {
            int goodSlot = 1;
            int malformedSlot = 5;
            ItemStack goodSlotBefore = player.getInventory().getItem(goodSlot).copy();
            ItemStack malformedSlotBefore = player.getInventory().getItem(malformedSlot).copy();
            try {
                ItemStack good = new ItemStack(CurrencyItems.CREDITS);
                CreditsItem.setAmount(good, 100);
                player.getInventory().setItem(goodSlot, good);

                long beforeMalformed = CreditPaymentHelper.physicalCredits(player);

                // CreditsItem.setAmount performs no validation of its own — a corrupted/malformed
                // stack with a negative stored amount is safely constructible in this dev harness,
                // exactly the kind of data physicalCredits must defend against.
                ItemStack malformed = new ItemStack(CurrencyItems.CREDITS);
                CreditsItem.setAmount(malformed, -9_999);
                player.getInventory().setItem(malformedSlot, malformed);

                long afterMalformed = CreditPaymentHelper.physicalCredits(player);

                boolean pass = beforeMalformed == 100 && afterMalformed == 100;
                return result(pass, "beforeMalformed=" + beforeMalformed + ", afterMalformed=" + afterMalformed);
            } finally {
                player.getInventory().setItem(goodSlot, goodSlotBefore);
                player.getInventory().setItem(malformedSlot, malformedSlotBefore);
            }
        });
    }

    /**
     * Final correction pass: {@code shrinkPhysicalCredits} previously processed a malformed
     * (non-positive stored amount) Credits stack the same as a legitimate one — {@code take}
     * came out negative for a negative {@code stackAmount}, and {@code remaining -= take} then
     * INCREASED the amount still owed instead of leaving it untouched. A malformed stack sitting
     * in an earlier slot than the legitimate Credits could therefore make a physical payment
     * consume more legitimate Credits than it should, while still reporting success. Placing the
     * malformed stack in {@code malformedSlot} (4) ahead of the legitimate stack in
     * {@code goodSlot} (6) exercises exactly that ordering.
     */
    private static void checkShrinkPhysicalCreditsIgnoresMalformedNegativeStack(
            VerificationReporter r, ServerPlayer player) {
        safe(r, "shrinkPhysicalCredits ignores a malformed negative-amount stack instead of inflating the amount owed", () -> {
            int malformedSlot = 4;
            int goodSlot = 6;
            ItemStack malformedSlotBefore = player.getInventory().getItem(malformedSlot).copy();
            ItemStack goodSlotBefore = player.getInventory().getItem(goodSlot).copy();
            try {
                ItemStack malformed = new ItemStack(CurrencyItems.CREDITS);
                CreditsItem.setAmount(malformed, -9_999);
                player.getInventory().setItem(malformedSlot, malformed);

                ItemStack good = new ItemStack(CurrencyItems.CREDITS);
                CreditsItem.setAmount(good, 100);
                player.getInventory().setItem(goodSlot, good);

                boolean paid = CreditPaymentHelper.payPhysical(player, 50);
                long remaining = CreditPaymentHelper.physicalCredits(player);
                long goodStackAmount = CreditsItem.getAmount(player.getInventory().getItem(goodSlot));

                boolean pass = paid && remaining == 50L && goodStackAmount == 50L;
                return result(pass, "paid=" + paid + ", remaining=" + remaining + ", goodStackAmount=" + goodStackAmount);
            } finally {
                player.getInventory().setItem(malformedSlot, malformedSlotBefore);
                player.getInventory().setItem(goodSlot, goodSlotBefore);
            }
        });
    }

    private static void checkBuyOverflowRejectedWithoutMutation(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "BUY multiplication overflow is rejected and changes nothing", player, overflowPriceShop(), 300,
                ItemStack.EMPTY, (merchant, slot) -> {
                    giveWallet(player, 10_000);
                    long walletBefore = wallet(player);
                    int itemsBefore = countMatching(player, Items.DIAMOND);

                    // price = Long.MAX_VALUE, quantity 2 -> multiplyExact must overflow, not wrap negative.
                    BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 2);

                    boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.OVERFLOW
                            && wallet(player) == walletBefore
                            && merchant.currentCredits() == 300
                            && countMatching(player, Items.DIAMOND) == itemsBefore;
                    return result(pass, "buyResult=" + buyResult + ", currentCredits=" + merchant.currentCredits());
                });
    }

    private static void checkNegativeShopPriceCannotCompleteBuy(VerificationReporter r, ServerPlayer player) {
        runSessionCheck(r, "A negative authored shop price cannot complete a BUY", player, negativePriceShop(), 300,
                ItemStack.EMPTY, (merchant, slot) -> {
                    giveWallet(player, 10_000);
                    long walletBefore = wallet(player);
                    int itemsBefore = countMatching(player, Items.STICK);

                    BuyResult buyResult = TradeSessionManager.handleBuy(player, 0, 1);

                    boolean pass = !buyResult.success() && buyResult.reason() == BuyResult.Reason.INVALID_PRICE
                            && wallet(player) == walletBefore
                            && merchant.currentCredits() == 300
                            && countMatching(player, Items.STICK) == itemsBefore;
                    return result(pass, "buyResult=" + buyResult + ", currentCredits=" + merchant.currentCredits());
                });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Hardening pass — entity-backed session revalidation (Part 4)
    //
    // Part 3 (MerchantRuntimeRegistry's per-MinecraftServer scoping / SERVER_STOPPED clearing)
    // is deliberately NOT exercised by a self-test here (correction pass, Part 2) — a synthetic
    // check previously called MerchantRuntimeRegistry.getOrCreate/clearForServer directly against
    // the REAL development server, which is exactly the kind of verification-mutates-production
    // state this suite otherwise goes out of its way to avoid (see Part 2's isolation checks
    // below). The two-consecutive-logical-server runtime test documented in the canonical design
    // doc's Section 12d already proves this behavior end-to-end, for real, without that
    // side effect.
    // ─────────────────────────────────────────────────────────────────────

    private static void checkDiscardedNpcEndsSessionAndBlocksFurtherTransaction(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Discarding the trade partner NPC ends the session and blocks any further transaction", () -> {
            TotalityNpcEntity npc = new TotalityNpcEntity(ModEntities.TOTALITY_NPC, server.overworld());
            npc.setPos(player.getX(), player.getY(), player.getZ());
            server.overworld().addFreshEntity(npc);

            int slot = 2;
            try {
                MerchantRuntime merchant = freshMerchant(300);
                TradeSessionManager.startTradeForVerification(player, VERIFICATION_SHOP_ID, standardShop(), merchant, npc);

                npc.discard(); // TotalityNpcEntity.remove() proactively ends the session

                player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 4));
                long walletBefore = wallet(player);
                SellResult sellResult = TradeSessionManager.handleSell(player, slot, 1);

                boolean pass = !sellResult.success()
                        && !TradeSessionManager.isTrading(player)
                        && wallet(player) == walletBefore
                        && merchant.currentCredits() == 300;
                return result(pass, "sellResult=" + sellResult + ", stillTrading=" + TradeSessionManager.isTrading(player));
            } finally {
                TradeSessionManager.endTrade(player);
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                if (!npc.isRemoved()) npc.discard();
            }
        });
    }

    private static void checkOutOfRangeNpcEntityBackedSessionRejectedByRevalidation(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "An entity-backed session with an out-of-range NPC is rejected by revalidation before any mutation", () -> {
            TotalityNpcEntity npc = new TotalityNpcEntity(ModEntities.TOTALITY_NPC, server.overworld());
            // Started adjacent to the player (so startTradeForVerification's own setup is valid),
            // then moved far away WITHOUT a server tick running — only TradeSessionManager's own
            // revalidation (not the NPC's per-tick safety net) can catch this in time.
            npc.setPos(player.getX(), player.getY(), player.getZ());
            server.overworld().addFreshEntity(npc);

            int slot = 3;
            try {
                MerchantRuntime merchant = freshMerchant(300);
                TradeSessionManager.startTradeForVerification(player, VERIFICATION_SHOP_ID, standardShop(), merchant, npc);

                npc.setPos(player.getX() + 1000, player.getY(), player.getZ() + 1000);

                player.getInventory().setItem(slot, new ItemStack(Items.BREAD, 4));
                long walletBefore = wallet(player);
                SellResult sellResult = TradeSessionManager.handleSell(player, slot, 1);

                boolean pass = !sellResult.success() && sellResult.reason() == SellResult.Reason.NPC_INVALID
                        && !TradeSessionManager.isTrading(player)
                        && wallet(player) == walletBefore
                        && merchant.currentCredits() == 300
                        && player.getInventory().getItem(slot).getCount() == 4;
                return result(pass, "sellResult=" + sellResult + ", stillTrading=" + TradeSessionManager.isTrading(player));
            } finally {
                TradeSessionManager.endTrade(player);
                player.getInventory().setItem(slot, ItemStack.EMPTY);
                if (!npc.isRemoved()) npc.discard();
            }
        });
    }

    /**
     * Correction pass, Part 1: {@code releaseTradePartner} previously resolved the session's
     * recorded NPC by dimension + numeric entity id ALONE, with no UUID check — so if the id
     * happened to resolve to a DIFFERENT entity than the one the session actually started with,
     * releasing the session would incorrectly release THAT unrelated entity's interaction lock.
     *
     * <p>Two live but never-added-to-the-level entities are used to drive
     * {@link TradeSessionManager}'s package-visible {@code matchesUuid(candidate, expectedUuid)}
     * predicate directly — the exact, sole UUID-comparison gate {@code resolveRecordedNpc}
     * (and transitively {@code revalidateNpc}/{@code releaseTradePartner}) enforces before ever
     * acting on a resolved entity. Deliberately does NOT attempt to force a live numeric-id
     * collision through {@code Level#getEntity(int)}: an earlier version of this check tried
     * exactly that (first via {@code Entity.setId}, which {@code addFreshEntity} ignored by
     * assigning its own fresh id regardless; then via forcing the target chunk to load, which
     * still left {@code getEntity} returning null) and confirmed empirically that this suite —
     * running at {@code SERVER_STARTED}, before any tick has processed a freshly spawned entity
     * into that lookup — cannot exercise live id-based resolution deterministically at all. The
     * two real, distinct entity objects (never added to a level, so none of that machinery is
     * needed) still let this prove the exact UUID-comparison guarantee without depending on it.
     */
    private static void checkReusedEntityIdWithWrongUuidLockNotReleased(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "matchesUuid rejects a candidate with the wrong UUID, and only that entity's lock is ever touched", () -> {
            TotalityNpcEntity actual = new TotalityNpcEntity(ModEntities.TOTALITY_NPC, server.overworld());
            TotalityNpcEntity impostor = new TotalityNpcEntity(ModEntities.TOTALITY_NPC, server.overworld());

            ServerPlayer otherPlayer = TotalityFakePlayer.create(server.overworld(), "[MerchantSellVerification-other]");
            impostor.acquireInteractionLock(otherPlayer); // impostor's own, unrelated lock — must survive untouched

            try {
                // Positive control: the real entity + its own real UUID must match.
                boolean positiveControl = TradeSessionManager.matchesUuid(actual, actual.getUUID());

                boolean lockOwnedBeforeAttempt = impostor.isInteractionLockOwnedBy(otherPlayer);

                // The actual case under test: `impostor` resolved at the recorded id, but the
                // recorded UUID belongs to `actual` — simulates the id having been reused by an
                // unrelated entity since the session was recorded.
                boolean mismatchRejected = !TradeSessionManager.matchesUuid(impostor, actual.getUUID());

                boolean pass = positiveControl && lockOwnedBeforeAttempt && mismatchRejected
                        && impostor.isInteractionLockOwnedBy(otherPlayer); // never touched by the mismatched call
                return result(pass, "positiveControl=" + positiveControl
                        + ", mismatchRejected=" + mismatchRejected
                        + ", lockStillOwnedAfter=" + impostor.isInteractionLockOwnedBy(otherPlayer));
            } finally {
                impostor.releaseInteractionLock();
                actual.releaseInteractionLock();
                if (!actual.isRemoved()) actual.discard();
                if (!impostor.isRemoved()) impostor.discard();
            }
        });
    }

    private static void checkVerificationNeverTouchesProductionRuntime(
            VerificationReporter r, ServerPlayer player, MinecraftServer server) {
        safe(r, "Running the transaction tests never creates or modifies the production runtime for totality:test_trader", () -> {
            boolean noProductionRuntime = MerchantRuntimeRegistry.peek(server, TEST_TRADER_SHOP_ID).isEmpty();
            boolean noDanglingSession = !TradeSessionManager.isTrading(player);
            boolean pass = noProductionRuntime && noDanglingSession;
            return result(pass, "noProductionRuntime=" + noProductionRuntime + ", noDanglingSession=" + noDanglingSession);
        });
    }

    // ─────────────────────────────────────────────────────────────────────
    // Fixtures and helpers
    // ─────────────────────────────────────────────────────────────────────

    private static MerchantRuntime freshMerchant(long credits) {
        return new InMemoryMerchantRuntime(
                Identifier.fromNamespaceAndPath("totality", "selftest/merchant"), credits, Set.of(ModTags.PROVISIONER_BUYS));
    }

    /** A self-contained shop fixture (not the real, datapack-loaded {@code totality:test_trader})
     *  so session checks never depend on — or risk being broken by — authored shop content. */
    private static ShopTemplate standardShop() {
        return new ShopTemplate("Verification Shop", List.of(new ShopEntry(new ItemStack(Items.IRON_SWORD), 450)));
    }

    private static ShopTemplate overflowPriceShop() {
        return new ShopTemplate("Overflow Fixture Shop", List.of(new ShopEntry(new ItemStack(Items.DIAMOND), Long.MAX_VALUE)));
    }

    private static ShopTemplate negativePriceShop() {
        return new ShopTemplate("Malformed Fixture Shop", List.of(new ShopEntry(new ItemStack(Items.STICK), -10)));
    }

    private static int countMatching(ServerPlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static long wallet(ServerPlayer player) {
        return CurrencyComponents.WALLET.get((ComponentProvider) player).getValue();
    }

    private static void setWallet(ServerPlayer player, long value) {
        CurrencyComponents.WALLET.get((ComponentProvider) player).setValue(value);
    }

    private static void giveWallet(ServerPlayer player, long amount) {
        CurrencyComponents.WALLET.get((ComponentProvider) player).modify(amount);
    }

    private static void setHasAccount(ServerPlayer player, boolean hasAccount) {
        DialogueComponents.FLAGS.get((ComponentProvider) player).setFlag("has_account", hasAccount ? 1 : 0);
    }

    private static long physical(ServerPlayer player) {
        return CreditPaymentHelper.physicalCredits(player);
    }

    /** Pays down any physical Credits a check created back to {@code before} — mirrors this
     *  suite's existing wallet/slot restoration pattern so a Part A check can never leak physical
     *  Credits stacks into the fake player's inventory for a later check to trip over. */
    private static void restorePhysicalCredits(ServerPlayer player, long before) {
        long current = physical(player);
        if (current > before) {
            CreditPaymentHelper.payPhysical(player, current - before);
        }
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    /** Runs one check body, converting an unexpected exception into a FAIL instead of aborting
     *  the whole suite — a bug in one check must not hide the pass/fail of the rest. */
    private static void safe(VerificationReporter r, String label, Supplier<CheckResult> body) {
        try {
            CheckResult checkResult = body.get();
            r.check(label, checkResult.pass(), checkResult.detail());
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    @FunctionalInterface
    private interface SessionCheckBody {
        CheckResult run(MerchantRuntime merchant, int slot);
    }

    /**
     * Runs one session-based check against a freshly isolated {@link ShopTemplate} and
     * {@link InMemoryMerchantRuntime} (economy hardening pass, Part 2) — never touches
     * {@link ShopRegistry} or {@link MerchantRuntimeRegistry}. The fake player's original wallet
     * value and the original contents of the test slot are captured BEFORE the check body runs
     * and restored in {@code finally}, alongside ending the session — even if the check body
     * throws — so a single broken check can never leave a stale session, a leftover item, or a
     * mutated wallet behind for the next one (correction pass, Part 3: previously wallet
     * restoration was left to each check body individually, which a thrown exception could skip).
     */
    private static void runSessionCheck(
            VerificationReporter r, String label, ServerPlayer player, ShopTemplate shop, long merchantCredits,
            ItemStack initialSlotStack, SessionCheckBody body) {
        int slot = 0;
        long walletBefore = wallet(player);
        ItemStack slotBefore = player.getInventory().getItem(slot).copy();
        player.getInventory().setItem(slot, initialSlotStack);
        MerchantRuntime merchant = freshMerchant(merchantCredits);
        try {
            TradeSessionManager.startTradeForVerification(player, VERIFICATION_SHOP_ID, shop, merchant);
            CheckResult checkResult = body.run(merchant, slot);
            r.check(label, checkResult.pass(), checkResult.detail());
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        } finally {
            TradeSessionManager.endTrade(player);
            setWallet(player, walletBefore);
            player.getInventory().setItem(slot, slotBefore);
        }
    }
}
