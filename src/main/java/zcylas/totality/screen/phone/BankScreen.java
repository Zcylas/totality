package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.networking.currency.ClientWalletManager;

import java.util.List;

/**
 * Basic Bank app screen — shows the account (Wallet) balance, distinct from the
 * inventory screen's physical-Credits-on-hand readout. Drawn on the phone device
 * ({@link PhoneFrameRenderer}, {@link PhoneDeviceStyle}). Opened from {@link PhoneAppGridScreen}'s
 * Bank tile; ESC/TAB return to the app grid (not a full close), per the "BACK returns to
 * phone app grid" convention for phone apps.
 * TODO: gate the Bank tile behind an actual account-ownership check once has_account
 * (currently a server-only narrative flag) has a client-side sync. Deposit/withdraw
 * still requires visiting a Banker — this screen is balance-display only for now.
 */
public class BankScreen extends Screen {

    private static final String LABEL = "ACCOUNT BALANCE";
    private static final String BACK = "[ESC] Back";

    private final PhoneFrame frame;
    private final PhoneDeviceStyle style;
    private final long openedNanos;

    public BankScreen(PhoneFrame frame) {
        super(Component.literal("Bank"));
        this.frame = frame;
        this.style = PhoneDeviceStyle.of(frame);
        this.openedNanos = PhoneFrameRenderer.beginOpen();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Left intentionally empty — the game world stays visible around the phone.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        PhoneFrameRenderer.Layout base = PhoneFrameRenderer.layout(width, height, style);
        PhoneFrameRenderer.Transition t = PhoneFrameRenderer.transition(openedNanos);
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.drawDevice(g, base, style, t);
        String balance = "\u20b5 " + ClientWalletManager.getValue();
        float scale = Math.min(PhoneUi.displayScale(l),
                PhoneUi.crispScale(font, List.of("BANK" + BACK + "  ", LABEL, balance), l.dw() - 12, PhoneUi.guiScale()));
        PhoneUi ui = new PhoneUi(g, font, scale);
        PhoneTheme d = ui.colors();

        ui.header(l.dx(), l.dy(), l.dw(), "BANK", BACK);
        int cx = l.dx() + l.dw() / 2;
        int line = ui.lineHeight();
        int top = l.dy() + l.dh() * 2 / 5 - line;
        ui.textCentered(LABEL, cx, top, d.textDim());
        ui.separator(cx - ui.width(LABEL) / 2, top + line + 2, ui.width(LABEL));
        // The balance at twice the display scale: still whole screen pixels per font pixel.
        PhoneUi big = new PhoneUi(g, font, scale * 2);
        if (big.width(balance) > l.dw() - 12) big = ui;
        big.textCentered(balance, cx, top + line + 7, d.text());
        PhoneFrameRenderer.finishDisplay(g, l, style, t);
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
