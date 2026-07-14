package zcylas.totality.screen.shop;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
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

/**
 * Trading screen (Phase 4 redesign) — visual direction from {@code
 * Context/Audit/Image References/trade_screen.png} (header / BUY-SELL-BUYBACK tabs / catalog +
 * detail + player-inventory layout), rendered with Totality's established flat-color
 * near-black + cyan + gold/amber palette (no stone/gold texture art yet — same "logic now,
 * visuals later" split every other Totality screen uses).
 *
 * <p><b>The Phase 3 quantity bug, root-caused this phase:</b> the OLD screen capped its quantity
 * slider at a flat {@code MAX_QUANTITY = 100} and never read {@link
 * ShopEntryDisplayData#limitedStock()}/{@code availableStock()} at all — so a player could freely
 * select a quantity above a Provisioner entry's REAL current stock. The server correctly rejected
 * the resulting BUY ({@code OUT_OF_STOCK}), but there was no client-visible rejection feedback
 * either, so the purchase simply appeared to do nothing whenever the selected quantity exceeded
 * the entry's actual stock — reproducing exactly the reported "can only successfully buy one item
 * at a time" symptom for any low-stock entry. Fixed by {@link TradingQuantityMath#maxBuyQuantity}
 * (the one client-side source of truth for the valid quantity range, Part C) AND by wiring up real
 * rejection feedback ({@link #showRejection}, Part D) so a future edge case is visible instead of
 * silent.
 *
 * <p>SELL mode is now fully functional (Phase 2's backend), using a live server-computed quote
 * per selected inventory slot ({@link #applySellQuote}) rather than any client-computed payout.
 * BUYBACK is visibly present but inert (Part B — functional buyback is explicitly out of scope).
 */
public class TradingScreen extends Screen {

    private static final int COLOR_BG          = 0xFF05070A;
    private static final int COLOR_PANEL_BG    = 0xFF0A0A0A;
    private static final int COLOR_CELL_BG     = 0xFF101010;
    private static final int COLOR_CELL_HOV    = 0xFF1A1A1A;
    private static final int COLOR_GOLD        = 0xFFD4A030;
    private static final int COLOR_GOLD_DIM    = 0xFF7A5010;
    private static final int COLOR_CYAN        = 0xFF00CCFF;
    private static final int COLOR_LABEL       = 0xFFCCCCCC;
    private static final int COLOR_DIM         = 0xFF888888;
    private static final int COLOR_RED         = 0xFFCC4444;
    private static final int COLOR_GREEN       = 0xFF44CC44;
    private static final int COLOR_SOLDOUT_OVERLAY = 0xB0000000;

    private static final String CREDITS_SYMBOL = "₵"; // ₵ — matches every other Credits display in the mod

    private static final int PAD = 6;
    private static final int CATALOG_COLS = 3;
    private static final int CATALOG_ROW_H = 44;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 2;
    private static final int INV_COLS = 9;
    private static final int INV_STORAGE_ROWS = 3;

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

    public TradingScreen(ShowShopStatePayload initial) {
        super(Component.translatable("totality.trading.title"));
        this.state = initial;
    }

    /** Server-issued state refresh (session start, or after a successful BUY/SELL — Part F: never
     *  sent on a rejected attempt, see {@link #showRejection}). Preserves the current selection and
     *  quantity when still valid; clamps DOWN, never up, otherwise (Part C). */
    public void applyUpdate(ShowShopStatePayload update) {
        this.state = update;
        rejectionKey = null;

        if (selectedBuyIndex >= update.sells().size()) selectedBuyIndex = -1;
        if (selectedBuyIndex >= 0) {
            buyQuantity = TradingQuantityMath.reconcileAfterRefresh(
                    Math.max(buyQuantity, 1), computeMaxBuyQuantity(selectedBuyIndex));
        }

        if (selectedSellSlot >= 0 && !playerStack(selectedSellSlot).isEmpty()) {
            requestSellQuoteFor(selectedSellSlot);
        }
    }

    /** Live SELL quote for the currently selected slot (Part F/D) — ignored if it answers a slot
     *  the player has since deselected (Part G: a stale/late response must never misapply). */
    public void applySellQuote(SellQuoteResultPayload payload) {
        if (payload.slotIndex() != selectedSellSlot) return;
        this.sellQuote = payload;
        int maxQ = payload.effectiveMaxQuantity();
        if (pendingFreshSellSelection) {
            sellQuantity = TradingQuantityMath.clamp(1, maxQ);
            pendingFreshSellSelection = false;
        } else {
            sellQuantity = TradingQuantityMath.reconcileAfterRefresh(Math.max(sellQuantity, 1), maxQ);
        }
    }

    /** Part D: a failed BUY/SELL previously produced no visible feedback at all. */
    public void showRejection(boolean buy, String reasonKey) {
        this.rejectionKey = reasonKey;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        maybeRefreshStaleSellQuote();

        Layout l = layout();
        g.fill(l.panelX, l.panelY, l.panelX + l.panelW, l.panelY + l.panelH, COLOR_PANEL_BG);
        drawFrame(g, l.panelX, l.panelY, l.panelW, l.panelH, 2, COLOR_GOLD_DIM);

        drawHeader(g, l);
        drawTabs(g, l, mx, my);

        switch (mode) {
            case BUY -> {
                drawBuyCatalog(g, l, mx, my);
                drawBuyDetail(g, l, mx, my);
            }
            case SELL -> {
                drawSellHint(g, l);
                drawSellDetail(g, l, mx, my);
            }
            case BUYBACK -> drawBuybackPlaceholder(g, l);
        }

        drawInventoryRow(g, l, mx, my);
        drawRejectionBanner(g, l);
    }

    // ── Layout ────────────────────────────────────────────────────────────────

    private record Layout(
            int panelX, int panelY, int panelW, int panelH,
            int tabsY, int tabsH,
            int contentX, int contentY, int contentW, int contentH,
            int catalogX, int catalogW,
            int detailX, int detailW,
            int invX, int invY, int invW, int invH
    ) {}

    private Layout layout() {
        int px = width / 20, py = height / 14;
        int pw = width - px * 2, ph = height - py * 2;

        int headerH = 30;
        int tabsH = 20;
        int invH = (INV_STORAGE_ROWS + 1) * (SLOT_SIZE + SLOT_GAP) + SLOT_GAP + PAD * 2 + 8;

        int tabsY = py + headerH;
        int contentY = tabsY + tabsH + PAD;
        int invY = py + ph - invH;
        int contentH = Math.max(20, invY - contentY - PAD);
        int contentX = px + PAD;
        int contentW = pw - PAD * 2;

        int catalogW = (int) (contentW * 0.48f);
        int catalogX = contentX;
        int detailX = catalogX + catalogW + PAD;
        int detailW = contentX + contentW - detailX;

        return new Layout(px, py, pw, ph, tabsY, tabsH, contentX, contentY, contentW, contentH,
                catalogX, catalogW, detailX, detailW, contentX, invY, contentW, invH);
    }

    // ── Header ────────────────────────────────────────────────────────────────

    private void drawHeader(GuiGraphicsExtractor g, Layout l) {
        int x = l.panelX, y = l.panelY, w = l.panelW, h = 30;
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_GOLD_DIM);

        String title = Component.translatable("totality.trading.title").getString();
        g.text(font, Component.literal(title), x + PAD, y + 3, COLOR_GOLD, true);

        String merchantLine = state.shopName().getString() + "  ·  " + state.merchantArchetype().getString();
        drawScaledLeft(g, merchantLine, x + PAD, y + 16, (int) (w * 0.55f), COLOR_LABEL);

        String merchantCredits = Component.translatable("totality.trading.merchant_credits").getString()
                + ": " + CREDITS_SYMBOL + formatCredits(state.merchantCredits());
        String playerCredits = Component.translatable("totality.trading.your_credits").getString()
                + ": " + CREDITS_SYMBOL + formatCredits(state.walletBalance() + state.physicalCredits());

        int rightW = (int) (w * 0.42f);
        int rightX = x + w - PAD - rightW;
        drawScaledLeft(g, merchantCredits, rightX, y + 4, rightW, COLOR_GOLD);
        drawScaledLeft(g, playerCredits, rightX, y + 16, rightW, COLOR_CYAN);
    }

    private String formatCredits(long amount) {
        return String.format("%,d", amount);
    }

    // ── Tabs ──────────────────────────────────────────────────────────────────

    private void drawTabs(GuiGraphicsExtractor g, Layout l, int mx, int my) {
        int x = l.panelX, y = l.tabsY, w = l.panelW, h = l.tabsH;
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);

        int tabW = w / 3;
        drawTab(g, Component.translatable("totality.trading.buy").getString(),
                x, y, tabW, h, mode == Mode.BUY, true, mx, my);
        drawTab(g, Component.translatable("totality.trading.sell").getString(),
                x + tabW, y, tabW, h, mode == Mode.SELL, true, mx, my);
        String buybackLabel = Component.translatable("totality.trading.buyback").getString()
                + " (" + Component.translatable("totality.trading.coming_later").getString() + ")";
        drawTab(g, buybackLabel, x + tabW * 2, y, w - tabW * 2, h, mode == Mode.BUYBACK, false, mx, my);
    }

    private void drawTab(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                          boolean active, boolean enabled, int mx, int my) {
        boolean hovered = enabled && inB(mx, my, x, y, w, h);
        int bg = active ? 0xFF14283A : (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
        int border = active ? COLOR_CYAN : COLOR_GOLD_DIM;
        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, 1, border);
        int c = !enabled ? COLOR_DIM : (active ? COLOR_CYAN : COLOR_LABEL);
        drawScaledCentered(g, label, x + w / 2, y + (h - 8) / 2, w - 4, c);
    }

    // ── BUY: catalog ──────────────────────────────────────────────────────────

    private void drawBuyCatalog(GuiGraphicsExtractor g, Layout l, int mx, int my) {
        List<ShopEntryDisplayData> sells = state.sells();
        int cw = (l.catalogW - PAD * (CATALOG_COLS - 1)) / CATALOG_COLS;
        int visibleRows = Math.max(1, l.contentH / CATALOG_ROW_H);
        int totalRows = Math.max(1, (sells.size() + CATALOG_COLS - 1) / CATALOG_COLS);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        if (catalogScrollRows > maxScroll) catalogScrollRows = maxScroll;
        if (catalogScrollRows < 0) catalogScrollRows = 0;

        try (CloseableScissor ignored = new CloseableScissor(g, l.catalogX, l.contentY, l.catalogW, l.contentH)) {
            for (int i = 0; i < sells.size(); i++) {
                int row = i / CATALOG_COLS - catalogScrollRows;
                if (row < -1 || row > visibleRows) continue;
                int col = i % CATALOG_COLS;
                int cx = l.catalogX + col * (cw + PAD);
                int cy = l.contentY + row * CATALOG_ROW_H;
                drawBuyCard(g, sells.get(i), i, cx, cy, cw, CATALOG_ROW_H - PAD, mx, my);
            }
        }

        if (maxScroll > 0) {
            int trackH = l.contentH;
            int thumbH = Math.max(10, trackH * visibleRows / totalRows);
            int thumbY = l.contentY + (int) ((trackH - thumbH) * (catalogScrollRows / (float) maxScroll));
            int barX = l.catalogX + l.catalogW - 2;
            g.fill(barX, l.contentY, barX + 2, l.contentY + trackH, 0xFF1A1A1A);
            g.fill(barX, thumbY, barX + 2, thumbY + thumbH, COLOR_GOLD_DIM);
        }
    }

    private void drawBuyCard(GuiGraphicsExtractor g, ShopEntryDisplayData entry, int index,
                              int x, int y, int w, int h, int mx, int my) {
        boolean hovered = inB(mx, my, x, y, w, h);
        boolean isSelected = index == selectedBuyIndex;
        boolean soldOut = entry.soldOut();
        int bg = isSelected ? 0xFF14283A : (hovered && !soldOut ? COLOR_CELL_HOV : COLOR_CELL_BG);
        int border = isSelected ? COLOR_CYAN : (soldOut ? COLOR_RED : (hovered ? COLOR_GOLD : COLOR_GOLD_DIM));

        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, isSelected ? 2 : 1, border);

        String name = displayNameFor(entry.stack());
        drawScaledCentered(g, name, x + w / 2, y + 3, w - 6, soldOut ? COLOR_DIM : COLOR_LABEL);

        if (!entry.stack().isEmpty()) {
            g.item(entry.stack(), x + w / 2 - 8, y + 14);
        }

        String priceLine;
        int priceColor;
        if (soldOut) {
            priceLine = Component.translatable("totality.trading.sold_out").getString();
            priceColor = COLOR_RED;
        } else {
            priceLine = CREDITS_SYMBOL + entry.price();
            if (entry.limitedStock()) priceLine += "  (" + entry.availableStock() + ")";
            priceColor = entry.affordable() ? COLOR_GOLD : COLOR_RED;
        }
        g.text(font, Component.literal(priceLine), x + w / 2 - font.width(priceLine) / 2, y + h - 11, priceColor, false);

        if (soldOut) {
            g.fill(x, y, x + w, y + h, COLOR_SOLDOUT_OVERLAY);
        }
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

    private void drawBuyDetail(GuiGraphicsExtractor g, Layout l, int mx, int my) {
        int x = l.detailX, y = l.contentY, w = l.detailW, h = l.contentH;
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_GOLD_DIM);

        if (selectedBuyIndex < 0 || selectedBuyIndex >= state.sells().size()) {
            drawSelectHint(g, x, y, w, h);
            return;
        }

        ShopEntryDisplayData entry = state.sells().get(selectedBuyIndex);
        ItemStack stack = entry.stack();
        int maxQ = computeMaxBuyQuantity(selectedBuyIndex);

        int iconX = x + PAD, iconY = y + PAD;
        g.item(stack, iconX, iconY);
        drawScaledLeft(g, displayNameFor(stack), iconX + 22, iconY + 4, w - PAD * 2 - 22, COLOR_LABEL);

        int cy = iconY + 22;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_GOLD_DIM);
        cy += 4;

        String priceLabel = Component.translatable("totality.trading.price_each").getString() + ": "
                + CREDITS_SYMBOL + entry.price();
        g.text(font, Component.literal(priceLabel), x + PAD, cy, COLOR_GOLD, false);
        cy += 10;

        if (entry.limitedStock()) {
            String stockLabel = Component.translatable("totality.trading.stock").getString() + ": " + entry.availableStock();
            g.text(font, Component.literal(stockLabel), x + PAD, cy, entry.soldOut() ? COLOR_RED : COLOR_LABEL, false);
            cy += 10;
        }

        int tooltipTop = cy + 2;
        int tooltipBottom = y + h - 46;
        for (Component line : tooltipLines(stack)) {
            String text = line.getString();
            if (text.isEmpty()) continue;
            for (String wline : wrap(text, w - PAD * 2)) {
                if (cy > tooltipBottom) break;
                g.text(font, Component.literal(wline), x + PAD, cy, COLOR_DIM, false);
                cy += 9;
            }
        }

        int qtyY = y + h - 40;
        drawQuantityRow(g, x + PAD, qtyY, w - PAD * 2, buyQuantity, maxQ, mx, my);

        long total = TradingQuantityMath.checkedTotal(entry.price(), buyQuantity);
        boolean canConfirm = buyQuantity > 0 && total >= 0;
        String totalStr = Component.translatable("totality.trading.total_cost").getString() + ": "
                + CREDITS_SYMBOL + (total < 0 ? "?" : formatCredits(total));
        g.text(font, Component.literal(totalStr), x + PAD, qtyY + 16, canConfirm ? COLOR_GOLD : COLOR_DIM, false);

        drawConfirmCancel(g, x, y, w, h, canConfirm, mx, my);
    }

    // ── SELL: left hint + inventory-driven selection ─────────────────────────

    private void drawSellHint(GuiGraphicsExtractor g, Layout l) {
        int x = l.catalogX, y = l.contentY, w = l.catalogW, h = l.contentH;
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_GOLD_DIM);
        String hint = Component.translatable("totality.trading.select_an_item").getString();
        drawScaledCentered(g, hint, x + w / 2, y + h / 2 - 4, w - PAD * 2, COLOR_DIM);
    }

    private void drawSellDetail(GuiGraphicsExtractor g, Layout l, int mx, int my) {
        int x = l.detailX, y = l.contentY, w = l.detailW, h = l.contentH;
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_GOLD_DIM);

        ItemStack stack = selectedSellSlot >= 0 ? playerStack(selectedSellSlot) : ItemStack.EMPTY;
        if (selectedSellSlot < 0 || stack.isEmpty()) {
            drawSelectHint(g, x, y, w, h);
            return;
        }

        int iconX = x + PAD, iconY = y + PAD;
        g.item(stack, iconX, iconY);
        drawScaledLeft(g, displayNameFor(stack), iconX + 22, iconY + 4, w - PAD * 2 - 22, COLOR_LABEL);

        int cy = iconY + 22;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_GOLD_DIM);
        cy += 4;

        if (sellQuote == null) {
            g.text(font, Component.translatable("totality.trading.quote_loading"), x + PAD, cy, COLOR_DIM, false);
            return;
        }

        if (!sellQuote.accepted()) {
            g.text(font, Component.translatable("totality.trading.not_accepted"), x + PAD, cy, COLOR_RED, false);
            return;
        }
        if (!sellQuote.hasValue()) {
            g.text(font, Component.translatable("totality.trading.no_value"), x + PAD, cy, COLOR_RED, false);
            return;
        }

        String payoutLabel = Component.translatable("totality.trading.payout_each").getString() + ": "
                + CREDITS_SYMBOL + sellQuote.unitPayout();
        g.text(font, Component.literal(payoutLabel), x + PAD, cy, COLOR_GOLD, false);
        cy += 10;

        String stockLabel = Component.translatable("totality.trading.stock").getString() + ": " + sellQuote.stackCount();
        g.text(font, Component.literal(stockLabel), x + PAD, cy, COLOR_LABEL, false);
        cy += 10;

        if (sellQuote.effectiveMaxQuantity() <= 0) {
            g.text(font, Component.translatable("totality.trading.merchant_cannot_afford"), x + PAD, cy, COLOR_RED, false);
        }

        int qtyY = y + h - 40;
        drawQuantityRow(g, x + PAD, qtyY, w - PAD * 2, sellQuantity, sellQuote.effectiveMaxQuantity(), mx, my);

        long total = TradingQuantityMath.checkedTotal(sellQuote.unitPayout(), sellQuantity);
        boolean canConfirm = sellQuantity > 0 && total >= 0;
        String totalStr = Component.translatable("totality.trading.total_payout").getString() + ": "
                + CREDITS_SYMBOL + (total < 0 ? "?" : formatCredits(total));
        g.text(font, Component.literal(totalStr), x + PAD, qtyY + 16, canConfirm ? COLOR_GOLD : COLOR_DIM, false);

        drawConfirmCancel(g, x, y, w, h, canConfirm, mx, my);
    }

    private void drawSelectHint(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        String hint = Component.translatable("totality.trading.select_an_item").getString();
        drawScaledCentered(g, hint, x + w / 2, y + h / 2 - 4, w - PAD * 2, COLOR_DIM);
    }

    // ── BUYBACK placeholder ───────────────────────────────────────────────────

    private void drawBuybackPlaceholder(GuiGraphicsExtractor g, Layout l) {
        int x = l.contentX, y = l.contentY, w = l.contentW, h = l.contentH;
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, COLOR_GOLD_DIM);
        String msg = Component.translatable("totality.trading.buyback").getString() + " — "
                + Component.translatable("totality.trading.coming_later").getString();
        drawScaledCentered(g, msg, x + w / 2, y + h / 2 - 4, w - PAD * 2, COLOR_DIM);
    }

    // ── Quantity control (shared BUY/SELL) ────────────────────────────────────

    private record QtyRects(int minusX, int minusW, int boxX, int boxW, int plusX, int plusW, int h) {}

    private QtyRects qtyRects(int x, int y, int w, int h) {
        int btnW = 16;
        int boxW = Math.max(36, w - btnW * 2 - 8);
        int minusX = x, plusX = x + btnW + 4 + boxW + 4;
        return new QtyRects(minusX, btnW, minusX + btnW + 4, boxW, plusX, btnW, h);
    }

    private void drawQuantityRow(GuiGraphicsExtractor g, int x, int y, int w, int quantity, int maxQuantity, int mx, int my) {
        boolean active = maxQuantity > 0;
        QtyRects r = qtyRects(x, y, w, 14);

        drawSmallButton(g, "-", r.minusX, y, r.minusW, r.h, active && quantity > 1, mx, my);
        drawSmallButton(g, "+", r.plusX, y, r.plusW, r.h, active && quantity < maxQuantity, mx, my);

        boolean boxHovered = active && inB(mx, my, r.boxX, y, r.boxW, r.h);
        g.fill(r.boxX, y, r.boxX + r.boxW, y + r.h,
                editingQuantity ? 0xFF1A2A3A : (boxHovered ? COLOR_CELL_HOV : 0xFF0A0A0A));
        drawFrame(g, r.boxX, y, r.boxW, r.h, 1, editingQuantity ? COLOR_CYAN : (active ? COLOR_GOLD_DIM : 0xFF333333));

        String qtyLabel = editingQuantity ? editBuffer + "|" : (active ? String.valueOf(quantity) : "0");
        String maxLabel = active ? ("/" + maxQuantity) : "";
        String full = qtyLabel + maxLabel;
        g.text(font, Component.literal(full), r.boxX + r.boxW / 2 - font.width(full) / 2, y + (r.h - 8) / 2,
                editingQuantity ? COLOR_CYAN : (active ? COLOR_LABEL : COLOR_DIM), false);
    }

    private void drawSmallButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                                  boolean enabled, int mx, int my) {
        boolean hovered = enabled && inB(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? COLOR_CELL_HOV : 0xFF0A0A0A);
        drawFrame(g, x, y, w, h, 1, enabled ? COLOR_GOLD_DIM : 0xFF333333);
        int c = enabled ? COLOR_LABEL : COLOR_DIM;
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, c, false);
    }

    private void drawConfirmCancel(GuiGraphicsExtractor g, int panelX, int panelY, int panelW, int panelH,
                                    boolean canConfirm, int mx, int my) {
        int btnW = 44, btnH = 14;
        int y = panelY + panelH - 16;
        int confirmX = panelX + panelW - PAD - btnW;
        int cancelX = confirmX - 4 - btnW;

        boolean confirmHovered = canConfirm && inB(mx, my, confirmX, y, btnW, btnH);
        g.fill(confirmX, y, confirmX + btnW, y + btnH, canConfirm ? (confirmHovered ? 0xFF1A4A1A : 0xFF0F2F0F) : 0xFF0A0A0A);
        drawFrame(g, confirmX, y, btnW, btnH, 1, canConfirm ? COLOR_GREEN : COLOR_DIM);
        String confirmLabel = (mode == Mode.SELL
                ? Component.translatable("totality.trading.sell")
                : Component.translatable("totality.trading.buy")).getString();
        g.text(font, Component.literal(confirmLabel), confirmX + btnW / 2 - font.width(confirmLabel) / 2,
                y + (btnH - 8) / 2, canConfirm ? COLOR_GREEN : COLOR_DIM, false);

        boolean cancelHovered = inB(mx, my, cancelX, y, btnW, btnH);
        g.fill(cancelX, y, cancelX + btnW, y + btnH, cancelHovered ? 0xFF4A1A1A : 0xFF2F0F0F);
        drawFrame(g, cancelX, y, btnW, btnH, 1, COLOR_RED);
        String x_ = "X";
        g.text(font, Component.literal(x_), cancelX + btnW / 2 - font.width(x_) / 2, y + (btnH - 8) / 2, COLOR_RED, false);
    }

    // ── Player inventory row ──────────────────────────────────────────────────

    private void drawInventoryRow(GuiGraphicsExtractor g, Layout l, int mx, int my) {
        g.fill(l.invX, l.invY, l.invX + l.invW, l.invY + l.invH, COLOR_CELL_BG);
        drawFrame(g, l.invX, l.invY, l.invW, l.invH, 1, COLOR_GOLD_DIM);

        int gridW = INV_COLS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int startX = l.invX + (l.invW - gridW) / 2;
        int startY = l.invY + PAD;

        // Storage rows (slots 9..35), then the hotbar row (slots 0..8) below it — vanilla layout.
        for (int row = 0; row < INV_STORAGE_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int slotIndex = 9 + row * INV_COLS + col;
                int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
                int sy = startY + row * (SLOT_SIZE + SLOT_GAP);
                drawInventorySlot(g, slotIndex, sx, sy, mx, my);
            }
        }
        int hotbarY = startY + INV_STORAGE_ROWS * (SLOT_SIZE + SLOT_GAP) + 4;
        for (int col = 0; col < INV_COLS; col++) {
            int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
            drawInventorySlot(g, col, sx, hotbarY, mx, my);
        }
    }

    private void drawInventorySlot(GuiGraphicsExtractor g, int slotIndex, int x, int y, int mx, int my) {
        ItemStack stack = playerStack(slotIndex);
        boolean hovered = inB(mx, my, x, y, SLOT_SIZE, SLOT_SIZE);
        boolean selected = mode == Mode.SELL && slotIndex == selectedSellSlot;

        boolean sellableTag = !stack.isEmpty() && stack.is(ModTags.PROVISIONER_BUYS);
        boolean valued = !stack.isEmpty() && state.valuedInventorySlots().contains(slotIndex);

        int bg = 0xFF0A0A0A;
        int border;
        if (stack.isEmpty()) {
            border = 0xFF2A2A2A;
        } else if (mode != Mode.SELL) {
            border = 0xFF2A2A2A;
        } else if (selected) {
            border = COLOR_CYAN;
        } else if (!sellableTag) {
            border = 0xFF5A2A2A;
        } else if (!valued) {
            border = COLOR_GOLD_DIM;
        } else {
            border = COLOR_CYAN;
        }

        g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, hovered && mode == Mode.SELL && !stack.isEmpty() ? COLOR_CELL_HOV : bg);
        drawFrame(g, x, y, SLOT_SIZE, SLOT_SIZE, selected ? 2 : 1, border);

        if (!stack.isEmpty()) {
            g.item(stack, x + 1, y + 1);
            if (mode == Mode.SELL && !sellableTag) {
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x50990000);
            } else if (mode == Mode.SELL && !valued) {
                g.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x40000000);
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

    // ── Rejection banner ──────────────────────────────────────────────────────

    private void drawRejectionBanner(GuiGraphicsExtractor g, Layout l) {
        if (rejectionKey == null) return;
        String msg = Component.translatable(rejectionKey).getString();
        int bw = font.width(msg) + 16;
        int bx = l.panelX + (l.panelW - bw) / 2;
        int by = l.contentY - 2;
        g.fill(bx, by, bx + bw, by + 12, 0xFF3A1414);
        drawFrame(g, bx, by, bw, 12, 1, COLOR_RED);
        g.text(font, Component.literal(msg), bx + 8, by + 2, COLOR_RED, false);
    }

    // ── Tooltip lines (reused from the previous screen) ──────────────────────

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

    // ── Quantity math (client-side single source of truth, Part C) ──────────

    private int computeMaxBuyQuantity(int index) {
        if (index < 0 || index >= state.sells().size()) return 0;
        ShopEntryDisplayData entry = state.sells().get(index);
        long funds = state.walletBalance() + state.physicalCredits();
        return TradingQuantityMath.maxBuyQuantity(entry.limitedStock(), entry.availableStock(), entry.price(), funds);
    }

    private void requestSellQuoteFor(int slotIndex) {
        quotedStackSnapshot = playerStack(slotIndex).copy();
        ClientPlayNetworking.send(new RequestSellQuotePayload(slotIndex));
    }

    /** Part G: if the selected SELL slot's stack changes identity/count while the screen is open
     *  (picked up a different item, stack partially consumed elsewhere, etc.), the previously
     *  fetched quote no longer describes what's actually there — re-request rather than let the
     *  panel silently show stale numbers for a different stack. */
    private void maybeRefreshStaleSellQuote() {
        if (mode != Mode.SELL || selectedSellSlot < 0 || quotedStackSnapshot == null) return;
        ItemStack live = playerStack(selectedSellSlot);
        boolean sameItem = ItemStack.isSameItemSameComponents(live, quotedStackSnapshot);
        if (!sameItem || live.getCount() != quotedStackSnapshot.getCount()) {
            requestSellQuoteFor(selectedSellSlot);
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();
        if (editingQuantity) commitQuantityEdit();

        Layout l = layout();

        int tabW = l.panelW / 3;
        if (inB(mx, my, l.panelX, l.tabsY, tabW, l.tabsH)) { click(); setMode(Mode.BUY); return true; }
        if (inB(mx, my, l.panelX + tabW, l.tabsY, tabW, l.tabsH)) { click(); setMode(Mode.SELL); return true; }
        if (inB(mx, my, l.panelX + tabW * 2, l.tabsY, l.panelW - tabW * 2, l.tabsH)) { click(); setMode(Mode.BUYBACK); return true; }

        if (mode == Mode.BUY && clickBuyCatalog(mx, my, l)) return true;
        if (mode == Mode.SELL && clickInventoryForSell(mx, my, l)) return true;

        if ((mode == Mode.BUY && selectedBuyIndex >= 0) || (mode == Mode.SELL && selectedSellSlot >= 0)) {
            if (clickDetailControls(mx, my, l)) return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    private boolean clickBuyCatalog(int mx, int my, Layout l) {
        List<ShopEntryDisplayData> sells = state.sells();
        int cw = (l.catalogW - PAD * (CATALOG_COLS - 1)) / CATALOG_COLS;
        for (int i = 0; i < sells.size(); i++) {
            int row = i / CATALOG_COLS - catalogScrollRows;
            int col = i % CATALOG_COLS;
            int cx = l.catalogX + col * (cw + PAD);
            int cy = l.contentY + row * CATALOG_ROW_H;
            if (inB(mx, my, cx, cy, cw, CATALOG_ROW_H - PAD)) {
                if (sells.get(i).soldOut()) return true; // consume the click, do nothing
                click();
                selectedBuyIndex = i;
                buyQuantity = TradingQuantityMath.clamp(1, computeMaxBuyQuantity(i));
                rejectionKey = null;
                return true;
            }
        }
        return false;
    }

    private boolean clickInventoryForSell(int mx, int my, Layout l) {
        int gridW = INV_COLS * (SLOT_SIZE + SLOT_GAP) - SLOT_GAP;
        int startX = l.invX + (l.invW - gridW) / 2;
        int startY = l.invY + PAD;

        for (int row = 0; row < INV_STORAGE_ROWS; row++) {
            for (int col = 0; col < INV_COLS; col++) {
                int slotIndex = 9 + row * INV_COLS + col;
                int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
                int sy = startY + row * (SLOT_SIZE + SLOT_GAP);
                if (inB(mx, my, sx, sy, SLOT_SIZE, SLOT_SIZE)) {
                    selectSellSlot(slotIndex);
                    return true;
                }
            }
        }
        int hotbarY = startY + INV_STORAGE_ROWS * (SLOT_SIZE + SLOT_GAP) + 4;
        for (int col = 0; col < INV_COLS; col++) {
            int sx = startX + col * (SLOT_SIZE + SLOT_GAP);
            if (inB(mx, my, sx, hotbarY, SLOT_SIZE, SLOT_SIZE)) {
                selectSellSlot(col);
                return true;
            }
        }
        return false;
    }

    private void selectSellSlot(int slotIndex) {
        if (playerStack(slotIndex).isEmpty()) return;
        click();
        selectedSellSlot = slotIndex;
        sellQuote = null;
        pendingFreshSellSelection = true;
        sellQuantity = 0;
        rejectionKey = null;
        requestSellQuoteFor(slotIndex);
    }

    private boolean clickDetailControls(int mx, int my, Layout l) {
        int x = l.detailX, w = l.detailW;
        int y = l.contentY, h = l.contentH;
        int qtyY = y + h - 40;
        int maxQ = mode == Mode.BUY ? computeMaxBuyQuantity(selectedBuyIndex)
                : (sellQuote != null ? sellQuote.effectiveMaxQuantity() : 0);
        int quantity = mode == Mode.BUY ? buyQuantity : sellQuantity;

        QtyRects r = qtyRects(x + PAD, qtyY, w - PAD * 2, 14);
        if (inB(mx, my, r.minusX, qtyY, r.minusW, r.h)) { click(); setQuantity(Math.max(0, quantity - 1), maxQ); return true; }
        if (inB(mx, my, r.plusX, qtyY, r.plusW, r.h)) { click(); setQuantity(quantity + 1, maxQ); return true; }
        if (inB(mx, my, r.boxX, qtyY, r.boxW, r.h)) {
            click();
            editingQuantity = true;
            editBuffer = String.valueOf(Math.max(quantity, 1));
            return true;
        }

        boolean canConfirm = quantity > 0;
        int btnW = 44, btnH = 14;
        int by = y + h - 16;
        int confirmX = x + w - PAD - btnW;
        int cancelX = confirmX - 4 - btnW;
        if (inB(mx, my, confirmX, by, btnW, btnH)) { confirmAction(canConfirm); return true; }
        if (inB(mx, my, cancelX, by, btnW, btnH)) { click(); clearSelection(); return true; }
        return false;
    }

    private void clearSelection() {
        selectedBuyIndex = -1;
        selectedSellSlot = -1;
        sellQuote = null;
        rejectionKey = null;
    }

    private void setQuantity(int desired, int maxQ) {
        int q = mode == Mode.BUY ? TradingQuantityMath.clamp(desired, maxQ) : TradingQuantityMath.clamp(desired, maxQ);
        if (mode == Mode.BUY) buyQuantity = q; else sellQuantity = q;
    }

    private void confirmAction(boolean canConfirm) {
        if (!canConfirm) return;
        click();
        if (mode == Mode.BUY) {
            ClientPlayNetworking.send(new BuyItemPayload(selectedBuyIndex, buyQuantity));
        } else if (mode == Mode.SELL) {
            ClientPlayNetworking.send(new SellItemPayload(selectedSellSlot, sellQuantity));
        }
    }

    private void setMode(Mode newMode) {
        this.mode = newMode;
        rejectionKey = null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mode == Mode.BUY) {
            catalogScrollRows -= (int) Math.signum(scrollY);
            if (catalogScrollRows < 0) catalogScrollRows = 0;
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void commitQuantityEdit() {
        editingQuantity = false;
        int maxQ = mode == Mode.BUY ? computeMaxBuyQuantity(selectedBuyIndex)
                : (sellQuote != null ? sellQuote.effectiveMaxQuantity() : 0);
        try {
            setQuantity(Integer.parseInt(editBuffer), maxQ);
        } catch (NumberFormatException ignored) {
            // leave quantity unchanged — e.g. buffer was cleared to empty
        }
        editBuffer = "";
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
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
     *  labels/credit values so an unusually long value (Part G: "large Credit balances") shrinks
     *  rather than overlapping neighboring UI. */
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
