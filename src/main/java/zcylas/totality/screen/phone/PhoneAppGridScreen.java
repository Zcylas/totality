package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.screen.character.CharacterScreen;
import zcylas.totality.screen.inventory.TotalityInventoryScreen;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Phone app grid (post-setup). Opened via TAB (equipped Phone) or by right-clicking
 * a held, set-up Phone. Rendered inside the shared phone-shaped frame
 * ({@link PhoneFrameRenderer}), parameterized by {@link PhoneFrame} so a higher phone
 * tier is just a different enum constant, not new drawing code.
 * Apps without a backing system yet (Quests, Wallet, Settings) render as unlocked per
 * design but no-op on click — hook up once Credits/Quest APIs exist.
 * TODO(visual pass): real icon art — currently plain text-label cells.
 */
public class PhoneAppGridScreen extends Screen {

    private static final int COLOR_CELL_BG   = 0xFF101010;
    private static final int COLOR_CELL_HOV  = 0xFF1A1A1A;
    private static final int COLOR_LOCKED_BG = 0xFF0A0A0A;
    private static final int COLOR_LABEL     = 0xFFCCCCCC;
    private static final int COLOR_LOCKED    = 0xFF555555;
    private static final int COLOR_FAV_BG    = 0xFF0A0A0A;
    private static final int COLOR_ENERGY_FULL = 0xFF00AA00; // matches UEItem.getEnergyBarColor's "green at 100%"
    private static final int COLOR_ENERGY_EMPTY = 0xFF222222;

    private static final int STATUS_H = 14;
    private static final int FAV_H    = 20;
    private static final int GRID_PAD = 6;
    private static final int CELL_GAP = 4;

    private record App(String label, boolean unlocked, String lockReason, Runnable action) {}

    private final PhoneFrame frame;
    private final List<List<App>> pages = new ArrayList<>();
    private int page = 0;

    public PhoneAppGridScreen(PhoneFrame frame) {
        super(Component.literal("Phone"));
        this.frame = frame;
        buildApps();
    }

    private void buildApps() {
        Runnable openCharacter = () -> Minecraft.getInstance().setScreen(new CharacterScreen());
        Runnable openSkills    = () -> Minecraft.getInstance().setScreen(new CharacterScreen(CharacterScreen.CharacterTab.SKILLS));
        Runnable openInventory = () -> Minecraft.getInstance().setScreen(new TotalityInventoryScreen());
        Runnable openBank      = () -> Minecraft.getInstance().setScreen(new BankScreen(frame));
        Runnable openQuests    = () -> zcylas.totality.client.quest.ClientQuestManager.openQuestApp(frame);
        boolean bankUnlocked   = zcylas.totality.client.dialogue.ClientNarrativeFlagsManager.hasFlag("bank_app_unlocked");

        List<App> all = new ArrayList<>();
        all.add(new App("Character", true, null, openCharacter));
        all.add(new App("Skills",    true, null, openSkills));
        all.add(new App("Quests",    true, null, openQuests));
        all.add(new App("Bestiary",  false, "Unlock by discovering your first creature.", null));
        all.add(new App("Spells",    false, "Unlocks on a higher-tier phone.", null));
        all.add(new App("Abilities", false, "Unlocks on a higher-tier phone.", null));
        all.add(new App("Wallet",    true, null, null));
        all.add(new App("Map",       false, "Unlocks with progression.", null));
        all.add(new App("Classes",   false, "Unlocks on a higher-tier phone.", null));
        all.add(new App("Inventory", true, null, openInventory));
        all.add(new App("Bank",      bankUnlocked, bankUnlocked ? null : "Link your phone with a Banker first.", openBank));
        all.add(new App("Mail",      false, "Unlocks once messaging is connected.", null));
        all.add(new App("Settings",  true, null, null));
        all.add(new App("Store",     false, "Requires Standard account tier.", null));

        for (int i = 0; i < all.size(); i += 9) {
            pages.add(all.subList(i, Math.min(i + 9, all.size())));
        }
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

        drawStatusBar(g, sx, sy, sw);

        int gridY = sy + STATUS_H + GRID_PAD;
        int gridH = sh - STATUS_H - GRID_PAD - FAV_H - 1;
        drawGrid(g, sx + GRID_PAD, gridY, sw - GRID_PAD * 2, gridH, mx, my);

        int favY = sy + sh - FAV_H;
        g.fill(sx, favY, sx + sw, favY + 1, frame.colorDim);
        drawFavourites(g, sx, favY, sw, mx, my);
    }

    private static final DateTimeFormatter PC_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private void drawStatusBar(GuiGraphicsExtractor g, int sx, int sy, int sw) {
        g.fill(sx, sy, sx + sw, sy + STATUS_H, 0xFF0A0A0A);
        g.fill(sx, sy + STATUS_H - 1, sx + sw, sy + STATUS_H, frame.colorDim);

        g.text(font, Component.literal("TOTALITY"), sx + 3, sy + 3, frame.colorBright, false);

        String time = currentTimeString();
        drawSmallCentered(g, time, sx + sw / 2, sy + 3, COLOR_LABEL);

        int batteryPercent = 100;
        int iconW = 10, iconH = 5, nubW = 1, nubH = 2;
        int iconX = sx + sw - 3 - iconW - nubW;
        int iconY = sy + (STATUS_H - iconH) / 2;

        drawBatteryIcon(g, iconX, iconY, iconW, iconH, nubW, nubH, batteryPercent);
    }

    private void drawSmallCentered(GuiGraphicsExtractor g, String text, int centerX, int y, int color) {
        float scale = 0.8f;
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        int w = font.width(text);
        g.text(font, Component.literal(text),
                Math.round((centerX - w * scale / 2f) / scale), Math.round(y / scale), color, false);
        g.pose().popMatrix();
    }

    private void drawBatteryIcon(GuiGraphicsExtractor g, int x, int y, int w, int h, int nubW, int nubH, int percent) {
        drawBorder(g, x, y, w, h, COLOR_LABEL);
        g.fill(x + w, y + (h - nubH) / 2, x + w + nubW, y + (h - nubH) / 2 + nubH, COLOR_LABEL);

        int segments = 3;
        int lit = Math.max(0, Math.min(segments, (int) Math.ceil(percent / (100.0 / segments))));
        int segW = 2, segGap = 1;
        int innerX = x + 1, innerY = y + 1, innerH = h - 2;
        for (int i = 0; i < segments; i++) {
            int sxi = innerX + i * (segW + segGap);
            if (sxi + segW > x + w - 1) break;
            g.fill(sxi, innerY, sxi + segW, innerY + innerH, i < lit ? COLOR_ENERGY_FULL : COLOR_ENERGY_EMPTY);
        }
    }

    private String currentTimeString() {
        Level level = Minecraft.getInstance().level;
        String gameTime = "--:--";
        if (level != null) {
            long dayTime = level.getOverworldClockTime() % 24000;
            int hour = (int) (((dayTime / 1000) + 6) % 24);
            int minute = (int) ((dayTime % 1000) * 60 / 1000);
            gameTime = String.format("%02d:%02d", hour, minute);
        }
        String pcTime = LocalTime.now().format(PC_TIME_FORMAT);
        return gameTime + " (" + pcTime + ")";
    }

    private int[] cellSize(int w, int h) {
        int cw = (w - CELL_GAP * 2) / 3;
        int ch = (h - CELL_GAP * 2) / 3;
        return new int[]{ cw, ch };
    }

    private void drawGrid(GuiGraphicsExtractor g, int x, int y, int w, int h, int mx, int my) {
        List<App> apps = pages.get(page);
        int[] cell = cellSize(w, h);
        int cw = cell[0], ch = cell[1];

        for (int i = 0; i < apps.size(); i++) {
            int row = i / 3, col = i % 3;
            int cx = x + col * (cw + CELL_GAP);
            int cy = y + row * (ch + CELL_GAP);
            drawCell(g, apps.get(i), cx, cy, cw, ch, mx, my);
        }

        if (pages.size() > 1) {
            String prev = page > 0 ? "<" : "";
            String next = page < pages.size() - 1 ? ">" : "";
            g.text(font, Component.literal(prev), x - 8, y + h / 2 - 4, COLOR_LABEL, false);
            g.text(font, Component.literal(next), x + w + 2, y + h / 2 - 4, COLOR_LABEL, false);
        }
    }

    private void drawCell(GuiGraphicsExtractor g, App app, int x, int y, int w, int h, int mx, int my) {
        boolean hovered = app.unlocked() && inB(mx, my, x, y, w, h);
        int bg = app.unlocked() ? (hovered ? COLOR_CELL_HOV : COLOR_CELL_BG) : COLOR_LOCKED_BG;
        int border = app.unlocked() ? (hovered ? frame.colorBright : frame.colorDim) : COLOR_LOCKED;
        int text = app.unlocked() ? COLOR_LABEL : COLOR_LOCKED;

        g.fill(x, y, x + w, y + h, bg);
        drawBorder(g, x, y, w, h, border);

        String label = app.unlocked() ? app.label() : app.label();
        drawSmallCentered(g, label, x + w / 2, y + h - 9, text);
        if (!app.unlocked()) drawSmallCentered(g, "🔒", x + w / 2, y + 3, COLOR_LOCKED);

        if (!app.unlocked() && inB(mx, my, x, y, w, h) && app.lockReason() != null) {
            g.setTooltipForNextFrame(font, Component.literal(app.lockReason()), mx, my);
        }
    }

    private void drawFavourites(GuiGraphicsExtractor g, int sx, int favY, int sw, int mx, int my) {
        g.fill(sx, favY, sx + sw, favY + FAV_H, COLOR_FAV_BG);

        String[] favs = { "Character", "Skills", "Quests", "Wallet" };
        int cw = sw / favs.length;
        for (int i = 0; i < favs.length; i++) {
            int x = sx + i * cw;
            boolean hovered = inB(mx, my, x, favY, cw, FAV_H);
            int c = hovered ? frame.colorBright : COLOR_LABEL;
            drawSmallCentered(g, favs[i], x + cw / 2, favY + FAV_H / 2 - 3, c);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        int mx = (int) mouse.x(), my = (int) mouse.y();

        int[] pb = PhoneFrameRenderer.bounds(width, height);
        int[] screen = PhoneFrameRenderer.screenBounds(pb[0], pb[1], pb[2], pb[3]);
        int sx = screen[0], sy = screen[1], sw = screen[2], sh = screen[3];

        int gridX = sx + GRID_PAD;
        int gridY = sy + STATUS_H + GRID_PAD;
        int gridW = sw - GRID_PAD * 2;
        int gridH = sh - STATUS_H - GRID_PAD - FAV_H - 1;

        List<App> apps = pages.get(page);
        int[] cell = cellSize(gridW, gridH);
        int cw = cell[0], ch = cell[1];

        for (int i = 0; i < apps.size(); i++) {
            int row = i / 3, col = i % 3;
            int cx = gridX + col * (cw + CELL_GAP);
            int cy = gridY + row * (ch + CELL_GAP);
            if (inB(mx, my, cx, cy, cw, ch)) {
                App app = apps.get(i);
                if (app.unlocked()) {
                    click();
                    if (app.action() != null) app.action().run();
                }
                return true;
            }
        }

        if (pages.size() > 1) {
            if (mx < gridX && page > 0) { click(); page--; return true; }
            if (mx > gridX + gridW && page < pages.size() - 1) { click(); page++; return true; }
        }

        int favY = sy + sh - FAV_H;
        if (my >= favY && my < sy + sh) {
            String[] favs = { "Character", "Skills", "Quests", "Wallet" };
            int fcw = sw / favs.length;
            int idx = (mx - sx) / fcw;
            if (idx >= 0 && idx < favs.length) {
                click();
                switch (favs[idx]) {
                    case "Character" -> Minecraft.getInstance().setScreen(new CharacterScreen());
                    case "Skills"    -> Minecraft.getInstance().setScreen(new CharacterScreen(CharacterScreen.CharacterTab.SKILLS));
                    case "Quests"    -> zcylas.totality.client.quest.ClientQuestManager.openQuestApp(frame);
                    default -> { /* Wallet: no backing system yet */ }
                }
            }
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(event);
    }

    private void drawBorder(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private boolean inB(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
