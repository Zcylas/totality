package zcylas.totality.screen.economy;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.networking.economy.DepositCreditsPayload;
import zcylas.totality.networking.economy.ShowBankTellerPayload;
import zcylas.totality.networking.economy.WithdrawCreditsPayload;

/**
 * Banker Teller screen — opened via the Banker's "make a deposit/withdrawal" dialogue
 * choice (see {@code OpenBankTellerAction}). Moves value between physical
 * {@code totality:credits} items and the account (Wallet) balance, in any amount up to
 * whichever side currently holds. Panel proportions and slider pattern mirror
 * {@link zcylas.totality.screen.shop.TradingScreen}.
 */
public class BankTellerScreen extends Screen {

    private static final int COLOR_BG            = 0xFF05070A;
    private static final int COLOR_PANEL_BG      = 0xFF0A0A0A;
    private static final int COLOR_CELL_BG       = 0xFF101010;
    private static final int COLOR_CELL_HOV      = 0xFF1A1A1A;
    private static final int COLOR_COPPER        = 0xFFB87A1A;
    private static final int COLOR_COPPER_BRIGHT = 0xFFD4A030;
    private static final int COLOR_COPPER_DIM    = 0xFF7A5010;
    private static final int COLOR_ACCENT        = 0xFF00CCFF;
    private static final int COLOR_LABEL         = 0xFFCCCCCC;
    private static final int COLOR_DIM           = 0xFF888888;
    private static final int COLOR_RED           = 0xFFCC4444;
    private static final int COLOR_GREEN         = 0xFF44CC44;

    private static final int PAD = 8;
    private static final int TAB_ROW_H = 24;
    private static final int AMOUNT_ROW_H = 22;
    private static final int BOTTOM_H = TAB_ROW_H + AMOUNT_ROW_H;

    private enum Mode { DEPOSIT, WITHDRAW }

    private ShowBankTellerPayload state;
    private Mode mode = Mode.DEPOSIT;
    private long amount;
    private boolean draggingSlider = false;
    private boolean editingAmount = false;
    private String editBuffer = "";

    public BankTellerScreen(ShowBankTellerPayload initial) {
        super(Component.literal("Bank Teller"));
        this.state = initial;
        this.amount = Math.min(1, maxForMode());
    }

    public void applyUpdate(ShowBankTellerPayload update) {
        this.state = update;
        clampAmount();
    }

    /** Same outer proportions as {@link zcylas.totality.screen.shop.TradingScreen} — one
     *  source of truth so render and click-hit geometry can never drift apart. */
    private int[] panelBounds() {
        int px = width / 20, py = height / 14;
        int pw = width - px * 2, ph = height - py * 2;
        return new int[]{ px, py, pw, ph };
    }

    private long maxForMode() {
        return mode == Mode.DEPOSIT ? state.physicalCredits() : state.walletBalance();
    }

    private void clampAmount() {
        long max = maxForMode();
        amount = max <= 0 ? 0 : Math.max(1, Math.min(amount, max));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, COLOR_BG);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        int[] b = panelBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];

        g.fill(px, py, px + pw, py + ph, COLOR_PANEL_BG);
        g.text(font, Component.literal("BANK TELLER"), px + PAD, py - 12, COLOR_COPPER_BRIGHT, true);

        int contentY = py + PAD;
        int contentH = ph - PAD * 2 - BOTTOM_H;
        drawBalances(g, px + PAD, contentY, pw - PAD * 2, contentH);

        int amountY = py + ph - BOTTOM_H;
        drawAmountRow(g, px, amountY, pw, AMOUNT_ROW_H, mx, my);
        drawTabRow(g, px, amountY + AMOUNT_ROW_H, pw, TAB_ROW_H, mx, my);

        // Drawn last so the amount/tab row backgrounds above (which span the panel's
        // full width) can never paint over the border's side edges.
        drawFrame(g, px, py, pw, ph, 2, COLOR_COPPER);
    }

    private void drawBalances(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        int halfW = (w - PAD) / 2;
        int cardH = Math.min(64, h - 24);
        int cardY = y + Math.max(0, (h - cardH - 20) / 2);

        drawStatCard(g, x, cardY, halfW, cardH, "ACCOUNT", state.walletBalance() + "₵", mode == Mode.WITHDRAW);
        drawStatCard(g, x + halfW + PAD, cardY, halfW, cardH, "ON HAND", state.physicalCredits() + "₵", mode == Mode.DEPOSIT);

        String hint = mode == Mode.DEPOSIT
                ? "Deposit physical Credits into your account."
                : "Withdraw from your account as physical Credits.";
        g.text(font, Component.literal(hint), x + w / 2 - font.width(hint) / 2, cardY + cardH + 8, COLOR_DIM, false);
    }

    private void drawStatCard(GuiGraphicsExtractor g, int x, int y, int w, int h,
                               String label, String value, boolean highlighted) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        drawFrame(g, x, y, w, h, 1, highlighted ? COLOR_ACCENT : COLOR_COPPER_DIM);
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + 7, COLOR_LABEL, false);
        g.text(font, Component.literal(value), x + w / 2 - font.width(value) / 2, y + h - 19, COLOR_ACCENT, true);
    }

    /** {@code labelW} (last element) is both the space reserved for the amount box here
     *  AND the width drawn for it in {@link #drawAmountRow} / hit-tested in
     *  {@link #mouseClicked} — one number for both, so the box can never spill past the
     *  row (it's sized off whichever text is actually showing: the plain amount, or the
     *  edit buffer while typing, so it grows/shrinks with digit count either way). */
    private int[] amountLayout(int x, int y, int w, int h) {
        int btnW = 16, btnH = h - 4;
        int minusX = x + PAD;
        String displayText = editingAmount ? editBuffer + "|" : amount + "₵";
        int labelW = Math.max(40, font.width(displayText) + 10);
        int plusX = x + w - PAD - labelW - 6 - btnW;
        int trackX = minusX + btnW + 4;
        int trackW = Math.max(10, plusX - 4 - trackX);
        return new int[]{ minusX, btnW, btnH, trackX, trackW, plusX, labelW };
    }

    private void drawAmountRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);

        boolean active = maxForMode() > 0;
        int textColor = active ? COLOR_LABEL : COLOR_DIM;

        int[] layout = amountLayout(x, y, w, h);
        int minusX = layout[0], btnW = layout[1], btnH = layout[2];
        int trackX = layout[3], trackW = layout[4], plusX = layout[5], labelW = layout[6];
        int by = y + 2;

        drawSmallButton(g, "-", minusX, by, btnW, btnH, active, mx, my);
        drawSmallButton(g, "+", plusX, by, btnW, btnH, active, mx, my);

        int trackY = by + btnH / 2 - 2;
        g.fill(trackX, trackY, trackX + trackW, trackY + 4, 0xFF1A1A1A);
        drawFrame(g, trackX, trackY, trackW, 4, 1, active ? COLOR_COPPER_DIM : 0xFF333333);
        long max = maxForMode();
        if (active && max > 1) {
            int handleX = trackX + (int) ((amount - 1) / (float) (max - 1) * trackW);
            g.fill(handleX - 1, trackY - 2, handleX + 2, trackY + 6, COLOR_ACCENT);
        }

        int labelX = plusX + btnW + 6;
        boolean labelHovered = active && inB(mx, my, labelX, by, labelW, btnH);
        g.fill(labelX, by, labelX + labelW, by + btnH,
                editingAmount ? 0xFF1A2A3A : (labelHovered ? COLOR_CELL_HOV : 0xFF0A0A0A));
        drawFrame(g, labelX, by, labelW, btnH, 1,
                editingAmount ? COLOR_ACCENT : (active ? COLOR_COPPER_DIM : 0xFF333333));

        String amtLabel = editingAmount ? editBuffer + "|" : amount + "₵";
        int labelColor = editingAmount ? COLOR_ACCENT : textColor;
        g.text(font, Component.literal(amtLabel), labelX + 4, by + (btnH - 8) / 2, labelColor, false);
    }

    private void drawSmallButton(GuiGraphicsExtractor g, String label, int x, int y, int w, int h,
                                  boolean active, int mx, int my) {
        boolean hovered = active && inB(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, hovered ? COLOR_CELL_HOV : 0xFF0A0A0A);
        drawFrame(g, x, y, w, h, 1, active ? COLOR_COPPER_DIM : 0xFF333333);
        int c = active ? COLOR_LABEL : COLOR_DIM;
        g.text(font, Component.literal(label), x + w / 2 - font.width(label) / 2, y + (h - 8) / 2, c, false);
    }

    /** Lays out DEPOSIT/WITHDRAW/OK/X with equal gaps between every consecutive item —
     *  the gap is whatever space is left after the four fixed-width items, split three
     *  ways, so spacing stays even regardless of panel width (same fix as the Quest
     *  screen's bottom bar). Shared by draw and click-hit so they can't drift apart. */
    private int[] tabRowLayout(int x, int y, int w, int h) {
        int tabW = 80, tabH = h - 6;
        int btnW = 28, btnH = h - 6;
        int itemsW = tabW * 2 + btnW * 2;
        int gap = Math.max(4, (w - PAD * 2 - itemsW) / 3);
        int depX = x + PAD;
        int witX = depX + tabW + gap;
        int confirmX = witX + tabW + gap;
        int cancelX = confirmX + btnW + gap;
        return new int[]{ depX, witX, confirmX, cancelX, tabW, tabH, btnW, btnH };
    }

    private void drawTabRow(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        g.fill(x, y, x + w, y + h, COLOR_CELL_BG);
        g.fill(x, y, x + w, y + 1, COLOR_COPPER_DIM);

        int[] l = tabRowLayout(x, y, w, h);
        int depX = l[0], witX = l[1], confirmX = l[2], cancelX = l[3];
        int tabW = l[4], tabH = l[5], btnW = l[6], btnH = l[7];

        drawTab(g, "DEPOSIT", depX, y + 3, tabW, tabH, mode == Mode.DEPOSIT, mx, my);
        drawTab(g, "WITHDRAW", witX, y + 3, tabW, tabH, mode == Mode.WITHDRAW, mx, my);

        boolean canConfirm = amount > 0 && amount <= maxForMode();

        boolean confirmHovered = inB(mx, my, confirmX, y + 3, btnW, btnH);
        g.fill(confirmX, y + 3, confirmX + btnW, y + 3 + btnH,
                canConfirm ? (confirmHovered ? 0xFF1A4A1A : 0xFF0F2F0F) : 0xFF0A0A0A);
        drawFrame(g, confirmX, y + 3, btnW, btnH, 1, canConfirm ? COLOR_GREEN : COLOR_DIM);
        String ok = "OK";
        g.text(font, Component.literal(ok), confirmX + btnW / 2 - font.width(ok) / 2,
                y + 3 + (btnH - 8) / 2, canConfirm ? COLOR_GREEN : COLOR_DIM, false);

        boolean cancelHovered = inB(mx, my, cancelX, y + 3, btnW, btnH);
        g.fill(cancelX, y + 3, cancelX + btnW, y + 3 + btnH, cancelHovered ? 0xFF4A1A1A : 0xFF2F0F0F);
        drawFrame(g, cancelX, y + 3, btnW, btnH, 1, COLOR_RED);
        String x_ = "X";
        g.text(font, Component.literal(x_), cancelX + btnW / 2 - font.width(x_) / 2,
                y + 3 + (btnH - 8) / 2, COLOR_RED, false);
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

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        if (editingAmount) commitAmountEdit();

        int[] b = panelBounds();
        int px = b[0], py = b[1], pw = b[2], ph = b[3];
        int amountY = py + ph - BOTTOM_H;

        int[] layout = amountLayout(px, amountY, pw, AMOUNT_ROW_H);
        int minusX = layout[0], btnW = layout[1], btnH = layout[2];
        int trackX = layout[3], trackW = layout[4], plusX = layout[5], labelW = layout[6];
        int by = amountY + 2;

        if (maxForMode() > 0) {
            int labelX = plusX + btnW + 6;
            if (inB(mx, my, labelX, by, labelW, btnH)) {
                if (doubleClick) {
                    click();
                    editingAmount = true;
                    editBuffer = String.valueOf(amount);
                }
                return true;
            }
            if (inB(mx, my, minusX, by, btnW, btnH)) { click(); setAmount(amount - 1); return true; }
            if (inB(mx, my, plusX, by, btnW, btnH)) { click(); setAmount(amount + 1); return true; }
            if (inB(mx, my, trackX, by - 4, trackW, btnH + 8)) {
                draggingSlider = true;
                setAmountFromTrackX(mx, trackX, trackW);
                return true;
            }
        }

        int tabY = amountY + AMOUNT_ROW_H;
        int[] tl = tabRowLayout(px, tabY, pw, TAB_ROW_H);
        int depX = tl[0], witX = tl[1], confirmX = tl[2], cancelX = tl[3];
        int tabW = tl[4], tabH = tl[5], btnW2 = tl[6], btnH2 = tl[7];
        if (inB(mx, my, depX, tabY + 3, tabW, tabH)) { click(); mode = Mode.DEPOSIT; clampAmount(); return true; }
        if (inB(mx, my, witX, tabY + 3, tabW, tabH)) { click(); mode = Mode.WITHDRAW; clampAmount(); return true; }
        if (inB(mx, my, confirmX, tabY + 3, btnW2, btnH2)) { confirmTransaction(); return true; }
        if (inB(mx, my, cancelX, tabY + 3, btnW2, btnH2)) { click(); Minecraft.getInstance().gui.setScreen(null); return true; }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouse, double dragX, double dragY) {
        if (draggingSlider) {
            int[] b = panelBounds();
            int amountY = b[1] + b[3] - BOTTOM_H;
            int[] layout = amountLayout(b[0], amountY, b[2], AMOUNT_ROW_H);
            setAmountFromTrackX((int) mouse.x(), layout[3], layout[4]);
            return true;
        }
        return super.mouseDragged(mouse, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouse) {
        draggingSlider = false;
        return super.mouseReleased(mouse);
    }

    private void setAmountFromTrackX(int mx, int trackX, int trackW) {
        long max = maxForMode();
        if (max <= 0) return;
        float frac = trackW <= 0 ? 0 : (mx - trackX) / (float) trackW;
        frac = Math.max(0f, Math.min(1f, frac));
        setAmount(Math.round(1 + frac * (max - 1)));
    }

    private void setAmount(long value) {
        long max = maxForMode();
        amount = max <= 0 ? 0 : Math.max(1, Math.min(max, value));
    }

    private void commitAmountEdit() {
        editingAmount = false;
        try {
            setAmount(Long.parseLong(editBuffer));
        } catch (NumberFormatException ignored) {
            // leave amount unchanged — e.g. buffer was cleared to empty
        }
        editBuffer = "";
    }

    private void confirmTransaction() {
        if (amount <= 0 || amount > maxForMode()) return;
        click();
        if (mode == Mode.DEPOSIT) {
            ClientPlayNetworking.send(new DepositCreditsPayload(amount));
        } else {
            ClientPlayNetworking.send(new WithdrawCreditsPayload(amount));
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editingAmount) {
            if (event.isEscape()) { editingAmount = false; editBuffer = ""; return true; }
            int key = event.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE && !editBuffer.isEmpty()) {
                editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                commitAmountEdit();
                return true;
            }
            return true;
        }

        int key = event.key();
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirmTransaction();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingAmount) {
            String s = event.codepointAsString();
            if (s.length() == 1 && Character.isDigit(s.charAt(0)) && editBuffer.length() < 12) {
                editBuffer += s;
            }
            return true;
        }
        return super.charTyped(event);
    }

    private void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    private boolean inB(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
