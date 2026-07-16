package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.networking.currency.ClientWalletManager;

/**
 * Basic Bank app screen — shows the account (Wallet) balance, distinct from the
 * inventory screen's physical-Credits-on-hand readout. Opened from {@link PhoneAppGridScreen}'s
 * Bank tile; ESC/TAB return to the app grid (not a full close), per the "BACK returns to
 * phone app grid" convention for phone apps.
 * TODO: gate the Bank tile behind an actual account-ownership check once has_account
 * (currently a server-only narrative flag) has a client-side sync. Deposit/withdraw
 * still requires visiting a Banker — this screen is balance-display only for now.
 */
public class BankScreen extends Screen {

    private static final int COLOR_PANEL_BG  = 0xFF0A0A0A;
    private static final int COLOR_VALUE     = 0xFF00CCFF;
    private static final int COLOR_LABEL     = 0xFFCCCCCC;

    private static final int STATUS_H = 14;
    private static final int PANEL_W  = 140;
    private static final int PANEL_H  = 60;

    private final PhoneFrame frame;

    public BankScreen(PhoneFrame frame) {
        super(Component.literal("Bank"));
        this.frame = frame;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Left intentionally empty — the game world stays visible around the phone
        // instead of a full-screen backdrop, now that the phone is anchored to the right.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);

        int[] pb = PhoneFrameRenderer.bounds(width, height);
        int[] screen = PhoneFrameRenderer.draw(g, pb[0], pb[1], pb[2], pb[3], frame);
        int sx = screen[0], sy = screen[1], sw = screen[2], sh = screen[3];

        g.fill(sx, sy, sx + sw, sy + STATUS_H, 0xFF0A0A0A);
        g.fill(sx, sy + STATUS_H - 1, sx + sw, sy + STATUS_H, frame.colorDim);
        g.text(font, Component.literal("BANK"), sx + 3, sy + 3, frame.colorBright, false);

        String hint = "[ESC] Back";
        g.text(font, Component.literal(hint), sx + sw - 3 - font.width(hint), sy + 3, COLOR_LABEL, false);

        int px = sx + sw / 2 - PANEL_W / 2;
        int py = sy + sh / 2 - PANEL_H / 2;
        g.fill(px, py, px + PANEL_W, py + PANEL_H, COLOR_PANEL_BG);
        drawFrame(g, px, py, PANEL_W, PANEL_H, 1, frame.colorDim);

        String label = "ACCOUNT BALANCE";
        g.text(font, Component.literal(label),
                sx + sw / 2 - font.width(label) / 2, py + 12, COLOR_LABEL, false);

        String balance = "₵ " + ClientWalletManager.getValue();
        g.text(font, Component.literal(balance),
                sx + sw / 2 - font.width(balance) / 2, py + 28, COLOR_VALUE, true);
    }

    private void drawFrame(GuiGraphicsExtractor g, int x, int y, int w, int h, int t, int color) {
        g.fill(x, y, x + w, y + t, color);
        g.fill(x, y + h - t, x + w, y + h, color);
        g.fill(x, y, x + t, y + h, color);
        g.fill(x + w - t, y, x + w, y + h, color);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame));
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
