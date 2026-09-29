package zcylas.totality.screen.phone;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.api.item.TotalityItemComponents;
import zcylas.totality.networking.item.PhoneSetupPayload;

/**
 * Phone first-time setup screen. Opened by right-clicking a Phone whose
 * {@code setup_complete} component is absent/false. The device and its display are drawn by
 * {@link PhoneFrameRenderer} in the phone's {@link PhoneDeviceStyle}; this screen only owns the setup
 * content and its one action (Begin: Enter/E or click; TAB closes).
 */
public class PhoneSetupScreen extends Screen {

    private static final int BUTTON_H = 16;

    private final PhoneSource source;
    private final PhoneFrame frame;
    private final PhoneDeviceStyle style;
    private final long openedNanos;

    public PhoneSetupScreen(PhoneSource source, PhoneFrame frame) {
        super(Component.literal("Phone Setup"));
        this.source = source;
        this.frame = frame;
        this.style = PhoneDeviceStyle.of(frame);
        this.openedNanos = PhoneFrameRenderer.beginOpen();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Left intentionally empty — the game world stays visible around the phone
        // instead of a full-screen backdrop, now that the phone is anchored to the right.
    }

    private static final String TITLE = "TOTALITY NETWORK";
    private static final String LINE_1 = "New signal detected.";
    private static final String LINE_2 = "Create local profile?";
    private static final String BEGIN = "Begin";

    private float scale(PhoneFrameRenderer.Layout l) {
        return Math.min(PhoneUi.displayScale(l),
                PhoneUi.crispScale(font, java.util.List.of(TITLE, LINE_1, LINE_2), l.dw() - 12, PhoneUi.guiScale()));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        PhoneFrameRenderer.Layout base = PhoneFrameRenderer.layout(width, height, style);
        PhoneFrameRenderer.Transition t = PhoneFrameRenderer.transition(openedNanos);
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.drawDevice(g, base, style, t);
        PhoneUi ui = new PhoneUi(g, font, style, scale(base));
        PhoneDeviceStyle.Display d = ui.colors();
        int cx = l.dx() + l.dw() / 2;

        // First boot: no status bar yet, just the network greeting centred on the glass.
        int line = ui.lineHeight();
        int top = l.dy() + l.dh() * 2 / 5 - line * 2;
        ui.textCentered(TITLE, cx, top, d.accentBright());
        ui.separator(cx - ui.width(TITLE) / 2, top + line + 3, ui.width(TITLE));
        ui.textCentered(LINE_1, cx, top + line + 9, d.textDim());
        ui.textCentered(LINE_2, cx, top + line * 2 + 11, d.text());

        int[] b = buttonBounds(base, ui.scale);
        int shift = l.x() - base.x();
        boolean hovered = inB(mx - shift, my, b[0], b[1], b[2], b[3]);
        // The only choice is always selected (Enter works too), hover just brightens it.
        ui.tile(b[0] + shift, b[1], b[2], b[3], BEGIN, hovered ? PhoneUi.TileState.PRESSED : PhoneUi.TileState.ACTIVE);
        PhoneFrameRenderer.finishDisplay(g, l, style, t);
    }

    private int[] buttonBounds(PhoneFrameRenderer.Layout l, float scale) {
        int w = Math.max(Math.round(font.width(BEGIN) * scale) + 24, l.dw() / 2);
        int h = Math.max(BUTTON_H, Math.round(9 * scale) + 7);
        return new int[] {l.dx() + (l.dw() - w) / 2, l.dy() + l.dh() * 3 / 5, w, h};
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.layout(width, height, style);
        int[] b = buttonBounds(l, scale(l));
        if (inB(mx, my, b[0], b[1], b[2], b[3])) {
            confirm();
            return true;
        }
        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_E || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    private void confirm() {
        click();
        ItemStack stack = source.resolveClient();
        if (!stack.isEmpty()) {
            stack.set(TotalityItemComponents.PHONE_SETUP_COMPLETE, true);
        }
        ClientPlayNetworking.send(new PhoneSetupPayload(source.equipped(),
                source.hand() == net.minecraft.world.InteractionHand.OFF_HAND));
        Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame));
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
