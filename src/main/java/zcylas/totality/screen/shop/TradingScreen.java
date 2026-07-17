package zcylas.totality.screen.shop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.api.client.util.CloseableScissor;
import zcylas.totality.api.shop.TradingQuantityMath;
import zcylas.totality.api.shop.TradingScreenLayout;
import zcylas.totality.api.shop.TradingScreenLayout.Rect;
import zcylas.totality.api.shop.TradingScreenLayout.Regions;
import zcylas.totality.client.tooltip.TooltipExtension;
import zcylas.totality.init.ModTags;
import zcylas.totality.networking.shop.BuyItemPayload;
import zcylas.totality.networking.shop.CloseTradePayload;
import zcylas.totality.networking.shop.RequestSellQuotePayload;
import zcylas.totality.networking.shop.SellItemPayload;
import zcylas.totality.networking.shop.SellQuoteResultPayload;
import zcylas.totality.networking.shop.ShopEntryDisplayData;
import zcylas.totality.networking.shop.ShowShopStatePayload;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static zcylas.totality.api.shop.TradingScreenLayout.CATALOG_COLS;
import static zcylas.totality.api.shop.TradingScreenLayout.CATALOG_ROW_H;
import static zcylas.totality.api.shop.TradingScreenLayout.DETAIL_BOTTOM_BAND_H;
import static zcylas.totality.api.shop.TradingScreenLayout.INV_COLS;
import static zcylas.totality.api.shop.TradingScreenLayout.INV_STORAGE_ROWS;
import static zcylas.totality.api.shop.TradingScreenLayout.PAD;
import static zcylas.totality.api.shop.TradingScreenLayout.SLOT_GAP;
import static zcylas.totality.api.shop.TradingScreenLayout.SLOT_SIZE;

/**
 * Trading screen (Phase 4 redesign + GUI refinement pass) — native Minecraft rendering styled
 * after the {@code Context/Audit/Image References/trade_screen_buy_v1.png}/{@code
 * trade_screen_sell_v1.png} references: dark navy panels with steel-cyan structural borders,
 * gold reserved for the title and Credit values, a large centered TRADING title with the
 * merchant identity beneath it, a bordered Credits box top-right, labeled detail rows with
 * right-aligned values, and chunky per-mode action buttons (Cancel+Buy / Clear+Sell). Still no
 * texture art, portrait system, or GUI framework — the full custom textured overhaul remains
 * deferred; this is the references' information hierarchy and palette expressed with
 * {@code fill}/{@code text}/{@code item} primitives only.
 *
 * <p><b>GUI Scale 4:</b> all geometry comes from {@link TradingScreenLayout} (pure,
 * verification-exercised — see its javadoc for the 480x270 root cause and the compact fallback).
 * The detail panels reserve a fixed bottom action band ({@link
 * TradingScreenLayout#DETAIL_BOTTOM_BAND_H}) and scissor/skip their top field flow at the band
 * edge, so no window size can make fields and controls overlap.
 *
 * <p><b>The Phase 3 quantity bug</b> (flat 100 cap ignoring real stock, with silent rejection)
 * remains fixed by {@link TradingQuantityMath#maxBuyQuantity} as the one client-side source of
 * truth for the valid quantity range, plus {@link #showRejection} for visible feedback. SELL uses
 * a live server-computed quote per selected inventory slot ({@link #applySellQuote}) — never a
 * client-computed payout. BUYBACK is visibly present but inert.
 */
public class TradingScreen extends Screen {

    // ── Reference palette: navy surfaces, steel-cyan structure, gold values ──
    private static final int COLOR_BG          = 0xFF05070A;
    private static final int COLOR_PANEL_BG    = 0xFF0A0E16;
    private static final int COLOR_CELL_BG     = 0xFF0D131D;
    private static final int COLOR_CELL_HOV    = 0xFF152030;
    private static final int COLOR_FRAME       = 0xFF2A4A5A; // structural borders (steel cyan)
    private static final int COLOR_FRAME_HOV   = 0xFF3A6A80;
    private static final int COLOR_FRAME_FAINT = 0xFF1C2630; // empty/idle slot borders
    private static final int COLOR_ACTIVE_FILL = 0xFF0D2A38; // active tab / selected card fill
    private static final int COLOR_GOLD        = 0xFFD4A030;
    private static final int COLOR_GOLD_DIM    = 0xFF7A5010;
    private static final int COLOR_CYAN        = 0xFF00CCFF;
    private static final int COLOR_LABEL       = 0xFFCCCCCC;
    private static final int COLOR_DIM         = 0xFF888888;
    private static final int COLOR_RED         = 0xFFCC4444;
    private static final int COLOR_RED_SOFT    = 0xFFCC6666;
    private static final int COLOR_GREEN       = 0xFF44CC44;
    private static final int COLOR_SOLDOUT_OVERLAY = 0xB0000000;
    private static final int COLOR_INV_LABEL   = 0xFF5A8A9A;

    private static final String CREDITS_SYMBOL = "₵"; // ₵ — matches every other Credits display in the mod

    // Bottom action band rows (inside DETAIL_BOTTOM_BAND_H = 46): quantity row, then the total
    // row, then the full-width button pair — shared by drawing AND click hit-testing.
    private static final int QTY_ROW_OFFSET    = 44;
    private static final int TOTAL_ROW_OFFSET  = 28;
    private static final int BUTTON_ROW_OFFSET = 17;
    private static final int BUTTON_H          = 15;

    /** How long a transaction rejection banner stays visible (6s) before clearing itself — it
     *  also clears immediately on any new server state, selection change, or tab switch. */
    private static final int REJECTION_VISIBLE_TICKS = 120;

    private enum Mode { BUY, SELL, BUYBACK }

    private ShowShopStatePayload state;
    private Mode mode = Mode.BUY;

    private int selectedBuyIndex = -1;
    private int buyQuantity = 0;
    private int catalogScrollRows = 0;

    private int selectedSellSlot = -1;
    private int sellQuantity = 0;
    @Nullable private SellQuoteResultPayload sellQuote;
    @Nullable private ItemStack quotedStackSnapshot;
    private boolean pendingFreshSellSelection = false;

    private boolean editingQuantity = false;
    private String editBuffer = "";

    @Nullable private String rejectionKey;
    private int rejectionTicksLeft = 0;

    /** Terms of an underfunded-merchant SELL the player has been shown and must explicitly accept
     *  or cancel (design document Part A) — captured at the moment SELL is pressed, echoed back to
     *  the server only as PROOF of what was confirmed (never authoritative; the server independently
     *  recomputes and rejects as stale on any mismatch). Modal: while non-null, all other click/key
     *  input is swallowed. */
    private record PendingSellConfirmation(int slotIndex, int quantity, long totalValue, long payableAmount) {}
    @Nullable private PendingSellConfirmation pendingSellConfirmation;

    public TradingScreen(ShowShopStatePayload initial) {
        super(Component.translatable("totality.trading.title"));
        this.state = initial;
        // Open with the first purchasable entry pre-selected (trade_screen_buy_v1 reference) so
        // the detail panel is populated immediately instead of an empty "Select an Item" pane.
        // Once only — a later deliberate deselect (Cancel) is never overridden by a refresh.
        for (int i = 0; i < initial.sells().size(); i++) {
            if (!initial.sells().get(i).soldOut()) {
                selectedBuyIndex = i;
                buyQuantity = TradingQuantityMath.clamp(1, computeMaxBuyQuantity(i));
                break;
            }
        }
    }

    /** Server-issued state refresh (session start, or after a successful BUY/SELL — never sent on
     *  a rejected attempt, see {@link #showRejection}). Preserves the current selection and
     *  quantity when still valid; clamps DOWN, never up, otherwise — {@link
     *  TradingQuantityMath#reconcileAfterRefresh} is strictly non-increasing. */
    public void applyUpdate(ShowShopStatePayload update) {
        this.state = update;
        clearRejection();

        if (selectedBuyIndex >= update.sells().size()) selectedBuyIndex = -1;
        if (selectedBuyIndex >= 0) {
            buyQuantity = TradingQuantityMath.reconcileAfterRefresh(
                    buyQuantity, computeMaxBuyQuantity(selectedBuyIndex));
        }

        if (selectedSellSlot >= 0) {
            if (playerStack(selectedSellSlot).isEmpty()) {
                // The selected stack no longer exists (e.g. its final units were just sold) —
                // clear the selection explicitly rather than leaving a live index pointing at an
                // empty slot for the stale-quote check to chase.
                clearSellSelection();
            } else {
                requestSellQuoteFor(selectedSellSlot);
            }
        }
    }

    /** Live SELL quote for the currently selected slot — ignored if it answers a slot the player
     *  has since deselected (a stale/late response must never misapply). Same non-increasing
     *  refresh rule as {@link #applyUpdate}: only a genuinely fresh selection initializes to 1. */
    public void applySellQuote(SellQuoteResultPayload payload) {
        if (payload.slotIndex() != selectedSellSlot) return;
        this.sellQuote = payload;
        int maxQ = payload.effectiveMaxQuantity();
        if (pendingFreshSellSelection) {
            sellQuantity = TradingQuantityMath.clamp(1, maxQ);
            pendingFreshSellSelection = false;
        } else {
            sellQuantity = TradingQuantityMath.reconcileAfterRefresh(sellQuantity, maxQ);
        }
    }

    /** A failed BUY/SELL previously produced no visible feedback at all — this shows the
     *  server-resolved localized reason in the banner area for {@link #REJECTION_VISIBLE_TICKS}. */
    public void showRejection(boolean buy, String reasonKey) {
        this.rejectionKey = reasonKey;
        this.rejectionTicksLeft = REJECTION_VISIBLE_TICKS;
    }

    @Override
    public void tick() {
        if (rejectionTicksLeft > 0 && --rejectionTicksLeft == 0) {
            rejectionKey = null;
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        maybeRefreshStaleSellQuote();

        Regions l = TradingScreenLayout.compute(width, height, mode == Mode.SELL);
        // Central scroll clamp — draw, wheel, and click hit-testing all see the same clamped
        // value, so a resize can never leave the hitboxes mapped to an out-of-range scroll.
        catalogScrollRows = TradingScreenLayout.clampScroll(
                catalogScrollRows, state.sells().size(), l.contentH());

        Rect p = l.panel();
        g.fill(p.x(), p.y(), p.right(), p.bottom(), COLOR_PANEL_BG);
        drawFrame(g, p.x(), p.y(), p.w(), p.h(), 2, COLOR_FRAME);

        drawHeader(g, l);
        drawTabs(g, l, mx, my);

        switch (mode) {
            case BUY -> {
                drawBuyCatalog(g, l, mx, my);
                drawBuyDetail(g, l, mx, my);
            }
            // SELL's left panel IS the inventory grid (trade_screen_sell_v1) — drawn by
            // drawInventoryRow below, which is per-mode: left grid panel in SELL, hotbar strip
            // in BUY/BUYBACK.
            case SELL -> drawSellDetail(g, l, mx, my);
            case BUYBACK -> drawBuybackPlaceholder(g, l);
        }

        drawInventoryRow(g, l, mx, my);
        drawRejectionBanner(g, l);
        drawHoverTooltips(g, l, mx, my);
        drawSellConfirmation(g, mx, my);
    }

    // ── Header (reference style: big centered title, merchant beneath, credits box right) ──

    private void drawHeader(GuiGraphicsExtractor g, Regions l) {
        Rect hd = l.header();
        int x = hd.x(), y = hd.y(), w = hd.w(), h = hd.h();

        // No boxed header — the references let the title float on the panel, separated from the
        // tabs by a single line.
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, COLOR_FRAME);

        // Credits box, top-right (bordered, two labeled rows, values right-aligned in gold).
        // Row positions derive from the box height so the two rows can never collide — at the
        // compact header height the previous +2/+8 placement overlapped them (trade_screen5.png).
        int boxW = Math.max(120, Math.min(200, (int) (w * 0.38f)));
        int boxH = h - 5;
        int boxX = x + w - 3 - boxW;
        int boxY = y + 2;
        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, COLOR_CELL_BG);
        drawFrame(g, boxX, boxY, boxW, boxH, 1, COLOR_FRAME);
        int row1Y = boxY + 2;
        int row2Y = boxY + boxH - 10;
        String merchantValue = CREDITS_SYMBOL + TradingScreenLayout.formatCredits(state.merchantCredits());
        // Preserved meaning: total spendable funds (Wallet + physical Credits), exactly what the
        // server's own affordability check (CreditPaymentHelper.canAfford) evaluates for BUY.
        String playerValue = CREDITS_SYMBOL
                + TradingScreenLayout.formatCredits(state.walletBalance() + state.physicalCredits());
        drawLabelValueRow(g, boxX + 4, row1Y, boxW - 8,
                Component.translatable("totality.trading.merchant_credits").getString() + ":", merchantValue, COLOR_GOLD);
        drawLabelValueRow(g, boxX + 4, row2Y, boxW - 8,
                Component.translatable("totality.trading.your_credits").getString() + ":", playerValue, COLOR_GOLD);

        // Large centered title with flanking accent lines, merchant identity centered beneath.
        String title = Component.translatable("totality.trading.title").getString().toUpperCase(Locale.ROOT);
        float titleScale = l.compact() ? 1.1f : 1.4f;
        int centerX = x + w / 2;
        int titleY = y + 2;
        drawScaledUpCentered(g, title, centerX, titleY, titleScale, COLOR_GOLD);

        int titleHalfW = (int) (font.width(title) * titleScale / 2f) + 6;
        int lineY = titleY + (int) (4 * titleScale);
        int lineLeftEnd = centerX - titleHalfW;
        int lineRightStart = centerX + titleHalfW;
        if (lineLeftEnd - (x + PAD) > 20 && (boxX - 4) - lineRightStart > 20) {
            g.fill(x + PAD, lineY, lineLeftEnd, lineY + 1, COLOR_FRAME);
            g.fill(lineRightStart, lineY, boxX - 4, lineY + 1, COLOR_FRAME);
        }

        String merchantLine = state.shopName().getString() + "  ·  " + state.merchantArchetype().getString();
        int subtitleY = y + h - (l.compact() ? 11 : 12);
        int subtitleMaxW = Math.max(60, (boxX - 4 - centerX) * 2 - 8);
        drawScaledCentered(g, merchantLine, centerX, subtitleY, subtitleMaxW, COLOR_LABEL);
    }

    /** Label left (scaled down to fit beside the value), value right-aligned — the references'
     *  two-column row treatment used by the credits box and every detail field row. */
    private void drawLabelValueRow(GuiGraphicsExtractor g, int x, int y, int w,
                                   String label, String value, int valueColor) {
        int vw = font.width(value);
        drawScaledLeft(g, label, x, y, Math.max(10, w - vw - 6), COLOR_LABEL);
        g.text(font, Component.literal(value), x + w - vw, y, valueColor, false);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void drawTabs(GuiGraphicsExtractor g, Regions l, int mx, int my) {
        Rect t = l.tabs();
        int tabW = t.w() / 3;
        drawTab(g, Component.translatable("totality.trading.buy").getString(),
                t.x(), t.y(), tabW, t.h(), mode == Mode.BUY, true, mx, my);
        drawTab(g, Component.translatable("totality.trading.sell").getString(),
                t.x() + tabW, t.y(), tabW, t.h(), mode == Mode.SELL, true, mx, my);
        String buybackLabel = Component.translatable("totality.trading.buyback").getString()
                + " (" + Component.translatable("totality.trading.coming_later").getString() + ")";
        drawTab(g, buybackLabel, t.x() + tabW * 2, t.y(), t.w() - tabW * 2, t.h(), mode == Mode.BUYBACK, false, mx, my);
    }

    private void drawTab(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                          boolean active, boolean enabled, int mx, int my) {
        boolean hovered = enabled && inB(mx, my, x, y, w, h);
        int bg = active ? COLOR_ACTIVE_FILL : (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
        int border = active ? COLOR_CYAN : (enabled ? COLOR_FRAME : COLOR_FRAME_FAINT);
        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, 1, border);
        int c = !enabled ? COLOR_DIM : (active ? COLOR_CYAN : COLOR_LABEL);
        drawScaledCentered(g, label, x + w / 2, y + (h - 8) / 2, w - 4, c);
    }

    // ── BUY: catalog ──────────────────────────────────────────────────────────

    /** One catalog card's rectangle under the CURRENT scroll — the single geometry source shared
     *  by drawing, click hit-testing, and hover tooltips, so a card can never render in one place
     *  while keeping a hitbox in another. */
    private Rect catalogCardRect(Regions l, int index) {
        int cw = (l.catalog().w() - PAD * (CATALOG_COLS - 1)) / CATALOG_COLS;
        int row = index / CATALOG_COLS - catalogScrollRows;
        int col = index % CATALOG_COLS;
        int cx = l.catalog().x() + col * (cw + PAD);
        int cy = l.contentY() + row * CATALOG_ROW_H;
        return new Rect(cx, cy, cw, CATALOG_ROW_H - PAD);
    }

    private void drawBuyCatalog(GuiGraphicsExtractor g, Regions l, int mx, int my) {
        List<ShopEntryDisplayData> sells = state.sells();
        Rect cat = l.catalog();
        int visibleRows = TradingScreenLayout.visibleCatalogRows(l.contentH());
        int totalRows = TradingScreenLayout.totalCatalogRows(sells.size());
        int maxScroll = TradingScreenLayout.maxScrollRows(sells.size(), l.contentH());

        try (CloseableScissor ignored = new CloseableScissor(g, cat.x(), cat.y(), cat.w(), cat.h())) {
            for (int i = 0; i < sells.size(); i++) {
                int row = i / CATALOG_COLS - catalogScrollRows;
                if (row < -1 || row > visibleRows) continue;
                Rect card = catalogCardRect(l, i);
                drawBuyCard(g, sells.get(i), i, card.x(), card.y(), card.w(), card.h(), mx, my);
            }
        }

        if (maxScroll > 0) {
            int trackH = cat.h();
            int thumbH = Math.max(10, trackH * visibleRows / totalRows);
            int thumbY = cat.y() + (int) ((trackH - thumbH) * (catalogScrollRows / (float) maxScroll));
            int barX = cat.right() - 2;
            g.fill(barX, cat.y(), barX + 2, cat.bottom(), COLOR_FRAME_FAINT);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_FRAME);
        }
    }

    private void drawBuyCard(GuiGraphicsExtractor g, ShopEntryDisplayData entry, int index,
                              int x, int y, int w, int h, int mx, int my) {
        boolean hovered = inB(mx, my, x, y, w, h);
        boolean isSelected = index == selectedBuyIndex;
        boolean soldOut = entry.soldOut();
        int bg = isSelected ? COLOR_ACTIVE_FILL : (hovered && !soldOut ? COLOR_CELL_HOV : COLOR_CELL_BG);

        g.fill(x, y, x + w, y + h, bg);

        // Icon left, name and price stacked to its right — the centered-column arrangement drew
        // the price line straight through the item icon at these card sizes.
        if (!entry.stack().isEmpty()) {
            g.item(entry.stack(), x + 3, y + (h - 16) / 2);
        }
        int textX = x + 22;
        int textW = w - 25;
        drawScaledLeft(g, displayNameFor(entry.stack()), textX, y + 4, textW,
                soldOut ? COLOR_DIM : COLOR_LABEL);

        String priceLine = CREDITS_SYMBOL + TradingScreenLayout.formatCredits(entry.price());
        if (entry.limitedStock() && !soldOut) priceLine += " (" + entry.availableStock() + ")";
        int priceColor = soldOut ? COLOR_DIM : (entry.affordable() ? COLOR_GOLD : COLOR_RED);
        drawScaledLeft(g, priceLine, textX, y + 16, textW, priceColor);

        if (soldOut) {
            // Overlay FIRST, label after — the previous order dimmed the "Sold Out" text under
            // its own overlay, which is exactly why the state read as unclear in manual testing.
            g.fill(x, y, x + w, y + h, COLOR_SOLDOUT_OVERLAY);
            String soldOutLabel = Component.translatable("totality.trading.sold_out")
                    .getString().toUpperCase(Locale.ROOT);
            drawScaledCentered(g, soldOutLabel, x + w / 2, y + h / 2 - 4, w - 6, COLOR_RED);
        }

        int border = isSelected ? COLOR_CYAN
                : (soldOut ? COLOR_RED : (hovered ? COLOR_FRAME_HOV : COLOR_FRAME));
        drawFrame(g, x, y, w, h, isSelected ? 2 : 1, border);
    }

    /** Enchanted items show their enchantment(s) directly (e.g. "Sharpness III") instead of a
     *  generic "Enchanted Book" name — no need to select the item first to see what it actually is. */
    private String displayNameFor(ItemStack stack) {
        ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
        if (stored == null || stored.isEmpty()) stored = stack.get(DataComponents.ENCHANTMENTS);
        if (stored != null && !stored.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (var entry : stored.entrySet()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(Enchantment.getFullname(entry.getKey(), entry.getIntValue()).getString());
            }
            return sb.toString();
        }
        return stack.getHoverName().getString();
    }

    // ── BUY: detail panel ────────────────────────────────────────────────────

    private void drawBuyDetail(GuiGraphicsExtractor g, Regions l, int mx, int my) {
        Rect d = l.detail();
        int x = d.x(), y = d.y(), w = d.w(), h = d.h();
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_FRAME);

        if (selectedBuyIndex < 0 || selectedBuyIndex >= state.sells().size()) {
            drawSelectHint(g, x, y, w, h);
            return;
        }

        ShopEntryDisplayData entry = state.sells().get(selectedBuyIndex);
        ItemStack stack = entry.stack();
        int maxQ = computeMaxBuyQuantity(selectedBuyIndex);
        int bandTop = y + h - DETAIL_BOTTOM_BAND_H;

        // The top field flow is scissored to the reserved bottom action band's edge (and each
        // line additionally skipped once it would cross it) — at NO window size can fields draw
        // under the quantity/total/button controls, which was the GUI Scale 4 overlap bug.
        try (CloseableScissor ignored = new CloseableScissor(g, x + 1, y + 1, w - 2, Math.max(1, bandTop - y - 1))) {
            int cy = drawDetailHeader(g, x, y, w, stack);

            drawLabelValueRow(g, x + PAD, cy, w - PAD * 2,
                    Component.translatable("totality.trading.price_each").getString() + ":",
                    CREDITS_SYMBOL + TradingScreenLayout.formatCredits(entry.price()), COLOR_GOLD);
            cy += 10;

            if (entry.limitedStock() && cy <= bandTop - 9) {
                drawLabelValueRow(g, x + PAD, cy, w - PAD * 2,
                        Component.translatable("totality.trading.stock").getString() + ":",
                        String.valueOf(entry.availableStock()), entry.soldOut() ? COLOR_RED : COLOR_LABEL);
                cy += 10;
            }

            if (entry.soldOut() && cy <= bandTop - 9) {
                // Explicit sold-out statement in the detail panel — the catalog card's overlay
                // alone shouldn't be the only place the state is communicated.
                g.text(font, Component.translatable("totality.trading.sold_out"), x + PAD, cy, COLOR_RED, false);
                cy += 10;
            }

            cy += 2;
            for (Component line : tooltipLines(stack)) {
                String text = line.getString();
                if (text.isEmpty()) continue;
                for (String wline : wrap(text, w - PAD * 2)) {
                    if (cy > bandTop - 9) break;
                    g.text(font, Component.literal(wline), x + PAD, cy, COLOR_DIM, false);
                    cy += 9;
                }
            }
        }

        drawQuantityRow(g, x + PAD, y + h - QTY_ROW_OFFSET, w - PAD * 2, buyQuantity, maxQ, mx, my);

        long total = TradingQuantityMath.checkedTotal(entry.price(), buyQuantity);
        boolean canConfirm = buyQuantity > 0 && total >= 0;
        drawLabelValueRow(g, x + PAD, y + h - TOTAL_ROW_OFFSET, w - PAD * 2,
                Component.translatable("totality.trading.total_cost").getString() + ":",
                CREDITS_SYMBOL + (total < 0 ? "?" : TradingScreenLayout.formatCredits(total)),
                canConfirm ? COLOR_GOLD : COLOR_DIM);

        drawConfirmCancel(g, x, y, w, h, true, canConfirm, mx, my);
    }

    /** Detail header shared by BUY/SELL: framed icon box + item name, then a divider with a small
     *  center accent (the references' diamond marker rendered as a plain square). Returns the y
     *  where field rows start. */
    private int drawDetailHeader(GuiGraphicsExtractor g, int x, int y, int w, ItemStack stack) {
        int iconX = x + PAD, iconY = y + PAD;
        g.fill(iconX, iconY, iconX + 20, iconY + 20, COLOR_PANEL_BG);
        drawFrame(g, iconX, iconY, 20, 20, 1, COLOR_FRAME);
        g.item(stack, iconX + 2, iconY + 2);
        drawScaledLeft(g, displayNameFor(stack), iconX + 26, iconY + 6, w - PAD * 2 - 26, COLOR_LABEL);

        int cy = iconY + 24;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_GOLD_DIM);
        g.fill(x + w / 2 - 1, cy - 1, x + w / 2 + 2, cy + 2, COLOR_GOLD);
        return cy + 5;
    }

    // ── SELL: detail panel (the left pane is the inventory grid, see drawInventoryRow) ─

    private void drawSellDetail(GuiGraphicsExtractor g, Regions l, int mx, int my) {
        Rect d = l.detail();
        int x = d.x(), y = d.y(), w = d.w(), h = d.h();
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_FRAME);

        ItemStack stack = selectedSellSlot >= 0 ? playerStack(selectedSellSlot) : ItemStack.EMPTY;
        if (selectedSellSlot < 0 || stack.isEmpty()) {
            drawSelectHint(g, x, y, w, h);
            return;
        }

        // Quantity controls only ever offered when the item itself is sellable AND the merchant
        // has any Credits at all — a zero-Credit merchant is a distinct, non-confirmation-eligible
        // state (design document Part A), never presented as "sell for ₵0". Merchant affordability
        // for the FULL requested value no longer bounds this — see the underfunded confirmation
        // flow below instead.
        boolean itemSellable = sellQuote != null && sellQuote.accepted() && sellQuote.hasValue();
        boolean merchantHasCredits = state.merchantCredits() > 0;
        boolean canShowQuantityControls = itemSellable && merchantHasCredits && sellQuote.effectiveMaxQuantity() > 0;
        int bandTop = y + h - DETAIL_BOTTOM_BAND_H;

        try (CloseableScissor ignored = new CloseableScissor(g, x + 1, y + 1, w - 2, Math.max(1, bandTop - y - 1))) {
            int cy = drawDetailHeader(g, x, y, w, stack);

            if (sellQuote == null) {
                g.text(font, Component.translatable("totality.trading.quote_loading"), x + PAD, cy, COLOR_DIM, false);
            } else if (!sellQuote.accepted()) {
                g.text(font, Component.translatable("totality.trading.not_accepted"), x + PAD, cy, COLOR_RED, false);
            } else if (!sellQuote.hasValue()) {
                g.text(font, Component.translatable("totality.trading.no_value"), x + PAD, cy, COLOR_RED, false);
            } else if (!merchantHasCredits) {
                for (String wline : wrap(Component.translatable("totality.trading.merchant_zero_credits").getString(),
                        w - PAD * 2)) {
                    if (cy > bandTop - 9) break;
                    g.text(font, Component.literal(wline), x + PAD, cy, COLOR_RED, false);
                    cy += 9;
                }
            } else {
                drawLabelValueRow(g, x + PAD, cy, w - PAD * 2,
                        Component.translatable("totality.trading.payout_each").getString() + ":",
                        CREDITS_SYMBOL + TradingScreenLayout.formatCredits(sellQuote.unitPayout()), COLOR_GOLD);
                cy += 10;

                if (cy <= bandTop - 9) {
                    drawLabelValueRow(g, x + PAD, cy, w - PAD * 2,
                            Component.translatable("totality.trading.stock").getString() + ":",
                            String.valueOf(sellQuote.stackCount()), COLOR_LABEL);
                    cy += 10;
                }

                // Canonical presentation (Part A): the full quoted value never disappears, but
                // when the merchant can't fully afford it, show separately what it CAN pay and
                // what the player would forfeit — never silently substituted for the real total.
                long fullValue = TradingQuantityMath.checkedTotal(sellQuote.unitPayout(), sellQuantity);
                long merchantCredits = state.merchantCredits();
                long payableAmount = fullValue < 0 ? 0 : Math.min(fullValue, merchantCredits);
                long forfeitedValue = fullValue < 0 ? 0 : fullValue - payableAmount;
                if (forfeitedValue > 0 && cy <= bandTop - 9) {
                    drawLabelValueRow(g, x + PAD, cy, w - PAD * 2,
                            Component.translatable("totality.trading.merchant_can_pay").getString() + ":",
                            CREDITS_SYMBOL + TradingScreenLayout.formatCredits(payableAmount), COLOR_GOLD);
                    cy += 10;
                    if (cy <= bandTop - 9) {
                        drawLabelValueRow(g, x + PAD, cy, w - PAD * 2,
                                Component.translatable("totality.trading.forfeited_value").getString() + ":",
                                CREDITS_SYMBOL + TradingScreenLayout.formatCredits(forfeitedValue), COLOR_RED_SOFT);
                        cy += 10;
                    }
                }
            }
        }

        if (canShowQuantityControls) {
            drawQuantityRow(g, x + PAD, y + h - QTY_ROW_OFFSET, w - PAD * 2,
                    sellQuantity, sellQuote.effectiveMaxQuantity(), mx, my);

            // The real full quote — NEVER clamped to the merchant's current Credits (Part A: "the
            // ordinary SELL detail panel must continue showing the real full quote").
            long total = TradingQuantityMath.checkedTotal(sellQuote.unitPayout(), sellQuantity);
            boolean canConfirm = sellQuantity > 0 && total >= 0;
            drawLabelValueRow(g, x + PAD, y + h - TOTAL_ROW_OFFSET, w - PAD * 2,
                    Component.translatable("totality.trading.total_value").getString() + ":",
                    CREDITS_SYMBOL + (total < 0 ? "?" : TradingScreenLayout.formatCredits(total)),
                    canConfirm ? COLOR_GOLD : COLOR_DIM);

            drawConfirmCancel(g, x, y, w, h, true, canConfirm, mx, my);
        } else {
            // Rejected/still-loading/zero-Credit selection: no quantity controls or SELL button (a
            // disabled action for an impossible sale would be noise), but the clear/cancel button
            // stays available so the selection can be dismissed from the panel itself.
            drawConfirmCancel(g, x, y, w, h, false, false, mx, my);
        }
    }

    /** Underfunded-merchant confirmation modal (design document Part A) — a dimmed full-screen
     *  overlay with a centered box: wrapped explanatory text, then a Cancel/"Sell for ₵X" pair.
     *  Geometry is recomputed fresh every frame from {@link #pendingSellConfirmation} alone (same
     *  discipline as every other region in this screen) and shared exactly with
     *  {@link #handleSellConfirmationClick} so drawn buttons and their hitboxes can never diverge. */
    private void drawSellConfirmation(GuiGraphicsExtractor g, int mx, int my) {
        if (pendingSellConfirmation == null) return;
        g.fill(0, 0, width, height, 0xB0000000);

        String totalStr = CREDITS_SYMBOL + TradingScreenLayout.formatCredits(pendingSellConfirmation.totalValue());
        String payableStr = CREDITS_SYMBOL + TradingScreenLayout.formatCredits(pendingSellConfirmation.payableAmount());
        String msg = Component.translatable("totality.trading.underfunded_confirm", totalStr, payableStr, payableStr).getString();

        int boxW = Math.min(220, width - 20);
        List<String> lines = wrap(msg, boxW - 16);
        int boxH = lines.size() * 10 + 12 + BUTTON_H + 8;
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;

        g.fill(boxX, boxY, boxX + boxW, boxY + boxH, COLOR_PANEL_BG);
        drawFrame(g, boxX, boxY, boxW, boxH, 2, COLOR_GOLD);

        int ty = boxY + 8;
        for (String line : lines) {
            g.text(font, Component.literal(line), boxX + (boxW - font.width(line)) / 2, ty, COLOR_LABEL, false);
            ty += 10;
        }

        int btnW = (boxW - 24) / 2;
        int by = boxY + boxH - BUTTON_H - 6;
        int cancelX = boxX + 8;
        int confirmX = cancelX + btnW + 8;

        boolean cancelHovered = inB(mx, my, cancelX, by, btnW, BUTTON_H);
        g.fill(cancelX, by, cancelX + btnW, by + BUTTON_H, cancelHovered ? 0xFF32280F : 0xFF1C1608);
        drawFrame(g, cancelX, by, btnW, BUTTON_H, 1, COLOR_GOLD);
        drawScaledCentered(g, Component.translatable("totality.trading.cancel").getString(),
                cancelX + btnW / 2, by + (BUTTON_H - 8) / 2, btnW - 4, COLOR_GOLD);

        String sellLabel = Component.translatable("totality.trading.sell_for", payableStr).getString();
        boolean confirmHovered = inB(mx, my, confirmX, by, btnW, BUTTON_H);
        g.fill(confirmX, by, confirmX + btnW, by + BUTTON_H, confirmHovered ? 0xFF1A4A1A : 0xFF0F2A0F);
        drawFrame(g, confirmX, by, btnW, BUTTON_H, 1, COLOR_GREEN);
        drawScaledCentered(g, sellLabel, confirmX + btnW / 2, by + (BUTTON_H - 8) / 2, btnW - 4, COLOR_GREEN);
    }

    /** Hit-testing mirror of {@link #drawSellConfirmation} — Confirm sends the SELL packet with
     *  explicit consent to the terms captured when the popup opened; Cancel removes no items,
     *  moves no Credits, and changes no merchant state (it never even sends a packet). Either way
     *  the modal closes and input resumes flowing to the rest of the screen. */
    private boolean handleSellConfirmationClick(int mx, int my) {
        PendingSellConfirmation pending = pendingSellConfirmation;
        if (pending == null) return false;

        String totalStr = CREDITS_SYMBOL + TradingScreenLayout.formatCredits(pending.totalValue());
        String payableStr = CREDITS_SYMBOL + TradingScreenLayout.formatCredits(pending.payableAmount());
        String msg = Component.translatable("totality.trading.underfunded_confirm", totalStr, payableStr, payableStr).getString();
        int boxW = Math.min(220, width - 20);
        List<String> lines = wrap(msg, boxW - 16);
        int boxH = lines.size() * 10 + 12 + BUTTON_H + 8;
        int boxX = (width - boxW) / 2;
        int boxY = (height - boxH) / 2;
        int btnW = (boxW - 24) / 2;
        int by = boxY + boxH - BUTTON_H - 6;
        int cancelX = boxX + 8;
        int confirmX = cancelX + btnW + 8;

        if (inB(mx, my, confirmX, by, btnW, BUTTON_H)) {
            click();
            ClientPlayNetworking.send(new SellItemPayload(
                    pending.slotIndex(), pending.quantity(), true, pending.totalValue(), pending.payableAmount()));
            pendingSellConfirmation = null;
            return true;
        }
        if (inB(mx, my, cancelX, by, btnW, BUTTON_H)) {
            click();
            pendingSellConfirmation = null;
            return true;
        }
        return true; // modal — swallow every other click while open
    }

    private void drawSelectHint(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        String hint = Component.translatable("totality.trading.select_an_item").getString();
        drawScaledCentered(g, hint, x + w / 2, y + h / 2 - 4, w - PAD * 2, COLOR_DIM);
    }

    // ── BUYBACK placeholder ───────────────────────────────────────────────────

    private void drawBuybackPlaceholder(GuiGraphicsExtractor g, Regions l) {
        Rect c = l.content();
        g.fill(c.x(), c.y(), c.right(), c.bottom(), COLOR_CELL_BG);
        drawFrame(g, c.x(), c.y(), c.w(), c.h(), 1, COLOR_FRAME);
        String msg = Component.translatable("totality.trading.buyback").getString() + " — "
                + Component.translatable("totality.trading.coming_later").getString();
        drawScaledCentered(g, msg, c.x() + c.w() / 2, c.y() + c.h() / 2 - 4, c.w() - PAD * 2, COLOR_DIM);
    }

    // ── Quantity control (shared BUY/SELL — "Quantity:  [-] [ n ] [+]") ──────

    private record QtyRects(int minusX, int boxX, int boxW, int plusX, int btnW, int h) {}

    /** Label left, compact controls right-aligned (reference row treatment) — shared by drawing
     *  and click hit-testing. */
    private QtyRects qtyRects(int x, int y, int w) {
        int btnW = 14, boxW = 34, h = 14;
        int plusX = x + w - btnW;
        int boxX = plusX - 3 - boxW;
        int minusX = boxX - 3 - btnW;
        return new QtyRects(minusX, boxX, boxW, plusX, btnW, h);
    }

    private void drawQuantityRow(GuiGraphicsExtractor g, int x, int y, int w, int quantity, int maxQuantity, int mx, int my) {
        boolean active = maxQuantity > 0;
        QtyRects r = qtyRects(x, y, w);

        drawScaledLeft(g, Component.translatable("totality.trading.quantity").getString() + ":",
                x, y + 3, Math.max(20, r.minusX() - x - 4), active ? COLOR_LABEL : COLOR_DIM);

        drawSmallButton(g, "-", r.minusX(), y, r.btnW(), r.h(), active && quantity > 1, mx, my);
        drawSmallButton(g, "+", r.plusX(), y, r.btnW(), r.h(), active && quantity < maxQuantity, mx, my);

        boolean boxHovered = active && inB(mx, my, r.boxX(), y, r.boxW(), r.h());
        g.fill(r.boxX(), y, r.boxX() + r.boxW(), y + r.h(),
                editingQuantity ? 0xFF1A2A3A : (boxHovered ? COLOR_CELL_HOV : COLOR_PANEL_BG));
        drawFrame(g, r.boxX(), y, r.boxW(), r.h(), 1,
                editingQuantity ? COLOR_CYAN : (active ? COLOR_FRAME : COLOR_FRAME_FAINT));

        String label = editingQuantity ? editBuffer + "|" : (active ? String.valueOf(quantity) : "0");
        g.text(font, Component.literal(label), r.boxX() + r.boxW() / 2 - font.width(label) / 2, y + (r.h() - 8) / 2,
                editingQuantity ? COLOR_CYAN : (active ? COLOR_LABEL : COLOR_DIM), false);
    }

    private void drawSmallButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                                  boolean enabled, int mx, int my) {
        boolean hovered = enabled && inB(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? COLOR_CELL_HOV : COLOR_PANEL_BG);
        drawFrame(g, x, y, w, h, 1, enabled ? COLOR_FRAME : COLOR_FRAME_FAINT);
        int c = enabled ? COLOR_LABEL : COLOR_DIM;
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, c, false);
    }

    /** The action button pair, reference style: full-width split, cancel/clear left and the
     *  confirm right, colored per mode — BUY pairs a gold Cancel with a cyan Buy; SELL pairs a
     *  red Clear with a green Sell. */
    private void drawConfirmCancel(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH,
                                    boolean showConfirm, boolean canConfirm, int mx, int my) {
        int by = panelY + panelH - BUTTON_ROW_OFFSET;
        int totalW = panelW - PAD * 2;
        int btnW = (totalW - 4) / 2;
        int cancelX = panelX + PAD;
        int confirmX = cancelX + btnW + 4;

        boolean sell = mode == Mode.SELL;
        String cancelLabel = Component.translatable(sell ? "totality.trading.clear" : "totality.trading.cancel").getString();
        int cancelBorder = sell ? COLOR_RED : COLOR_GOLD;
        boolean cancelHovered = inB(mx, my, cancelX, by, btnW, BUTTON_H);
        int cancelFill = sell ? (cancelHovered ? 0xFF3A1414 : 0xFF200C0C)
                              : (cancelHovered ? 0xFF32280F : 0xFF1C1608);
        g.fill(cancelX, by, cancelX + btnW, by + BUTTON_H, cancelFill);
        drawFrame(g, cancelX, by, btnW, BUTTON_H, 1, cancelBorder);
        drawScaledCentered(g, cancelLabel, cancelX + btnW / 2, by + (BUTTON_H - 8) / 2, btnW - 4, cancelBorder);

        if (showConfirm) {
            String confirmLabel = Component.translatable(sell ? "totality.trading.sell" : "totality.trading.buy").getString();
            int confirmBorder = canConfirm ? (sell ? COLOR_GREEN : COLOR_CYAN) : COLOR_DIM;
            boolean confirmHovered = canConfirm && inB(mx, my, confirmX, by, btnW, BUTTON_H);
            int confirmFill = !canConfirm ? COLOR_PANEL_BG
                    : sell ? (confirmHovered ? 0xFF1A4A1A : 0xFF0F2A0F)
                           : (confirmHovered ? 0xFF14405A : COLOR_ACTIVE_FILL);
            g.fill(confirmX, by, confirmX + btnW, by + BUTTON_H, confirmFill);
            drawFrame(g, confirmX, by, btnW, BUTTON_H, 1, confirmBorder);
            drawScaledCentered(g, confirmLabel, confirmX + btnW / 2, by + (BUTTON_H - 8) / 2, btnW - 4, confirmBorder);
        }
    }

    // ── Player inventory row ──────────────────────────────────────────────────

    /**
     * One inventory slot's rectangle — the single geometry source shared by drawing, click
     * hit-testing, and hover tooltips. Per-mode (the references' split): in SELL the full grid
     * (storage rows 9-35, then the hotbar 0-8 below) lives inside the LEFT panel under its
     * header label; in BUY/BUYBACK only the hotbar row exists, in the bottom strip — storage
     * slots have no on-screen location there and return an empty rect (never hit, never drawn).
     */
    private Rect inventorySlotRect(Regions l, int slotIndex) {
        Rect inv = l.inventory();
        int startX = inv.x() + (inv.w() - TradingScreenLayout.GRID_W) / 2;
        if (mode == Mode.SELL) {
            int startY = inv.y() + PAD + 12; // below the YOUR INVENTORY header
            if (slotIndex < INV_COLS) { // hotbar row, under the storage rows
                int hotbarY = startY + INV_STORAGE_ROWS * (SLOT_SIZE + SLOT_GAP) + 4;
                return new Rect(startX + slotIndex * (SLOT_SIZE + SLOT_GAP), hotbarY, SLOT_SIZE, SLOT_SIZE);
            }
            int idx = slotIndex - INV_COLS;
            int row = idx / INV_COLS, col = idx % INV_COLS;
            return new Rect(startX + col * (SLOT_SIZE + SLOT_GAP),
                    startY + row * (SLOT_SIZE + SLOT_GAP), SLOT_SIZE, SLOT_SIZE);
        }
        if (slotIndex >= INV_COLS) return new Rect(0, 0, 0, 0); // no storage slots in BUY/BUYBACK
        return new Rect(startX + slotIndex * (SLOT_SIZE + SLOT_GAP), inv.y() + PAD, SLOT_SIZE, SLOT_SIZE);
    }

    /** The inventory slot index under the mouse, or -1. */
    private int inventorySlotAt(Regions l, int mx, int my) {
        if (!l.inventory().contains(mx, my)) return -1;
        for (int slot = 0; slot < INV_COLS * (INV_STORAGE_ROWS + 1); slot++) {
            if (inventorySlotRect(l, slot).contains(mx, my)) return slot;
        }
        return -1;
    }

    /** Per-mode inventory presentation: SELL fills the LEFT panel with the header label, the full
     *  grid, and the red-items legend beneath it (trade_screen_sell_v1); BUY/BUYBACK draw the
     *  single-hotbar-row bottom strip with the label in its left margin (trade_screen_buy_v1). */
    private void drawInventoryRow(GuiGraphicsExtractor g, Regions l, int mx, int my) {
        Rect inv = l.inventory();
        g.fill(inv.x(), inv.y(), inv.right(), inv.bottom(), COLOR_CELL_BG);
        drawFrame(g, inv.x(), inv.y(), inv.w(), inv.h(), 1, COLOR_FRAME);

        String invLabel = Component.translatable("totality.trading.your_inventory")
                .getString().toUpperCase(Locale.ROOT);

        if (mode == Mode.SELL) {
            drawScaledLeft(g, invLabel, inv.x() + PAD, inv.y() + PAD, inv.w() - PAD * 2, COLOR_INV_LABEL);

            for (int slot = 0; slot < INV_COLS * (INV_STORAGE_ROWS + 1); slot++) {
                Rect sr = inventorySlotRect(l, slot);
                drawInventorySlot(g, slot, sr.x(), sr.y(), mx, my);
            }

            // Red-items legend under the grid, in the panel's remaining space.
            int gridBottom = inv.y() + PAD + 12
                    + INV_STORAGE_ROWS * (SLOT_SIZE + SLOT_GAP) + 4 + SLOT_SIZE;
            int ty = gridBottom + 5;
            int tx = inv.x() + PAD, tw = inv.w() - PAD * 2;
            for (String wline : wrap(Component.translatable("totality.trading.rejected_legend").getString(), tw)) {
                if (ty > inv.bottom() - 10) break;
                g.text(font, Component.literal(wline), tx, ty, COLOR_RED_SOFT, false);
                ty += 9;
            }
            return;
        }

        int startX = inv.x() + (inv.w() - TradingScreenLayout.GRID_W) / 2;
        int marginW = startX - inv.x() - PAD * 2;
        if (marginW > 24) {
            drawScaledLeft(g, invLabel, inv.x() + PAD,
                    inv.y() + (inv.h() - 8) / 2, marginW, COLOR_INV_LABEL);
        }
        for (int slot = 0; slot < INV_COLS; slot++) {
            Rect sr = inventorySlotRect(l, slot);
            drawInventorySlot(g, slot, sr.x(), sr.y(), mx, my);
        }
    }

    private void drawInventorySlot(GuiGraphicsExtractor g, int slotIndex, int x, int y, int mx, int my) {
        ItemStack stack = playerStack(slotIndex);
        boolean hovered = inB(mx, my, x, y, SLOT_SIZE, SLOT_SIZE);
        boolean selected = mode == Mode.SELL && slotIndex == selectedSellSlot;

        boolean sellableTag = !stack.isEmpty() && stack.is(ModTags.PROVISIONER_BUYS);
        boolean valued = !stack.isEmpty() && state.valuedInventorySlots().contains(slotIndex);

        // Reference treatment: ordinary slots stay quiet (plain frames) — only PROBLEMS are
        // highlighted (rejected red, unvalued gold), plus the bright cyan current selection.
        int border;
        if (stack.isEmpty() || mode != Mode.SELL) {
            border = COLOR_FRAME_FAINT;
        } else if (selected) {
            border = COLOR_CYAN;
        } else if (!sellableTag) {
            border = 0xFF5A2A2A;
        } else if (!valued) {
            border = COLOR_GOLD_DIM;
        } else {
            border = COLOR_FRAME;
        }

        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE,
                hovered && mode == Mode.SELL && !stack.isEmpty() ? COLOR_CELL_HOV : COLOR_PANEL_BG);
        drawFrame(g, x, y, SLOT_SIZE, SLOT_SIZE, selected ? 2 : 1, border);

        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1);
            if (mode == Mode.SELL && !sellableTag) {
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x50990000);
                // Corner ✕ badge (trade_screen_sell_v1 reference) — the red border/tint alone
                // read as an unexplained state in manual testing; the badge marks "cannot be
                // sold" explicitly, and the hover tooltip states the exact reason.
                g.text(font, Component.literal("x"), x + SLOT_SIZE - 7, y + 1, 0xFFFF5555, true);
            } else if (mode == Mode.SELL && !valued) {
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x40000000);
                g.text(font, Component.literal("x"), x + SLOT_SIZE - 7, y + 1, 0xFFCC8833, true);
            }
        }
    }

    private ItemStack playerStack(int slotIndex) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ItemStack.EMPTY;
        Inventory inv = mc.player.getInventory();
        if (slotIndex < 0 || slotIndex >= inv.getContainerSize()) return ItemStack.EMPTY;
        return inv.getItem(slotIndex);
    }

    // ── Hover tooltips ────────────────────────────────────────────────────────

    /**
     * Hover tooltips for inventory slots and catalog cards (drawn last, over everything). In SELL
     * mode a rejected item's tooltip states WHICH established reason applies — merchant doesn't
     * accept the category vs. no known central value ({@link TradingScreenLayout#sellSlotIssueKey},
     * server-derived data only) — so the red border is never an unexplained state.
     */
    private void drawHoverTooltips(GuiGraphicsExtractor g, Regions l, int mx, int my) {
        int slot = inventorySlotAt(l, mx, my);
        if (slot >= 0) {
            ItemStack stack = playerStack(slot);
            if (stack.isEmpty()) return;
            List<Component> lines = new ArrayList<>();
            lines.add(stack.getHoverName());
            if (mode == Mode.SELL) {
                String issue = TradingScreenLayout.sellSlotIssueKey(
                        stack.is(ModTags.PROVISIONER_BUYS), state.valuedInventorySlots().contains(slot));
                lines.add(issue != null
                        ? Component.translatable(issue).withStyle(ChatFormatting.RED)
                        : Component.translatable("totality.trading.sellable_hint").withStyle(ChatFormatting.AQUA));
            }
            g.setComponentTooltipForNextFrame(font, lines, mx, my);
            return;
        }

        if (mode == Mode.BUY && l.catalog().contains(mx, my)) {
            List<ShopEntryDisplayData> sells = state.sells();
            for (int i = 0; i < sells.size(); i++) {
                if (catalogCardRect(l, i).contains(mx, my)) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(Component.literal(displayNameFor(sells.get(i).stack())));
                    if (sells.get(i).soldOut()) {
                        lines.add(Component.translatable("totality.trading.sold_out").withStyle(ChatFormatting.RED));
                    }
                    g.setComponentTooltipForNextFrame(font, lines, mx, my);
                    return;
                }
            }
        }
    }

    // ── Rejection banner ──────────────────────────────────────────────────────

    private void drawRejectionBanner(GuiGraphicsExtractor g, Regions l) {
        if (rejectionKey == null) return;
        String msg = Component.translatable(rejectionKey).getString();

        // Wrap within the panel instead of letting one long line run past its edges; the banner
        // sits at the top of the content band, well clear of the bottom-anchored quantity
        // controls and action buttons.
        int maxTextW = Math.max(40, l.panel().w() - PAD * 2 - 16);
        List<String> lines = wrap(msg, maxTextW);
        int textW = 0;
        for (String line : lines) textW = Math.max(textW, font.width(line));
        int bw = textW + 16;
        int bh = lines.size() * 9 + 5;
        int bx = l.panel().x() + (l.panel().w() - bw) / 2;
        int by = l.contentY() - 2;

        g.fill(bx, by, bx + bw, by + bh, 0xFF3A1414);
        drawFrame(g, bx, by, bw, bh, 1, COLOR_RED);
        int ty = by + 3;
        for (String line : lines) {
            g.text(font, Component.literal(line), bx + (bw - font.width(line)) / 2, ty, COLOR_RED, false);
            ty += 9;
        }
    }

    // ── Tooltip lines (detail panel body text) ────────────────────────────────

    private List<Component> tooltipLines(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            List<Component> vanilla = stack.getTooltipLines(
                    Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL);
            if (vanilla.size() > 1) lines.addAll(vanilla.subList(1, vanilla.size()));
        }
        if (stack.getItem() instanceof TooltipExtension ext) {
            ext.addTooltipLines(stack, font, lines);
        }
        return lines;
    }

    // ── Quantity math (client-side single source of truth) ──────────────────

    private int computeMaxBuyQuantity(int index) {
        if (index < 0 || index >= state.sells().size()) return 0;
        ShopEntryDisplayData entry = state.sells().get(index);
        long funds = state.walletBalance() + state.physicalCredits();
        return TradingQuantityMath.maxBuyQuantity(entry.limitedStock(), entry.availableStock(), entry.price(), funds);
    }

    /** The maximum quantity the CURRENT detail panel legitimately offers — 0 whenever the panel
     *  isn't showing active quantity controls (no selection, sold out, SELL quote still loading,
     *  or SELL quote rejected). Shared by the click handlers and the quantity-edit commit so their
     *  gating can never diverge from what's rendered. */
    private int currentDetailMaxQuantity() {
        if (mode == Mode.BUY) return computeMaxBuyQuantity(selectedBuyIndex);
        if (mode == Mode.SELL && sellQuote != null && sellQuote.accepted() && sellQuote.hasValue()
                && state.merchantCredits() > 0) {
            return sellQuote.effectiveMaxQuantity();
        }
        return 0;
    }

    private void requestSellQuoteFor(int slotIndex) {
        quotedStackSnapshot = playerStack(slotIndex).copy();
        ClientPlayNetworking.send(new RequestSellQuotePayload(slotIndex));
    }

    /** If the selected SELL slot's stack changes identity/count while the screen is open (picked
     *  up a different item, stack partially consumed elsewhere, etc.), the previously fetched
     *  quote no longer describes what's actually there — re-request rather than let the panel
     *  silently show stale numbers; a stack that is now GONE clears the selection outright
     *  instead of quoting an empty slot (which would misreport as "not accepted"). */
    private void maybeRefreshStaleSellQuote() {
        if (mode != Mode.SELL || selectedSellSlot < 0 || quotedStackSnapshot == null) return;
        ItemStack live = playerStack(selectedSellSlot);
        if (live.isEmpty()) {
            clearSellSelection();
            return;
        }
        boolean sameItem = ItemStack.isSameItemSameComponents(live, quotedStackSnapshot);
        if (!sameItem || live.getCount() != quotedStackSnapshot.getCount()) {
            requestSellQuoteFor(selectedSellSlot);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();
        if (pendingSellConfirmation != null) return handleSellConfirmationClick(mx, my);
        if (editingQuantity) commitQuantityEdit();

        Regions l = TradingScreenLayout.compute(width, height, mode == Mode.SELL);

        Rect tabs = l.tabs();
        int tabW = tabs.w() / 3;
        if (inB(mx, my, tabs.x(), tabs.y(), tabW, tabs.h())) { click(); setMode(Mode.BUY); return true; }
        if (inB(mx, my, tabs.x() + tabW, tabs.y(), tabW, tabs.h())) { click(); setMode(Mode.SELL); return true; }
        if (inB(mx, my, tabs.x() + tabW * 2, tabs.y(), tabs.w() - tabW * 2, tabs.h())) { click(); setMode(Mode.BUYBACK); return true; }

        if (mode == Mode.BUY && clickBuyCatalog(mx, my, l)) return true;
        if (mode == Mode.SELL && clickInventoryForSell(mx, my, l)) return true;

        if ((mode == Mode.BUY && selectedBuyIndex >= 0) || (mode == Mode.SELL && selectedSellSlot >= 0)) {
            if (clickDetailControls(mx, my, l)) return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    private boolean clickBuyCatalog(int mx, int my, Regions l) {
        // Hitboxes must match the scissored visuals: a click outside the catalog viewport can
        // never reach a partially-scrolled-out card.
        if (!l.catalog().contains(mx, my)) return false;

        List<ShopEntryDisplayData> sells = state.sells();
        for (int i = 0; i < sells.size(); i++) {
            if (catalogCardRect(l, i).contains(mx, my)) {
                click();
                // Sold-out entries are selectable view-only: their max quantity is 0, so the
                // quantity controls render inactive and BUY stays disabled — no payload possible.
                selectedBuyIndex = i;
                buyQuantity = TradingQuantityMath.clamp(1, computeMaxBuyQuantity(i));
                clearRejection();
                return true;
            }
        }
        return true; // inside the catalog but between cards — consume, no selection change
    }

    private boolean clickInventoryForSell(int mx, int my, Regions l) {
        int slot = inventorySlotAt(l, mx, my);
        if (slot < 0) return false;
        selectSellSlot(slot);
        return true;
    }

    private void selectSellSlot(int slotIndex) {
        if (playerStack(slotIndex).isEmpty()) return;
        click();
        selectedSellSlot = slotIndex;
        sellQuote = null;
        pendingFreshSellSelection = true;
        sellQuantity = 0;
        clearRejection();
        requestSellQuoteFor(slotIndex);
    }

    private boolean clickDetailControls(int mx, int my, Regions l) {
        Rect d = l.detail();
        int x = d.x(), y = d.y(), w = d.w(), h = d.h();

        // Mirror of the render state exactly: quantity controls only exist for BUY selections and
        // accepted+valued SELL quotes; their hitboxes obey the same enabled predicates the drawn
        // buttons show — a minus rendered disabled at quantity 1 must not still decrement to 0.
        boolean sellControlsShown = mode == Mode.SELL
                && sellQuote != null && sellQuote.accepted() && sellQuote.hasValue();
        boolean qtyRowShown = mode == Mode.BUY || sellControlsShown;
        int maxQ = currentDetailMaxQuantity();
        int quantity = mode == Mode.BUY ? buyQuantity : sellQuantity;
        boolean active = qtyRowShown && maxQ > 0;

        int qtyY = y + h - QTY_ROW_OFFSET;
        QtyRects r = qtyRects(x + PAD, qtyY, w - PAD * 2);
        if (inB(mx, my, r.minusX(), qtyY, r.btnW(), r.h())) {
            if (active && quantity > 1) { click(); setQuantity(quantity - 1, maxQ); }
            return true;
        }
        if (inB(mx, my, r.plusX(), qtyY, r.btnW(), r.h())) {
            if (active && quantity < maxQ) { click(); setQuantity(quantity + 1, maxQ); }
            return true;
        }
        if (inB(mx, my, r.boxX(), qtyY, r.boxW(), r.h())) {
            if (active) {
                click();
                editingQuantity = true;
                editBuffer = String.valueOf(Math.max(quantity, 1));
            }
            return true;
        }

        long unitAmount;
        if (mode == Mode.BUY) {
            unitAmount = selectedBuyIndex >= 0 && selectedBuyIndex < state.sells().size()
                    ? state.sells().get(selectedBuyIndex).price() : 0L;
        } else {
            unitAmount = sellQuote != null ? sellQuote.unitPayout() : 0L;
        }
        boolean canConfirm = qtyRowShown && quantity > 0
                && TradingQuantityMath.checkedTotal(unitAmount, quantity) >= 0;

        // Same geometry as drawConfirmCancel: full-width split pair, cancel left, confirm right.
        int by = y + h - BUTTON_ROW_OFFSET;
        int btnW = (w - PAD * 2 - 4) / 2;
        int cancelX = x + PAD;
        int confirmX = cancelX + btnW + 4;
        if (inB(mx, my, confirmX, by, btnW, BUTTON_H)) {
            if (canConfirm) confirmAction();
            return true;
        }
        if (inB(mx, my, cancelX, by, btnW, BUTTON_H)) { click(); clearSelection(); return true; }
        return false;
    }

    private void clearSelection() {
        selectedBuyIndex = -1;
        clearSellSelection();
        clearRejection();
    }

    private void clearSellSelection() {
        selectedSellSlot = -1;
        sellQuote = null;
        sellQuantity = 0;
        quotedStackSnapshot = null;
        pendingFreshSellSelection = false;
        pendingSellConfirmation = null;
    }

    private void clearRejection() {
        rejectionKey = null;
        rejectionTicksLeft = 0;
    }

    private void setQuantity(int desired, int maxQ) {
        int q = TradingQuantityMath.clamp(desired, maxQ);
        if (mode == Mode.BUY) buyQuantity = q; else sellQuantity = q;
    }

    /**
     * BUY submits immediately, unchanged. SELL branches on the underfunded confirmation flow
     * (design document Part A): a fully-affordable sale submits immediately with no confirmation
     * flag set (server treats that identically to before); a sale whose full value exceeds the
     * merchant's current Credits opens the confirmation popup instead of submitting anything —
     * the actual {@link SellItemPayload} is only sent once the player explicitly accepts it via
     * {@link #handleSellConfirmationClick}.
     */
    private void confirmAction() {
        if (mode == Mode.BUY && selectedBuyIndex >= 0) {
            click();
            ClientPlayNetworking.send(new BuyItemPayload(selectedBuyIndex, buyQuantity));
        } else if (mode == Mode.SELL && selectedSellSlot >= 0 && sellQuote != null) {
            long totalValue = TradingQuantityMath.checkedTotal(sellQuote.unitPayout(), sellQuantity);
            long merchantCredits = state.merchantCredits();
            boolean needsConfirmation = totalValue >= 0 && merchantCredits > 0 && totalValue > merchantCredits;
            click();
            if (needsConfirmation) {
                long payableAmount = Math.min(totalValue, merchantCredits);
                pendingSellConfirmation = new PendingSellConfirmation(selectedSellSlot, sellQuantity, totalValue, payableAmount);
            } else {
                ClientPlayNetworking.send(new SellItemPayload(selectedSellSlot, sellQuantity, false, 0L, 0L));
            }
        }
    }

    private void setMode(Mode newMode) {
        this.mode = newMode;
        clearRejection();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Regions l = TradingScreenLayout.compute(width, height, mode == Mode.SELL);
        // The wheel scrolls the region under the pointer — only the BUY catalog scrolls, and only
        // while the pointer is actually over it.
        if (mode == Mode.BUY && l.catalog().contains((int) mouseX, (int) mouseY)) {
            catalogScrollRows = TradingScreenLayout.clampScroll(
                    catalogScrollRows - (int) Math.signum(scrollY), state.sells().size(), l.contentH());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void commitQuantityEdit() {
        editingQuantity = false;
        int maxQ = currentDetailMaxQuantity();
        try {
            int parsed = Integer.parseInt(editBuffer);
            // Minimum valid quantity is 1 whenever transacting is possible — a typed "0" becomes
            // 1 rather than a dead zero state the player then has to click back up from.
            setQuantity(maxQ > 0 ? Math.max(1, parsed) : parsed, maxQ);
        } catch (NumberFormatException ignored) {
            // leave quantity unchanged — e.g. buffer was cleared to empty
        }
        editBuffer = "";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (pendingSellConfirmation != null) {
            // Escape dismisses only the modal (equivalent to Cancel) — it must never fall through
            // to closing the whole Trading Screen and releasing the NPC lock out from under an
            // open confirmation.
            if (event.isEscape()) { pendingSellConfirmation = null; }
            return true;
        }
        if (editingQuantity) {
            if (event.isEscape()) { editingQuantity = false; editBuffer = ""; return true; }
            int key = event.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE && !editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitQuantityEdit();
                return true;
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingQuantity) {
            String s = event.codepointAsString();
            if (s.length() == 1 && Character.isDigit(s.charAt(0)) && editBuffer.length() < 3) {
                editBuffer += s;
            }
            return true;
        }
        return super.charTyped(event);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    private boolean inB(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Renders text scaled down (never up) to fit maxW, centered on centerX. */
    private void drawScaledCentered(GuiGraphicsExtractor g, String text, int centerX, int y, int maxW, int color) {
        int rawW = Math.max(1, font.width(text));
        float scale = Math.min(1f, maxW / (float) rawW);
        scale = Math.max(0.4f, scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int sx = Math.round((centerX - rawW * scale / 2f) / scale);
        int sy = Math.round(y / scale);
        g.text(font, Component.literal(text), sx, sy, color, false);
        g.pose().popMatrix();
    }

    /** Same scaling rule as {@link #drawScaledCentered}, left-aligned instead — used for
     *  labels/credit values so an unusually long value shrinks rather than overlapping
     *  neighboring UI. */
    private void drawScaledLeft(GuiGraphicsExtractor g, String text, int x, int y, int maxW, int color) {
        int rawW = Math.max(1, font.width(text));
        float scale = Math.min(1f, maxW / (float) rawW);
        scale = Math.max(0.4f, scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int sx = Math.round(x / scale);
        int sy = Math.round(y / scale);
        g.text(font, Component.literal(text), sx, sy, color, false);
        g.pose().popMatrix();
    }

    /** Renders text scaled UP by a fixed factor, centered on centerX — used only for the header
     *  title (the references' large TRADING treatment); never used for body text. */
    private void drawScaledUpCentered(GuiGraphicsExtractor g, String text, int centerX, int y, float scale, int color) {
        int rawW = Math.max(1, font.width(text));
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int sx = Math.round(centerX / scale - rawW / 2f);
        int sy = Math.round(y / scale);
        g.text(font, Component.literal(text), sx, sy, color, true);
        g.pose().popMatrix();
    }

    private List<String> wrap(String text, int maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (font.width(test) > maxW) {
                if (!cur.isEmpty()) lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else cur = new StringBuilder(test);
        }
        if (!cur.isEmpty()) lines.add(cur.toString());
        return lines;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }

    /** Notifies the server the moment this screen closes (Esc or the Cancel button, both of
     *  which route through here) so {@link zcylas.totality.api.shop.TradeSessionManager} can
     *  release the NPC's interaction lock immediately instead of relying solely on the
     *  distance-based safety net. */
    @Override
    public void onClose() {
        ClientPlayNetworking.send(new CloseTradePayload());
        super.onClose();
    }
}
