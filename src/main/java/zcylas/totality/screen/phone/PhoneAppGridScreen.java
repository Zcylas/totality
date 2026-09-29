package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.Level;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.screen.character.CharacterScreen;
import zcylas.totality.screen.inventory.TotalityInventoryScreen;
import zcylas.totality.screen.menu.SkillsMenuScreen;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Phone app grid (post-setup). Opened via TAB (equipped Phone) or by right-clicking a held, set-up Phone.
 * The device and its display are drawn by {@link PhoneFrameRenderer} in the phone's {@link PhoneDeviceStyle};
 * this screen only owns the phone's content: the apps, their lock state and actions, pages and the dock.
 * Apps without a backing system yet (Codex, Wallet, Settings) render as unlocked per design but no-op on click.
 *
 * <p>Input: mouse (hover, click), and keyboard — arrow keys move the selection (left/right past the edge
 * turn the page), Enter/Space opens, ESC/TAB close.
 */
public class PhoneAppGridScreen extends Screen {

    private static final int COLUMNS = 3;
    private static final int ROWS = 3;
    private static final int PER_PAGE = COLUMNS * ROWS;
    private static final int PAD = 3;
    private static final int GAP = 3;
    private static final long PRESS_NANOS = 110_000_000L;
    private static final String[] DOCK = {"Character", "Skills", "Quests", "Wallet"};

    private record App(String label, boolean unlocked, String lockReason, Runnable action, Identifier icon) {
        App(String label, boolean unlocked, String lockReason, Runnable action) {
            this(label, unlocked, lockReason, action, null);
        }
    }

    /**
     * The Codex's default icon: the same artwork on every phone tier (the device styles the tile around it, never the
     * app's identity). 32x32, drawn at 32 GUI pixels so each texel is a whole number of screen pixels.
     */
    public static final Identifier CODEX_ICON = Identifier.fromNamespaceAndPath("totality", "phone/apps/codex");

    /** Everything the draw and the click code both need, computed from one layout. */
    private record Geometry(PhoneFrameRenderer.Layout device, float scale, int headerH, int gridX, int gridY,
                            int tileW, int tileH, int pageRowY, int dockY, int dockH, int[] dockX, int[] dockW) {
        int tileX(int col) { return gridX + col * (tileW + GAP); }
        int tileY(int row) { return gridY + row * (tileH + GAP); }
        int gridW() { return COLUMNS * tileW + (COLUMNS - 1) * GAP; }
        int gridH() { return ROWS * tileH + (ROWS - 1) * GAP; }
    }

    private final PhoneFrame frame;
    private final PhoneDeviceStyle style;
    private final List<List<App>> pages = new ArrayList<>();
    private final long openedNanos;
    private int page = 0;
    /** Keyboard selection on the current page; shown while the keyboard was used last. */
    private int focus = 0;
    private boolean keyboardMode;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    private int pressed = -1;
    private long pressedNanos;

    public PhoneAppGridScreen(PhoneFrame frame) {
        super(Component.literal("Phone"));
        this.frame = frame;
        this.style = PhoneDeviceStyle.of(frame);
        this.openedNanos = PhoneFrameRenderer.beginOpen();
        buildApps();
    }

    private void buildApps() {
        Runnable openCharacter = () -> Minecraft.getInstance().gui.setScreen(new CharacterScreen());
        Runnable openSkills    = () -> SkillsMenuScreen.open(this);
        Runnable openInventory = () -> Minecraft.getInstance().gui.setScreen(new TotalityInventoryScreen());
        Runnable openBank      = () -> Minecraft.getInstance().gui.setScreen(new BankScreen(frame));
        Runnable openQuests    = () -> zcylas.totality.client.quest.ClientQuestManager.openQuestApp(frame);
        boolean bankUnlocked   = zcylas.totality.client.dialogue.ClientNarrativeFlagsManager.hasFlag("bank_app_unlocked");

        List<App> all = new ArrayList<>();
        all.add(new App("Character", true, null, openCharacter));
        all.add(new App("Skills",    true, null, openSkills));
        all.add(new App("Quests",    true, null, openQuests));
        // Codex: knowledge and discoveries (the Bestiary becomes one of its categories). Visible and selectable,
        // with the normal press feedback, but it opens nothing yet.
        all.add(new App("Codex",     true, null, null, CODEX_ICON));
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

        for (int i = 0; i < all.size(); i += PER_PAGE) {
            pages.add(all.subList(i, Math.min(i + PER_PAGE, all.size())));
        }
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private Geometry geometry() {
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.layout(width, height, style);
        int tileW = (l.dw() - PAD * 2 - GAP * (COLUMNS - 1)) / COLUMNS;
        List<String> labels = new ArrayList<>();
        for (List<App> p : pages) for (App a : p) labels.add(a.label());
        // The device-wide display scale, reduced further (crisply) only if a label would not fit its tile.
        float scale = Math.min(PhoneUi.displayScale(l), Math.min(
                PhoneUi.crispScale(font, labels, tileW - 4, PhoneUi.guiScale()),
                PhoneUi.crispScale(font, List.of(String.join("", DOCK)), l.dw() - DOCK.length * 6, PhoneUi.guiScale())));
        int line = Math.round(9 * scale);
        int headerH = line + 5;
        int dockH = line + 9;
        int dockY = l.dy() + l.dh() - dockH;
        int pageRowY = dockY - 9;
        int gridY = l.dy() + headerH + PAD;
        int tileH = (pageRowY - 2 - gridY - GAP * (ROWS - 1)) / ROWS;
        int gridW = COLUMNS * tileW + (COLUMNS - 1) * GAP;
        int gridX = l.dx() + (l.dw() - gridW) / 2;
        // Dock entries share the width in proportion to their labels.
        int[] dockX = new int[DOCK.length], dockW = new int[DOCK.length];
        int labelsW = 0;
        for (String s : DOCK) labelsW += Math.round(font.width(s) * scale);
        int spare = l.dw() - labelsW, x = l.dx();
        for (int i = 0; i < DOCK.length; i++) {
            int w = Math.round(font.width(DOCK[i]) * scale) + spare / DOCK.length + (i < spare % DOCK.length ? 1 : 0);
            dockX[i] = x;
            dockW[i] = w;
            x += w;
        }
        return new Geometry(l, scale, headerH, gridX, gridY, tileW, tileH, pageRowY, dockY, dockH, dockX, dockW);
    }

    private int tileAt(Geometry geo, double mx, double my) {
        List<App> apps = pages.get(page);
        for (int i = 0; i < apps.size(); i++) {
            int x = geo.tileX(i % COLUMNS), y = geo.tileY(i / COLUMNS);
            if (mx >= x && mx < x + geo.tileW() && my >= y && my < y + geo.tileH()) return i;
        }
        return -1;
    }

    private int dockAt(Geometry geo, double mx, double my) {
        if (my < geo.dockY() || my >= geo.dockY() + geo.dockH()) return -1;
        for (int i = 0; i < DOCK.length; i++) if (mx >= geo.dockX()[i] && mx < geo.dockX()[i] + geo.dockW()[i]) return i;
        return -1;
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Left intentionally empty — the game world stays visible around the phone.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        if (mx != lastMouseX || my != lastMouseY) {
            if (!Double.isNaN(lastMouseX)) keyboardMode = false;
            lastMouseX = mx;
            lastMouseY = my;
        }
        Geometry geo = geometry();
        PhoneFrameRenderer.Transition t = PhoneFrameRenderer.transition(openedNanos);
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.drawDevice(g, geo.device(), style, t);
        int shift = l.x() - geo.device().x();
        PhoneUi ui = new PhoneUi(g, font, style, geo.scale());
        g.pose().pushMatrix();
        g.pose().translate(shift, 0);
        int mxs = mx - shift;

        ui.header(l.dx() - shift, l.dy(), l.dw(), "TOTALITY", currentTimeString());

        List<App> apps = pages.get(page);
        int hovered = keyboardMode ? -1 : tileAt(geo, mxs, my);
        int active = keyboardMode ? focus : hovered;
        boolean pressLive = pressed >= 0 && System.nanoTime() - pressedNanos < PRESS_NANOS;
        for (int i = 0; i < apps.size(); i++) {
            App app = apps.get(i);
            PhoneUi.TileState state = !app.unlocked() ? PhoneUi.TileState.LOCKED
                    : pressLive && pressed == i ? PhoneUi.TileState.PRESSED
                    : i == active ? PhoneUi.TileState.ACTIVE : PhoneUi.TileState.NORMAL;
            int x = geo.tileX(i % COLUMNS), y = geo.tileY(i / COLUMNS);
            ui.tile(x, y, geo.tileW(), geo.tileH(), app.label(), state, app.icon());
            if (!app.unlocked() && i == active) {
                // A locked entry can still be selected: a quiet outline marks it, the tooltip gives the reason.
                ui.outline(x - 1, y - 1, geo.tileW() + 2, geo.tileH() + 2, ui.colors().textFaint());
                if (app.lockReason() != null) {
                    int tx = keyboardMode ? x + shift : mx, ty = keyboardMode ? y + geo.tileH() : my;
                    g.setTooltipForNextFrame(font, Component.literal(app.lockReason()), tx, ty);
                }
            }
        }

        // Pages: dots, and chevrons where another page exists.
        int dotsY = geo.pageRowY() + 3;
        int cx = l.dx() - shift + l.dw() / 2;
        if (pages.size() > 1) {
            ui.pageDots(cx, dotsY, pages.size(), page);
            int half = PhoneUi.pageDotsWidth(pages.size()) / 2 + 7;
            if (page > 0) ui.chevron(cx - half - 3, dotsY - 1, false, ui.colors().textDim());
            if (page < pages.size() - 1) ui.chevron(cx + half, dotsY - 1, true, ui.colors().textDim());
        }

        // Dock (favourites): a row of soft keys under a separator.
        ui.separator(l.dx() - shift, geo.dockY(), l.dw());
        int dockHover = keyboardMode ? -1 : dockAt(geo, mxs, my);
        for (int i = 0; i < DOCK.length; i++) {
            int x = geo.dockX()[i], w = geo.dockW()[i];
            boolean h = i == dockHover;
            int ty = geo.dockY() + (geo.dockH() - ui.lineHeight()) / 2 + 1;
            ui.textCentered(DOCK[i], x + w / 2, ty, h ? ui.colors().accentBright() : ui.colors().textDim());
            if (h) g.fill(x + 3, ty + ui.lineHeight(), x + w - 3, ty + ui.lineHeight() + 1, ui.colors().accent());
            if (i > 0) g.fill(x, geo.dockY() + 4, x + 1, geo.dockY() + geo.dockH() - 3, ui.colors().line());
        }
        g.pose().popMatrix();
        PhoneFrameRenderer.finishDisplay(g, l, style, t);
    }

    private static final DateTimeFormatter PC_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

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

    // ── Input ─────────────────────────────────────────────────────────────────

    private void activate(int index) {
        List<App> apps = pages.get(page);
        if (index < 0 || index >= apps.size()) return;
        App app = apps.get(index);
        if (!app.unlocked()) return;
        click();
        pressed = index;
        pressedNanos = System.nanoTime();
        if (app.action() != null) app.action().run();
    }

    private void turnPage(int delta) {
        int next = page + delta;
        if (next < 0 || next >= pages.size()) return;
        click();
        page = next;
        focus = Math.min(focus, pages.get(page).size() - 1);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        double mx = mouse.x(), my = mouse.y();
        Geometry geo = geometry();

        int tile = tileAt(geo, mx, my);
        if (tile >= 0) {
            focus = tile;
            activate(tile);
            return true;
        }

        if (pages.size() > 1) {
            boolean inGridRows = my >= geo.gridY() && my < geo.pageRowY() + 9;
            int cx = geo.device().dx() + geo.device().dw() / 2;
            boolean pageRow = my >= geo.pageRowY() && my < geo.pageRowY() + 9;
            if ((inGridRows && mx < geo.gridX()) || (pageRow && mx < cx && mx >= geo.device().dx())) { turnPage(-1); return true; }
            if ((inGridRows && mx >= geo.gridX() + geo.gridW()) || (pageRow && mx >= cx && mx < geo.device().dx() + geo.device().dw())) {
                turnPage(1);
                return true;
            }
        }

        int dock = dockAt(geo, mx, my);
        if (dock >= 0) {
            click();
            switch (DOCK[dock]) {
                case "Character" -> Minecraft.getInstance().gui.setScreen(new CharacterScreen());
                case "Skills"    -> SkillsMenuScreen.open(this);
                case "Quests"    -> zcylas.totality.client.quest.ClientQuestManager.openQuestApp(frame);
                default -> { /* Wallet: no backing system yet */ }
            }
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        }
        int count = pages.get(page).size();
        int col = focus % COLUMNS, row = focus / COLUMNS;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                keyboardMode = true;
                if (col > 0) focus--;
                else if (page > 0) { turnPage(-1); focus = Math.min(row * COLUMNS + COLUMNS - 1, pages.get(page).size() - 1); }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                keyboardMode = true;
                if (col < COLUMNS - 1 && focus + 1 < count) focus++;
                else if (page < pages.size() - 1) { turnPage(1); focus = Math.min(row * COLUMNS, pages.get(page).size() - 1); }
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                keyboardMode = true;
                if (row > 0) focus -= COLUMNS;
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                keyboardMode = true;
                if (focus + COLUMNS < count) focus += COLUMNS;
                return true;
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                keyboardMode = true;
                activate(focus);
                return true;
            }
            default -> { }
        }
        return super.keyPressed(event);
    }

    private void click() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ── Capture diagnostics (development) ─────────────────────────────────────

    /** Centre of tile {@code index} on the current page, GUI pixels (for the dev capture run). */
    public double[] tileCentre(int index) {
        Geometry geo = geometry();
        return new double[] {geo.tileX(index % COLUMNS) + geo.tileW() / 2.0, geo.tileY(index / COLUMNS) + geo.tileH() / 2.0};
    }

    /** Centre of dock entry {@code index}, GUI pixels (for the dev capture run). */
    public double[] dockCentre(int index) {
        Geometry geo = geometry();
        return new double[] {geo.dockX()[index] + geo.dockW()[index] / 2.0, geo.dockY() + geo.dockH() / 2.0};
    }

    public int page() {
        return page;
    }

    /** Label of app {@code index} on the current page (for the dev capture run). */
    public String appLabel(int index) {
        List<App> apps = pages.get(page);
        return index >= 0 && index < apps.size() ? apps.get(index).label() : null;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
