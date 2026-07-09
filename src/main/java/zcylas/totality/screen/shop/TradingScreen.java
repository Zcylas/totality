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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.tooltip.TooltipExtension;
import zcylas.totality.networking.shop.BuyItemPayload;
import zcylas.totality.networking.shop.ShopEntryDisplayData;
import zcylas.totality.networking.shop.ShowShopStatePayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Trading screen — matches the layout of {@code Context/trading_screen_mockup.png}
 * (left item grid / right credits+detail panel / bottom BUY-SELL tabs + confirm-cancel)
 * using the mod's existing flat-color dark+copper+cyan palette instead of the mockup's
 * stone-and-gold texture art. TODO(visual pass): real border/background texture, per-item
 * icon art for items that don't have one yet.
 *
 * SELL is a visible-but-inert stub for this pass — only BUY is wired to the server
 * (see {@link zcylas.totality.api.shop.TradeSessionManager}); selling requires a item-value
 * system that doesn't exist yet.
 */
public class TradingScreen extends Screen {

    private static final int COLOR_BG          = 0xFF05070A;
    private static final int COLOR_PANEL_BG    = 0xFF0A0A0A;
    private static final int COLOR_CELL_BG     = 0xFF101010;
    private static final int COLOR_CELL_HOV    = 0xFF1A1A1A;
    private static final int COLOR_COPPER        = 0xFFB87A1A;
    private static final int COLOR_COPPER_BRIGHT = 0xFFD4A030;
    private static final int COLOR_COPPER_DIM     = 0xFF7A5010;
    private static final int COLOR_ACCENT      = 0xFF00CCFF;
    private static final int COLOR_LABEL       = 0xFFCCCCCC;
    private static final int COLOR_DIM         = 0xFF888888;
    private static final int COLOR_RED         = 0xFFCC4444;
    private static final int COLOR_GREEN       = 0xFF44CC44;

    private static final int PAD = 8;
    private static final int COLS = 3;
    private static final int CELL_GAP = 6;
    private static final int TOP_H = 20;
    private static final int TAB_ROW_H = 24;
    private static final int QTY_ROW_H = 18;
    private static final int BOTTOM_H = TAB_ROW_H + QTY_ROW_H;
    private static final int MAX_QUANTITY = 100;

    private enum Mode { BUY, SELL }

    private ShowShopStatePayload state;
    private Mode mode = Mode.BUY;
    private int selected = -1;
    private int quantity = 1;
    private boolean draggingSlider = false;
    private boolean editingQuantity = false;
    private String editBuffer = "";

    public TradingScreen(ShowShopStatePayload initial) {
        super(Component.literal("Trading"));
        this.state = initial;
    }

    public void applyUpdate(ShowShopStatePayload update) {
        this.state = update;
        if (selected >= update.sells().size()) selected = -1;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        int px = width / 20, py = height / 14;
        int pw = width - px * 2, ph = height - py * 2;

        g.fill(px, py, px + pw, py + ph, COLOR_PANEL_BG);
        drawFrame(g, px, py, pw, ph, 2, COLOR_COPPER);

        String title = state.shopName().getString();
        g.text(font, Component.literal(title), px + PAD, py - 12, COLOR_COPPER_BRIGHT, true);

        int leftW = (int) (pw * 0.55f);
        int rightX = px + leftW + PAD;
        int rightW = pw - leftW - PAD;
        int contentY = py + PAD;
        int contentH = ph - PAD * 2 - BOTTOM_H;

        drawGrid(g, px + PAD, contentY, leftW - PAD * 2, contentH, mx, my);
        drawRight(g, rightX, contentY, rightW - PAD, contentH, mx, my);

        int qtyY = py + ph - BOTTOM_H;
        drawQuantityRow(g, px, qtyY, pw, QTY_ROW_H, mx, my);
        drawTabRow(g, px, qtyY + QTY_ROW_H, pw, TAB_ROW_H, mx, my);
    }

    // ── Left: item grid ──────────────────────────────────────────────────────

    private void drawGrid(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        List<ShopEntryDisplayData> sells = state.sells();
        int rows = Math.max(1, (sells.size() + COLS - 1) / COLS);
        int cw = (w - CELL_GAP * (COLS - 1)) / COLS;
        int ch = Math.min(52, (h - CELL_GAP * (rows - 1)) / Math.max(1, rows));

        for (int i = 0; i < sells.size(); i++) {
            int row = i / COLS, col = i % COLS;
            int cx = x + col * (cw + CELL_GAP);
            int cy = y + row * (ch + CELL_GAP);
            drawCell(g, sells.get(i), i, cx, cy, cw, ch, mx, my);
        }
    }

    private void drawCell(GuiGraphicsExtractor g, ShopEntryDisplayData entry, int index,
                           int x, int y, int w, int h, int mx, int my) {
        boolean hovered = inB(mx, my, x, y, w, h);
        boolean isSelected = index == selected;
        int bg = isSelected ? COLOR_CELL_HOV : (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
        int border = isSelected ? COLOR_ACCENT : (hovered ? COLOR_COPPER_BRIGHT : COLOR_COPPER_DIM);

        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, isSelected ? 2 : 1, border);

        String name = displayNameFor(entry.stack());
        drawScaledCentered(g, name, x + w / 2, y + 3, w - 6, COLOR_LABEL);

        if (!entry.stack().isEmpty()) {
            g.item(entry.stack(), x + w / 2 - 8, y + 14);
        }

        String price = entry.price() + " ₵";
        int priceColor = entry.affordable() ? COLOR_ACCENT : COLOR_RED;
        g.text(font, Component.literal(price), x + w / 2 - font.width(price) / 2, y + h - 11, priceColor, false);
    }

    /** Enchanted items show their enchantment(s) directly (e.g. "Sharpness III") instead of
     *  a generic "Enchanted Book" — no need to select the item first to see what it actually is. */
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

    // ── Right: credits + detail panel ────────────────────────────────────────

    private void drawRight(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + TOP_H, COLOR_CELL_BG);
        drawFrame(g, x, y, w, TOP_H, 1, COLOR_COPPER_DIM);
        String label = "YOUR CREDITS:";
        g.text(font, Component.literal(label), x + 6, y + (TOP_H - 8) / 2, COLOR_COPPER_BRIGHT, false);
        String value = "₵ " + state.walletBalance();
        g.text(font, Component.literal(value), x + w - 6 - font.width(value), y + (TOP_H - 8) / 2, COLOR_ACCENT, true);

        int detailY = y + TOP_H + PAD;
        int detailH = h - TOP_H - PAD;
        g.fill(x, detailY, x + w, detailY + detailH, COLOR_CELL_BG);
        drawFrame(g, x, detailY, w, detailH, 1, COLOR_COPPER_DIM);

        if (selected < 0 || selected >= state.sells().size()) {
            String hint = "Select an item";
            g.text(font, Component.literal(hint),
                    x + w / 2 - font.width(hint) / 2, detailY + detailH / 2 - 4, COLOR_DIM, false);
            return;
        }

        ShopEntryDisplayData entry = state.sells().get(selected);
        ItemStack stack = entry.stack();

        int iconX = x + PAD, iconY = detailY + PAD;
        g.item(stack, iconX, iconY);

        String name = displayNameFor(stack);
        drawScaledCentered(g, name, iconX + 22 + (w - PAD * 2 - 22) / 2, iconY + 4, w - PAD * 2 - 22, COLOR_LABEL);

        int cy = iconY + 22;
        g.fill(x + PAD, cy, x + w - PAD, cy + 1, COLOR_COPPER_DIM);
        cy += 6;

        for (Component line : tooltipLines(stack)) {
            String text = line.getString();
            if (text.isEmpty()) continue;
            List<String> wrapped = wrap(text, w - PAD * 2);
            for (String wline : wrapped) {
                if (cy > detailY + detailH - 14) break;
                g.text(font, Component.literal(wline), x + PAD, cy, COLOR_DIM, false);
                cy += 10;
            }
        }
    }

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

    // ── Quantity row ──────────────────────────────────────────────────────────

    /** {@code qtyLabelW} (last element) is both the space reserved for the quantity box
     *  here AND the width drawn/hit-tested for it — sized off whichever text is actually
     *  showing (plain "xN", or the edit buffer while typing) so the box can never spill
     *  past the row. */
    private int[] quantityLayout(int x, int y, int w, int h) {
        int btnW = 16, btnH = h - 4;
        int minusX = x + PAD;
        String totalStr = "Total: " + currentTotal() + " ₵";
        int totalW = font.width(totalStr);
        String qtyText = editingQuantity ? editBuffer + "|" : "x" + quantity;
        int qtyLabelW = Math.max(30, font.width(qtyText) + 8);
        int plusX = x + w - PAD - totalW - 8 - qtyLabelW - 4 - btnW;
        int trackX = minusX + btnW + 4;
        int trackW = Math.max(10, plusX - 4 - trackX);
        return new int[]{ minusX, btnW, btnH, trackX, trackW, plusX, qtyLabelW };
    }

    private void drawQuantityRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);

        boolean active = mode == Mode.BUY && selected >= 0 && selected < state.sells().size();
        int textColor = active ? COLOR_LABEL : COLOR_DIM;

        int[] layout = quantityLayout(x, y, w, h);
        int minusX = layout[0], btnW = layout[1], btnH = layout[2];
        int trackX = layout[3], trackW = layout[4], plusX = layout[5], labelW = layout[6];
        int by = y + 2;

        drawSmallButton(g, "-", minusX, by, btnW, btnH, active, mx, my);
        drawSmallButton(g, "+", plusX, by, btnW, btnH, active, mx, my);

        int trackY = by + btnH / 2 - 2;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF1A1A1A);
        drawFrame(g, trackX, trackY, trackW, 4, 1, active ? COLOR_COPPER_DIM : 0xFF333333);
        int handleX = trackX + (int) ((quantity - 1) / (float) (MAX_QUANTITY - 1) * trackW);
        g.fill(handleX - 1, trackY - 2, handleX + 2, trackY + 6, active ? COLOR_ACCENT : COLOR_DIM);

        int labelX = plusX + btnW + 6;
        boolean labelHovered = active && inB(mx, my, labelX, by, labelW, btnH);
        g.fill(labelX, by, labelX + labelW, by + btnH,
                editingQuantity ? 0xFF1A2A3A : (labelHovered ? COLOR_CELL_HOV : 0xFF0A0A0A));
        drawFrame(g, labelX, by, labelW, btnH, 1,
                editingQuantity ? COLOR_ACCENT : (active ? COLOR_COPPER_DIM : 0xFF333333));

        String qtyLabel = editingQuantity ? editBuffer + "|" : "x" + quantity;
        g.text(font, Component.literal(qtyLabel), labelX + 4, by + (btnH - 8) / 2,
                editingQuantity ? COLOR_ACCENT : textColor, false);

        String totalStr = "Total: " + currentTotal() + " ₵";
        g.text(font, Component.literal(totalStr), x + w - PAD - font.width(totalStr), by + (btnH - 8) / 2,
                active ? COLOR_ACCENT : COLOR_DIM, false);
    }

    private void drawSmallButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                                  boolean active, int mx, int my) {
        boolean hovered = active && inB(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? COLOR_CELL_HOV : 0xFF0A0A0A);
        drawFrame(g, x, y, w, h, 1, active ? COLOR_COPPER_DIM : 0xFF333333);
        int c = active ? COLOR_LABEL : COLOR_DIM;
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, c, false);
    }

    private long currentTotal() {
        if (selected < 0 || selected >= state.sells().size()) return 0;
        return state.sells().get(selected).price() * (long) quantity;
    }

    // ── Bottom bar (tabs + confirm/cancel) ───────────────────────────────────

    private void drawTabRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        g.fill(x, y, x + w, y + 1, COLOR_COPPER_DIM);

        int tabW = 70, tabH = h - 6;
        int buyX = x + PAD, sellX = buyX + tabW + 4;
        drawTab(g, "BUY", buyX, y + 3, tabW, tabH, mode == Mode.BUY, mx, my);
        drawTab(g, "SELL", sellX, y + 3, tabW, tabH, mode == Mode.SELL, mx, my);

        int btnW = 28, btnH = h - 6;
        int cancelX = x + w - PAD - btnW, confirmX = cancelX - 4 - btnW;
        boolean canConfirm = canBuyNow();

        boolean confirmHovered = inB(mx, my, confirmX, y + 3, btnW, btnH);
        g.fill(confirmX, y + 3, confirmX + btnW, y + 3 + btnH,
                canConfirm ? (confirmHovered ? 0xFF1A4A1A : 0xFF0F2F0F) : 0xFF0A0A0A);
        drawFrame(g, confirmX, y + 3, btnW, btnH, 1, canConfirm ? COLOR_GREEN : COLOR_DIM);
        String check = "OK";
        g.text(font, Component.literal(check), confirmX + btnW / 2 - font.width(check) / 2,
                y + 3 + (btnH - 8) / 2, canConfirm ? COLOR_GREEN : COLOR_DIM, false);

        boolean cancelHovered = inB(mx, my, cancelX, y + 3, btnW, btnH);
        g.fill(cancelX, y + 3, cancelX + btnW, y + 3 + btnH, cancelHovered ? 0xFF4A1A1A : 0xFF2F0F0F);
        drawFrame(g, cancelX, y + 3, btnW, btnH, 1, COLOR_RED);
        String x_ = "X";
        g.text(font, Component.literal(x_), cancelX + btnW / 2 - font.width(x_) / 2,
                y + 3 + (btnH - 8) / 2, COLOR_RED, false);
    }

    private boolean canBuyNow() {
        if (mode != Mode.BUY || selected < 0 || selected >= state.sells().size()) return false;
        long funds = state.walletBalance() + state.physicalCredits();
        return funds >= currentTotal();
    }

    private void drawTab(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                          boolean active, int mx, int my) {
        boolean hovered = inB(mx, my, x, y, w, h);
        int bg = active ? 0xFF14283A : (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG);
        int border = active ? COLOR_ACCENT : COLOR_COPPER_DIM;
        g.fill(x, y, x + w, y + h, bg);
        drawFrame(g, x, y, w, h, 1, border);
        int c = active ? COLOR_ACCENT : COLOR_LABEL;
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, c, false);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        if (editingQuantity) commitQuantityEdit();

        int px = width / 20, py = height / 14;
        int pw = width - px * 2, ph = height - py * 2;
        int leftW = (int) (pw * 0.55f);
        int contentY = py + PAD;
        int contentH = ph - PAD * 2 - BOTTOM_H;
        int gx = px + PAD, gy = contentY;
        int gw = leftW - PAD * 2;

        List<ShopEntryDisplayData> sells = state.sells();
        int rows = Math.max(1, (sells.size() + COLS - 1) / COLS);
        int cw = (gw - CELL_GAP * (COLS - 1)) / COLS;
        int ch = Math.min(52, (contentH - CELL_GAP * (rows - 1)) / Math.max(1, rows));

        for (int i = 0; i < sells.size(); i++) {
            int row = i / COLS, col = i % COLS;
            int cx = gx + col * (cw + CELL_GAP);
            int cy = gy + row * (ch + CELL_GAP);
            if (inB(mx, my, cx, cy, cw, ch)) {
                click();
                selected = i;
                quantity = 1;
                return true;
            }
        }

        boolean qtyActive = mode == Mode.BUY && selected >= 0 && selected < state.sells().size();
        int qtyY = py + ph - BOTTOM_H;
        if (qtyActive) {
            int[] layout = quantityLayout(px, qtyY, pw, QTY_ROW_H);
            int minusX = layout[0], btnW = layout[1], btnH = layout[2];
            int trackX = layout[3], trackW = layout[4], plusX = layout[5], labelW = layout[6];
            int by = qtyY + 2;

            int labelX = plusX + btnW + 6;
            if (inB(mx, my, labelX, by, labelW, btnH)) {
                if (doubleClick) {
                    click();
                    editingQuantity = true;
                    editBuffer = String.valueOf(quantity);
                }
                return true;
            }
            if (inB(mx, my, minusX, by, btnW, btnH)) { click(); setQuantity(quantity - 1); return true; }
            if (inB(mx, my, plusX, by, btnW, btnH)) { click(); setQuantity(quantity + 1); return true; }
            if (inB(mx, my, trackX, by - 4, trackW, btnH + 8)) {
                draggingSlider = true;
                setQuantityFromTrackX(mx, trackX, trackW);
                return true;
            }
        }

        int tabY = qtyY + QTY_ROW_H;
        int tabW = 70, tabH = TAB_ROW_H - 6;
        int buyX = px + PAD, sellX = buyX + tabW + 4;
        if (inB(mx, my, buyX, tabY + 3, tabW, tabH)) { click(); mode = Mode.BUY; return true; }
        if (inB(mx, my, sellX, tabY + 3, tabW, tabH)) { click(); mode = Mode.SELL; return true; }

        int btnW2 = 28, btnH2 = TAB_ROW_H - 6;
        int cancelX = px + pw - PAD - btnW2, confirmX = cancelX - 4 - btnW2;
        if (inB(mx, my, confirmX, tabY + 3, btnW2, btnH2)) { confirmPurchase(); return true; }
        if (inB(mx, my, cancelX, tabY + 3, btnW2, btnH2)) { click(); Minecraft.getInstance().setScreen(null); return true; }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouse, double dragX, double dragY) {
        if (draggingSlider) {
            int px = width / 20, py = height / 14;
            int pw = width - px * 2, ph = height - py * 2;
            int qtyY = py + ph - BOTTOM_H;
            int[] layout = quantityLayout(px, qtyY, pw, QTY_ROW_H);
            setQuantityFromTrackX((int) mouse.x(), layout[3], layout[4]);
            return true;
        }
        return super.mouseDragged(mouse, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouse) {
        draggingSlider = false;
        return super.mouseReleased(mouse);
    }

    private void setQuantityFromTrackX(int mx, int trackX, int trackW) {
        float frac = trackW <= 0 ? 0 : (mx - trackX) / (float) trackW;
        frac = Math.max(0f, Math.min(1f, frac));
        setQuantity(Math.round(1 + frac * (MAX_QUANTITY - 1)));
    }

    private void setQuantity(int value) {
        quantity = Math.max(1, Math.min(MAX_QUANTITY, value));
    }

    private void commitQuantityEdit() {
        editingQuantity = false;
        try {
            setQuantity(Integer.parseInt(editBuffer));
        } catch (NumberFormatException ignored) {
            // leave quantity unchanged — e.g. buffer was cleared to empty
        }
        editBuffer = "";
    }

    private void confirmPurchase() {
        if (!canBuyNow()) return;
        click();
        ClientPlayNetworking.send(new BuyItemPayload(selected, quantity));
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

        int key = event.key();
        if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirmPurchase();
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
        scale = Math.max(0.5f, scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int sx = Math.round((centerX - rawW * scale / 2f) / scale);
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
}
