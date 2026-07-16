package zcylas.totality.api.shop;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.entity.npc.ProvisionerNpcEntity;
import zcylas.totality.init.ModEntities;
import zcylas.totality.networking.shop.BuyItemPayload;
import zcylas.totality.networking.shop.ShopEntryDisplayData;
import zcylas.totality.networking.shop.ShowShopStatePayload;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Dev-environment-gated self-test for Phase 4 (Trading Screen redesign, Part I) — same {@link
 * VerificationReporter}-based convention as {@code ItemValueVerification}/
 * {@code MerchantSellVerification}/{@code ProvisionerVerification}. Deliberately kept free of any
 * client-only (GL/render) dependency, unlike the Provisioner male/female texture check (Part A),
 * which lives in its own client-only suite (registered from {@code TotalityClient}, not here) —
 * referencing a client renderer class from a class that also runs on {@code SERVER_STARTED} would
 * risk a {@code NoClassDefFoundError} on a dedicated server if that environment's separate,
 * pre-existing classloading issue is ever fixed independently (explicitly out of scope for this
 * phase either way).
 *
 * <p>Covers the 14 non-texture checks Part I lists — mostly against {@link TradingQuantityMath}
 * (the actual fix for the Phase 3 quantity bug) and the new networking wire format, plus one real
 * session lifecycle check reusing existing {@link TradeSessionManager} verification entry points.
 */
public final class TradingScreenVerification {

    private TradingScreenVerification() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(TradingScreenVerification::runSelfTestIfDev);
    }

    static void runSelfTestIfDev(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;

        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "TradingScreenVerification");
        ServerPlayer player = TotalityFakePlayer.create(server.overworld(), "[TradingScreenVerification]");

        checkMaxBuyQuantityRespectsLimitedStock(r);
        checkMaxBuyQuantityRespectsServerCap(r);
        checkMaxBuyQuantityRespectsAffordability(r);
        checkZeroPriceBuyAvoidsDivisionByZero(r);
        checkSoldOutEntryProducesZeroMaxQuantity(r);
        checkQuantity16TransmittedAsSixteen(r);
        checkQuantityPreservedAfterRefreshWhenStillValid(r);
        checkQuantityClampedDownwardWhenStockDecreases(r);
        checkUnlimitedLegacyShopCappedAtServerMax(r);
        checkSellMaxRespectsStackCount(r, server, player);
        checkSellMaxRespectsMerchantCredits(r, server, player);
        checkRejectedItemsCannotSubmitSell(r);
        checkShowShopStatePayloadRoundTripsNewFields(r, server);
        checkClosingSessionEndsTrading(r, server, player);

        // ── GUI refinement pass (TradingScreenLayout — pure layout/display math) ─
        checkLayoutRegionsDoNotOverlapAtRepresentativeSizes(r);
        checkGuiScale4UsesCompactLayoutWithSufficientContent(r);
        checkScrollClampBounds(r);
        checkCreditFormattingGroupsThousands(r);
        checkSellSlotIssueKeyDistinguishesReasons(r);
        checkSoldOutSemantics(r);

        r.summarize();
    }

    // ── BUY quantity math (TradingQuantityMath — the actual Phase 3 bug fix) ─

    private static void checkMaxBuyQuantityRespectsLimitedStock(VerificationReporter r) {
        safe(r, "BUY quantity maximum respects limited stock", () -> {
            int max = TradingQuantityMath.maxBuyQuantity(true, 5, 10, 10_000);
            return result(max == 5, "max=" + max);
        });
    }

    private static void checkMaxBuyQuantityRespectsServerCap(VerificationReporter r) {
        safe(r, "BUY quantity maximum respects the 100 server cap even with huge funds/no stock limit", () -> {
            int max = TradingQuantityMath.maxBuyQuantity(false, 0, 1, 1_000_000L);
            return result(max == 100, "max=" + max);
        });
    }

    private static void checkMaxBuyQuantityRespectsAffordability(VerificationReporter r) {
        safe(r, "BUY quantity maximum respects affordability", () -> {
            int max = TradingQuantityMath.maxBuyQuantity(false, 0, 50, 120);
            return result(max == 2, "max=" + max);
        });
    }

    private static void checkZeroPriceBuyAvoidsDivisionByZero(VerificationReporter r) {
        safe(r, "A zero-price BUY entry does not divide by zero and is capped only by stock/100", () -> {
            int limited = TradingQuantityMath.maxBuyQuantity(true, 10, 0, 0);
            int unlimited = TradingQuantityMath.maxBuyQuantity(false, 0, 0, 0);
            return result(limited == 10 && unlimited == 100, "limited=" + limited + ", unlimited=" + unlimited);
        });
    }

    private static void checkSoldOutEntryProducesZeroMaxQuantity(VerificationReporter r) {
        safe(r, "A sold-out limited entry produces maximum quantity 0", () -> {
            int max = TradingQuantityMath.maxBuyQuantity(true, 0, 10, 10_000);
            return result(max == 0, "max=" + max);
        });
    }

    private static void checkQuantity16TransmittedAsSixteen(VerificationReporter r) {
        safe(r, "A selected quantity of 16 is transmitted as 16 in BuyItemPayload, never hardcoded to 1", () -> {
            BuyItemPayload payload = new BuyItemPayload(3, 16);
            return result(payload.quantity() == 16 && payload.index() == 3, "payload=" + payload);
        });
    }

    private static void checkQuantityPreservedAfterRefreshWhenStillValid(VerificationReporter r) {
        safe(r, "Quantity is preserved (not silently changed) after a refresh when still within the new maximum", () -> {
            int reconciled = TradingQuantityMath.reconcileAfterRefresh(16, 20);
            return result(reconciled == 16, "reconciled=" + reconciled);
        });
    }

    private static void checkQuantityClampedDownwardWhenStockDecreases(VerificationReporter r) {
        safe(r, "Quantity is clamped downward (never left invalid, never increased) when the new maximum shrinks", () -> {
            int reconciled = TradingQuantityMath.reconcileAfterRefresh(16, 5);
            int reconciledToZero = TradingQuantityMath.reconcileAfterRefresh(16, 0);
            boolean neverIncreases = TradingQuantityMath.reconcileAfterRefresh(3, 50) == 3;
            return result(reconciled == 5 && reconciledToZero == 0 && neverIncreases,
                    "reconciled=" + reconciled + ", reconciledToZero=" + reconciledToZero + ", neverIncreases=" + neverIncreases);
        });
    }

    private static void checkUnlimitedLegacyShopCappedAtServerMax(VerificationReporter r) {
        safe(r, "An unlimited legacy shop entry remains capped at 100 regardless of funds", () -> {
            int max = TradingQuantityMath.maxBuyQuantity(false, 0, 1, Long.MAX_VALUE);
            return result(max == 100, "max=" + max);
        });
    }

    // ── SELL quote math (existing MerchantSellQuoteView, exercised via the new payload shape) ─

    private static void checkSellMaxRespectsStackCount(VerificationReporter r, MinecraftServer server, ServerPlayer player) {
        safe(r, "SELL maximum respects the selected stack's count", () -> {
            ProvisionerNpcEntity npc = new ProvisionerNpcEntity(ModEntities.PROVISIONER, server.overworld());
            npc.setCreditsForTest(100_000L);
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, npc, new ItemStack(Items.BREAD, 4), 4, 1);
            return result(quote.maxQuantityByStack() == 4, "maxQuantityByStack=" + quote.maxQuantityByStack());
        });
    }

    private static void checkSellMaxRespectsMerchantCredits(VerificationReporter r, MinecraftServer server, ServerPlayer player) {
        safe(r, "SELL maximum respects the merchant's available Credits", () -> {
            ProvisionerNpcEntity npc = new ProvisionerNpcEntity(ModEntities.PROVISIONER, server.overworld());
            npc.setCreditsForTest(10L); // bread payout is 6 each -> merchant can afford only 1
            MerchantSellQuoteView quote = MerchantSellQuoteView.compute(player, npc, new ItemStack(Items.BREAD, 4), 4, 1);
            return result(quote.maxQuantityByMerchant() == 1 && quote.effectiveMaxQuantity() == 1,
                    "maxQuantityByMerchant=" + quote.maxQuantityByMerchant() + ", effectiveMaxQuantity=" + quote.effectiveMaxQuantity());
        });
    }

    private static void checkRejectedItemsCannotSubmitSell(VerificationReporter r) {
        safe(r, "TradeRejectionKeys maps NOT_SELLABLE's detail to the correct specific reason key", () -> {
            String notAccepted = TradeRejectionKeys.forSell(SellResult.Reason.NOT_SELLABLE, "Merchant does not accept this item");
            String noValue = TradeRejectionKeys.forSell(SellResult.Reason.NOT_SELLABLE, "Item has no resolvable value");
            String cannotAfford = TradeRejectionKeys.forSell(SellResult.Reason.NOT_SELLABLE, "Merchant cannot afford that quantity");
            String unknown = TradeRejectionKeys.forSell(SellResult.Reason.NOT_SELLABLE, "some future unrecognized reason");
            boolean pass = notAccepted.equals("totality.trading.reject.not_accepted")
                    && noValue.equals("totality.trading.reject.no_value")
                    && cannotAfford.equals("totality.trading.reject.merchant_cannot_afford")
                    && unknown.equals("totality.trading.reject.generic");
            return result(pass, "notAccepted=" + notAccepted + ", noValue=" + noValue
                    + ", cannotAfford=" + cannotAfford + ", unknown=" + unknown);
        });
    }

    // ── Networking round-trip (new Phase 4 wire format) ──────────────────────

    private static void checkShowShopStatePayloadRoundTripsNewFields(VerificationReporter r, MinecraftServer server) {
        safe(r, "ShowShopStatePayload round-trips merchantArchetype and valuedInventorySlots correctly", () -> {
            ShowShopStatePayload original = new ShowShopStatePayload(
                    7, Component.literal("Test Shop"),
                    Component.translatable("totality.trading.archetype.provisioner"),
                    List.of(new ShopEntryDisplayData(new ItemStack(Items.TORCH), 4, true, true, 12)),
                    500, 20, 300, List.of(2, 5, 9), false);

            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
            ShowShopStatePayload.STREAM_CODEC.encode(buf, original);
            ShowShopStatePayload decoded = ShowShopStatePayload.STREAM_CODEC.decode(buf);

            boolean pass = decoded.npcEntityId() == 7
                    && decoded.merchantArchetype().getString().equals(original.merchantArchetype().getString())
                    && decoded.valuedInventorySlots().equals(List.of(2, 5, 9))
                    && decoded.sells().size() == 1
                    && decoded.walletBalance() == 500 && decoded.merchantCredits() == 300;
            return result(pass, "decoded=" + decoded);
        });
    }

    // ── GUI refinement pass — TradingScreenLayout (pure geometry/display math) ─

    /**
     * Region non-overlap at representative logical screen sizes, in BOTH per-mode layouts (BUY
     * hotbar-strip layout and SELL left-panel-inventory layout — the references' split): 480x270
     * (1080p at GUI Scale 4 — Stefan's normal setting), 640x360 (1440p at Scale 4), 960x540
     * (1080p at Scale 2), 1920x1080 (Scale 1), and 320x240 (the smallest window Minecraft
     * allows). Asserts every pair of major regions is disjoint and every region sits inside the
     * panel — the pure-math half of "no overlap at GUI Scale 4"; the rendered result is Stefan's
     * manual check. In the SELL layout {@code inventory() == catalog()} by design, so the
     * pairwise set there is header/tabs/catalog/detail.
     */
    private static void checkLayoutRegionsDoNotOverlapAtRepresentativeSizes(VerificationReporter r) {
        safe(r, "Layout regions never overlap at representative sizes, in both BUY and SELL layouts", () -> {
            int[][] sizes = { {480, 270}, {640, 360}, {960, 540}, {1920, 1080}, {320, 240} };
            for (int[] size : sizes) {
                for (boolean sellLayout : new boolean[] { false, true }) {
                    TradingScreenLayout.Regions l = TradingScreenLayout.compute(size[0], size[1], sellLayout);
                    TradingScreenLayout.Rect[] regions = sellLayout
                            ? new TradingScreenLayout.Rect[] { l.header(), l.tabs(), l.catalog(), l.detail() }
                            : new TradingScreenLayout.Rect[] { l.header(), l.tabs(), l.catalog(), l.detail(), l.inventory() };
                    for (int a = 0; a < regions.length; a++) {
                        for (int b = a + 1; b < regions.length; b++) {
                            if (regions[a].intersects(regions[b])) {
                                return result(false, "overlap at " + size[0] + "x" + size[1]
                                        + " sell=" + sellLayout + ": " + regions[a] + " vs " + regions[b]);
                            }
                        }
                    }
                    for (TradingScreenLayout.Rect region : regions) {
                        boolean inPanel = region.x() >= l.panel().x() && region.y() >= l.panel().y()
                                && region.right() <= l.panel().right() && region.bottom() <= l.panel().bottom();
                        if (!inPanel) {
                            return result(false, "region outside panel at " + size[0] + "x" + size[1]
                                    + " sell=" + sellLayout + ": " + region);
                        }
                    }
                    if (sellLayout && !l.inventory().equals(l.catalog())) {
                        return result(false, "SELL layout must host the inventory in the left panel at "
                                + size[0] + "x" + size[1]);
                    }
                }
            }
            return result(true, "all sizes clean in both layouts");
        });
    }

    private static void checkGuiScale4UsesCompactLayoutWithSufficientContent(VerificationReporter r) {
        safe(r, "GUI Scale 4 @ 1080p (480x270) fits both per-mode layouts on normal metrics, the SELL left "
                + "panel fits the 9-column grid, and the compact fallback still engages at shorter windows", () -> {
            TradingScreenLayout.Regions buy4 = TradingScreenLayout.compute(480, 270, false);
            TradingScreenLayout.Regions sell4 = TradingScreenLayout.compute(480, 270, true);
            TradingScreenLayout.Regions shortWindow = TradingScreenLayout.compute(480, 200, false);
            boolean pass = !buy4.compact() && buy4.contentH() >= TradingScreenLayout.MIN_CONTENT_H
                    && !sell4.compact() && sell4.contentH() >= TradingScreenLayout.MIN_CONTENT_H
                    && sell4.catalog().w() >= TradingScreenLayout.GRID_W + TradingScreenLayout.PAD
                    && shortWindow.compact() && shortWindow.contentH() >= TradingScreenLayout.MIN_CONTENT_H;
            return result(pass, "buy4.contentH=" + buy4.contentH() + ", sell4.contentH=" + sell4.contentH()
                    + ", sell4.catalogW=" + sell4.catalog().w() + " (grid " + TradingScreenLayout.GRID_W + ")"
                    + ", shortWindow.compact=" + shortWindow.compact()
                    + ", shortWindow.contentH=" + shortWindow.contentH());
        });
    }

    private static void checkScrollClampBounds(VerificationReporter r) {
        safe(r, "Catalog scroll bounds stay valid: never negative, never past the last row, zero when everything fits", () -> {
            int contentH = 108; // the compact GUI Scale 4 content height — 3 visible rows of 36px
            boolean negativeClamped = TradingScreenLayout.clampScroll(-5, 10, contentH) == 0;
            int maxFor10 = TradingScreenLayout.maxScrollRows(10, contentH); // 4 rows total, 3 visible -> 1
            boolean overClamped = TradingScreenLayout.clampScroll(999, 10, contentH) == maxFor10;
            boolean emptyListNoScroll = TradingScreenLayout.maxScrollRows(0, contentH) == 0;
            boolean fitsNoScroll = TradingScreenLayout.maxScrollRows(9, contentH) == 0; // 3 rows, 3 visible
            boolean pass = negativeClamped && overClamped && maxFor10 == 1 && emptyListNoScroll && fitsNoScroll;
            return result(pass, "negativeClamped=" + negativeClamped + ", maxFor10=" + maxFor10
                    + ", overClamped=" + overClamped + ", emptyListNoScroll=" + emptyListNoScroll
                    + ", fitsNoScroll=" + fitsNoScroll);
        });
    }

    private static void checkCreditFormattingGroupsThousands(VerificationReporter r) {
        safe(r, "Credit formatting groups thousands without altering digits (locale-agnostic assertion)", () -> {
            String big = TradingScreenLayout.formatCredits(1_234_567L);
            String small = TradingScreenLayout.formatCredits(999L);
            boolean digitsPreserved = big.replaceAll("\\D", "").equals("1234567");
            boolean grouped = big.length() == 9; // 7 digits + 2 grouping separators, whatever the locale uses
            boolean smallUngrouped = small.equals("999");
            boolean pass = digitsPreserved && grouped && smallUngrouped;
            return result(pass, "big=" + big + ", small=" + small);
        });
    }

    private static void checkSellSlotIssueKeyDistinguishesReasons(VerificationReporter r) {
        safe(r, "Rejected-item hover reason distinguishes 'not accepted' from 'no known value', and a sellable item has neither", () -> {
            String notAccepted = TradingScreenLayout.sellSlotIssueKey(false, true);
            String notAcceptedNoValue = TradingScreenLayout.sellSlotIssueKey(false, false);
            String noValue = TradingScreenLayout.sellSlotIssueKey(true, false);
            String sellable = TradingScreenLayout.sellSlotIssueKey(true, true);
            boolean pass = "totality.trading.reject.not_accepted".equals(notAccepted)
                    && "totality.trading.reject.not_accepted".equals(notAcceptedNoValue) // acceptance outranks value
                    && "totality.trading.reject.no_value".equals(noValue)
                    && sellable == null
                    && !notAccepted.equals(noValue);
            return result(pass, "notAccepted=" + notAccepted + ", noValue=" + noValue + ", sellable=" + sellable);
        });
    }

    private static void checkSoldOutSemantics(VerificationReporter r) {
        safe(r, "Sold-out is exactly 'limited stock at zero' — the entry stays in the list (visible) with max quantity 0", () -> {
            ShopEntryDisplayData soldOut = new ShopEntryDisplayData(new ItemStack(Items.TORCH), 4, true, true, 0);
            ShopEntryDisplayData inStock = new ShopEntryDisplayData(new ItemStack(Items.TORCH), 4, true, true, 1);
            ShopEntryDisplayData unlimited = new ShopEntryDisplayData(new ItemStack(Items.TORCH), 4, true, false, 0);
            boolean pass = soldOut.soldOut() && !inStock.soldOut() && !unlimited.soldOut()
                    && TradingQuantityMath.maxBuyQuantity(true, 0, 4, 1_000_000L) == 0;
            return result(pass, "soldOut=" + soldOut.soldOut() + ", inStock=" + inStock.soldOut()
                    + ", unlimited=" + unlimited.soldOut());
        });
    }

    // ── Session lifecycle (existing behavior, regression guard per Part I item 14) ─

    private static void checkClosingSessionEndsTrading(VerificationReporter r, MinecraftServer server, ServerPlayer player) {
        safe(r, "Ending the trade session (as the client's screen-close action triggers) stops trading", () -> {
            ShopTemplate shop = new ShopTemplate("Verification Close Shop",
                    List.of(new ShopEntry(new ItemStack(Items.IRON_SWORD), 100)));
            MerchantRuntime merchant = new InMemoryMerchantRuntime(
                    Identifier.fromNamespaceAndPath("totality", "selftest/close_session_merchant"),
                    300, Set.of(zcylas.totality.init.ModTags.PROVISIONER_BUYS));
            try {
                TradeSessionManager.startTradeForVerification(
                        player, Identifier.fromNamespaceAndPath("totality", "selftest/close_session_shop"), shop, merchant);
                boolean startedTrading = TradeSessionManager.isTrading(player);
                TradeSessionManager.endTrade(player);
                boolean stillTrading = TradeSessionManager.isTrading(player);
                return result(startedTrading && !stillTrading,
                        "startedTrading=" + startedTrading + ", stillTradingAfterEnd=" + stillTrading);
            } finally {
                TradeSessionManager.endTrade(player);
            }
        });
    }

    private record CheckResult(boolean pass, String detail) {}

    private static CheckResult result(boolean pass, String detail) {
        return new CheckResult(pass, detail);
    }

    private static void safe(VerificationReporter r, String label, Supplier<CheckResult> body) {
        try {
            CheckResult checkResult = body.get();
            r.check(label, checkResult.pass(), checkResult.detail());
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
