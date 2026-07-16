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
import zcylas.totality.client.tooltip.renderer.TooltipAnimator;
import zcylas.totality.networking.item.PhoneSetupPayload;

/**
 * Phone first-time setup screen. Opened by right-clicking a Phone whose
 * {@code setup_complete} component is absent/false. Rendered inside the shared
 * phone-shaped frame ({@link PhoneFrameRenderer}) so it looks like you're actually
 * using the phone, not staring at a plain dialog box.
 */
public class PhoneSetupScreen extends Screen {

    private static final int COLOR_PANEL_BG  = 0xFF0A0A0A;
    private static final int COLOR_LABEL      = 0xFF999999;
    private static final int COLOR_TEXT       = 0xFFDDDDDD;

    private static final int PANEL_W = 150;
    private static final int PANEL_H = 100;
    private static final int BUTTON_W = 84;
    private static final int BUTTON_H = 16;

    private final PhoneSource source;
    private final PhoneFrame frame;
    private float alpha = 0f;

    public PhoneSetupScreen(PhoneSource source, PhoneFrame frame) {
        super(Component.literal("Phone Setup"));
        this.source = source;
        this.frame = frame;
    }

    @Override
    protected void init() {
        super.init();
        alpha = 0f;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Left intentionally empty — the game world stays visible around the phone
        // instead of a full-screen backdrop, now that the phone is anchored to the right.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        alpha = Math.min(1f, alpha + 0.12f);

        int[] pb = PhoneFrameRenderer.bounds(width, height);
        int[] screen = PhoneFrameRenderer.draw(g, pb[0], pb[1], pb[2], pb[3], frame);
        int sx = screen[0], sy = screen[1], sw = screen[2], sh = screen[3];

        int px = sx + sw / 2 - PANEL_W / 2;
        int py = sy + sh / 2 - PANEL_H / 2;

        g.fill(px, py, px + PANEL_W, py + PANEL_H, COLOR_PANEL_BG);
        drawFrame(g, px, py, PANEL_W, PANEL_H, 2, frame.color);

        String title = "TOTALITY NETWORK";
        long timeMs = System.currentTimeMillis();
        TooltipAnimator.drawForbiddenText(g, font, title,
                sx + sw / 2 - font.width(title) / 2, py + 12, frame.colorBright, timeMs);

        String line1 = "New signal detected.";
        g.text(font, Component.literal(line1),
                sx + sw / 2 - font.width(line1) / 2, py + 30,
                COLOR_LABEL, false);

        String line2 = "Create local profile?";
        g.text(font, Component.literal(line2),
                sx + sw / 2 - font.width(line2) / 2, py + 42,
                COLOR_TEXT, false);

        int[] b = buttonBounds(sx, sw, py);
        boolean hovered = inB(mx, my, b[0], b[1], b[2], b[3]);
        int btnBg = hovered ? frame.colorDim : 0xFF141414;
        g.fill(b[0], b[1], b[0] + b[2], b[1] + b[3], btnBg);
        drawFrame(g, b[0], b[1], b[2], b[3], 1, hovered ? frame.colorBright : frame.color);
        String label = "Begin";
        g.text(font, Component.literal(label),
                b[0] + b[2] / 2 - font.width(label) / 2, b[1] + (b[3] - 8) / 2,
                hovered ? frame.colorBright : COLOR_TEXT, false);
    }

    private int[] buttonBounds(int sx, int sw, int py) {
        return new int[]{
                sx + sw / 2 - BUTTON_W / 2, py + PANEL_H - BUTTON_H - 8,
                BUTTON_W, BUTTON_H
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();
        int[] pb = PhoneFrameRenderer.bounds(width, height);
        int[] screen = PhoneFrameRenderer.screenBounds(pb[0], pb[1], pb[2], pb[3]);
        int sx = screen[0], sy = screen[1], sw = screen[2], sh = screen[3];
        int py = sy + sh / 2 - PANEL_H / 2;
        int[] b = buttonBounds(sx, sw, py);
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
