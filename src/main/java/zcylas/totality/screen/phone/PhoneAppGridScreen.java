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
import zcylas.totality.api.entitlement.client.ClientEntitlementView;
import zcylas.totality.api.entitlement.integration.PhoneAppEntitlements;
import zcylas.totality.client.quest.ClientQuestManager;
import zcylas.totality.screen.character.CharacterScreen;
import zcylas.totality.screen.inventory.TotalityInventoryScreen;
import zcylas.totality.screen.menu.SkillsMenuScreen;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The phone's home screen (post-setup). Opened via TAB (equipped Phone) or by right-clicking a held, set-up Phone.
 * The device and its wallpaper are drawn by {@link PhoneFrameRenderer} in the phone's {@link PhoneDeviceStyle};
 * the OS colours come from {@link PhoneTheme}; this screen owns the home content: the status bar, a 3x4 grid of
 * apps on the main page, page indicator and sliding pages, the favourites dock, and the notification shade
 * ({@link PhoneNotificationShade}, a Phase 1 visual prototype).
 *
 * <p>Apps without a backing system yet (Codex, System, Settings, Camera) are unlocked per design but no-op on click;
 * Technology is visible but unavailable until it exists. Icons are provisional abbreviations, except the Codex's own
 * icon. The main page is always the one shown when the phone opens; development pages on either side exist only
 * with {@link PhonePrototype}.
 *
 * <p>Every app's interactive area is its visible bounds — the icon's hover frame plus its label
 * ({@link PhoneHomeGeometry}) — so empty space between apps never activates one; hover, press and click all use
 * those same bounds, mapped through the open slide-in offset.
 *
 * <p>Input: mouse (hover; press and release without dragging opens), drags (pull the status bar down for the
 * shade, swipe the grid sideways between pages), wheel (scroll the shade), and keyboard — arrow keys move the
 * selection (left/right past the edge turn the page), Enter/Space opens, ESC closes the shade or the phone,
 * TAB closes the phone.
 */
public class PhoneAppGridScreen extends Screen {

    private static final int COLUMNS = PhoneHomeGeometry.COLUMNS;
    private static final int PER_PAGE = COLUMNS * PhoneHomeGeometry.ROWS;
    private static final long PRESS_NANOS = 110_000_000L;
    private static final long PAGE_NANOS = 220_000_000L;
    /** Pointer travel (GUI px) after which a press becomes a drag instead of a click. */
    private static final int DRAG_SLOP = 4;

    /**
     * One app. {@code shortLabel} (optional) is shown only when the full label cannot fit its column at any readable
     * size; it is display text only, never the app's identity.
     */
    private record App(String label, String shortLabel, String abbreviation, boolean unlocked, String lockReason,
                       Runnable action, Identifier icon) {
        App(String label, String abbreviation, boolean unlocked, String lockReason, Runnable action) {
            this(label, null, abbreviation, unlocked, lockReason, action, null);
        }
    }

    /** One home page; {@code note} marks a development page (drawn faintly when it has no apps). */
    private record Page(List<App> apps, String note) {}

    /**
     * The Codex's default icon: the same artwork on every phone tier (the device styles the tile around it, never the
     * app's identity). 32x32, drawn so each texel is a whole number of screen pixels.
     */
    public static final Identifier CODEX_ICON = Identifier.fromNamespaceAndPath("totality", "phone/apps/codex");

    /**
     * Everything the draw and the input code both need, computed from one layout: the pure geometry, the text scales,
     * and whether labels with a short form use it (only when a full label cannot fit at any readable size).
     */
    private record Geometry(PhoneHomeGeometry home, float scale, float labelScale, boolean shortLabels) {
        PhoneFrameRenderer.Layout device() { return home.device(); }
        int statusBottom() { return home.statusBottom(); }
    }

    private enum Gesture { NONE, PENDING, PULL_SHADE, SHADE_SCROLL, SHADE_SWIPE, PAGE_DRAG }

    private final PhoneFrame frame;
    private final PhoneDeviceStyle style;
    private final List<Page> pages = new ArrayList<>();
    private final List<App> dock = new ArrayList<>();
    private final List<App> mainApps = new ArrayList<>();
    private final PhoneNotificationShade shade = new PhoneNotificationShade(PhonePrototype.notifications());
    private final long openedNanos;
    private final int mainPage;
    private int page;
    /** Page slide: the view position (in pages) the current animation started from. */
    private float viewFrom;
    private long pageAnimStart = -1;
    private float pageDrag;
    /** Keyboard selection on the current page; shown while the keyboard was used last. */
    private int focus = 0;
    private boolean keyboardMode;
    private double lastMouseX = Double.NaN, lastMouseY = Double.NaN;
    private int pressed = -1;
    private int pressedDock = -1;
    private long pressedNanos;

    private Gesture gesture = Gesture.NONE;
    private double pressX, pressY;
    private PhoneNotificationShade.Entry swipeEntry;

    public PhoneAppGridScreen(PhoneFrame frame) {
        super(Component.literal("Phone"));
        this.frame = frame;
        this.style = PhoneDeviceStyle.of(frame);
        this.openedNanos = PhoneFrameRenderer.beginOpen();
        buildApps();
        this.mainPage = PhonePrototype.enabled ? 1 : 0;
        this.page = mainPage;
        this.viewFrom = mainPage;
    }

    private void buildApps() {
        Runnable openCharacter = () -> Minecraft.getInstance().gui.setScreen(new CharacterScreen());
        Runnable openSkills    = () -> SkillsMenuScreen.open(this);
        Runnable openInventory = () -> Minecraft.getInstance().gui.setScreen(new TotalityInventoryScreen());
        Runnable openBank      = () -> Minecraft.getInstance().gui.setScreen(new BankScreen(frame));
        Runnable openQuests    = () -> ClientQuestManager.openQuestApp(frame);
        // Advisory server-provided entitlement view; the Bank app is visible-but-locked until unlocked.
        boolean bankUnlocked   = ClientEntitlementView.isSelectable(PhoneAppEntitlements.BANK_APP);
        String higherTier      = "Unlocks on a higher-tier phone.";

        // The main page, arranged by purpose: knowledge & world, character powers, economy & tech, phone.
        List<App> all = new ArrayList<>();
        // Codex: knowledge and discoveries (the Bestiary becomes one of its categories). Visible and selectable,
        // with the normal press feedback, but it opens nothing yet.
        all.add(new App("Codex",      null, null, true, null, null, CODEX_ICON));
        all.add(new App("Map",        "Mp", false, "Unlocks with progression.", null));
        all.add(new App("Inventory",  "Inv.", "In", true, null, openInventory, null));
        all.add(new App("Spells",     "Sp", false, higherTier, null));
        all.add(new App("Abilities",  "Ab", false, higherTier, null));
        all.add(new App("Classes",    "Cl", false, higherTier, null));
        // Technology: visible but unavailable until the app exists (no entitlement is implied).
        all.add(new App("Technology", "Tech", "Te", false, "Not available yet.", null, null));
        all.add(new App("Bank",       "Bk", bankUnlocked, bankUnlocked ? null : "Link your phone with a Banker first.", openBank));
        all.add(new App("Store",      "St", false, "Requires Standard account tier.", null));
        all.add(new App("System",     "Sy", true, null, null));
        all.add(new App("Mail",       "Ma", false, "Unlocks once messaging is connected.", null));
        all.add(new App("Settings",   "Se", true, null, null));

        // Favourites: icons only, and not repeated in the grid.
        dock.add(new App("Character", "Ch", true, null, openCharacter));
        dock.add(new App("Skills",    "Sk", true, null, openSkills));
        dock.add(new App("Quests",    "Qu", true, null, openQuests));
        dock.add(new App("Camera",    "Ca", true, null, null));

        mainApps.addAll(all);
        if (PhonePrototype.enabled) pages.add(new Page(List.of(), "Development page (left)"));
        for (int i = 0; i < all.size(); i += PER_PAGE) {
            pages.add(new Page(all.subList(i, Math.min(i + PER_PAGE, all.size())), null));
        }
        if (PhonePrototype.enabled) {
            List<App> stress = new ArrayList<>();
            if (PhonePrototype.labelStress) {
                for (String s : PhonePrototype.STRESS_LABELS) stress.add(new App(s, "T" + (stress.size() + 1), true, null, null));
            }
            pages.add(new Page(stress, "Development page (right)"));
        }
    }

    // ── Geometry ──────────────────────────────────────────────────────────────

    private Geometry geometry() {
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.layout(width, height, style);
        float scale = PhoneUi.displayScale(l);
        int avail = PhoneHomeGeometry.cellWidth(l) - 2;
        int gui = PhoneUi.guiScale();
        // Labels: one size for every app, the largest crisp size (up to the display's) at which every word of every
        // full label fits. Only if no readable size fits them all do apps with a short form switch to it.
        List<String> full = new ArrayList<>(), mixed = new ArrayList<>();
        for (App a : mainApps) {
            full.addAll(List.of(a.label().split(" ")));
            mixed.addAll(List.of((a.shortLabel() != null ? a.shortLabel() : a.label()).split(" ")));
        }
        float labelScale = Math.min(scale, PhoneUi.crispScale(font, full, avail, gui));
        boolean shortLabels = widest(full) * labelScale > avail;
        if (shortLabels) labelScale = Math.min(scale, PhoneUi.crispScale(font, mixed, avail, gui));
        PhoneHomeGeometry home = PhoneHomeGeometry.compute(l, scale, Math.round(9 * labelScale), dock.size());
        return new Geometry(home, scale, labelScale, shortLabels);
    }

    private int widest(List<String> words) {
        int w = 0;
        for (String s : words) w = Math.max(w, font.width(s));
        return w;
    }

    /** The label an app shows: its full name, or its short form when labels are shortened and the full one won't fit. */
    private String displayLabel(Geometry geo, App app, PhoneUi labels) {
        if (!geo.shortLabels() || app.shortLabel() == null) return app.label();
        int avail = geo.home().cellW() - 2;
        for (String w : app.label().split(" ")) if (labels.width(w) > avail) return app.shortLabel();
        return app.label();
    }

    /** The slide-in offset currently applied to the drawn phone (input is mapped back through it). */
    private int slide() {
        return PhoneFrameRenderer.transition(openedNanos).slide();
    }

    /** App {@code index}'s interactive rectangles on the current page (frame and label block). */
    private int[][] appBounds(Geometry geo, int index) {
        App app = pages.get(page).apps().get(index);
        PhoneUi labels = new PhoneUi(null, font, geo.labelScale());
        List<String> lines = labels.wrapLabel(displayLabel(geo, app, labels), geo.home().cellW() - 2);
        int w = 0;
        for (String line : lines) w = Math.max(w, labels.width(line));
        return geo.home().appBounds(index, w, lines.size());
    }

    /** The app whose VISIBLE bounds (frame or label) contain the point on the current page, or -1. */
    private int tileAt(Geometry geo, double mx, double my) {
        if (pageMoving()) return -1;
        for (int i = 0; i < pages.get(page).apps().size(); i++) {
            for (int[] r : appBounds(geo, i)) if (PhoneHomeGeometry.in(r, mx, my)) return i;
        }
        return -1;
    }

    private int dockAt(Geometry geo, double mx, double my) {
        for (int i = 0; i < dock.size(); i++) if (geo.home().hitsDock(i, mx, my)) return i;
        return -1;
    }

    /** The page marker under the pointer, or -1. */
    private int pageMarkerAt(Geometry geo, double mx, double my) {
        int dotsY = geo.home().dotsY();
        if (pages.size() < 2 || my < dotsY - 3 || my >= dotsY + 7) return -1;
        int cx = geo.device().dx() + geo.device().dw() / 2;
        for (int i = 0; i < pages.size(); i++) {
            if (Math.abs(mx - PhoneUi.pageMarkerX(cx, pages.size(), i)) <= 3) return i;
        }
        return -1;
    }

    // ── Page slide ────────────────────────────────────────────────────────────

    /** Where the view is, in pages (fractional while sliding or dragged). */
    private float viewPosition(int displayWidth) {
        if (gesture == Gesture.PAGE_DRAG) return page - pageDrag / displayWidth;
        if (pageAnimStart < 0) return page;
        float t = Math.min(1f, (System.nanoTime() - pageAnimStart) / (float) PAGE_NANOS);
        if (t >= 1f) {
            pageAnimStart = -1;
            return page;
        }
        float eased = 1 - (1 - t) * (1 - t) * (1 - t);
        return viewFrom + (page - viewFrom) * eased;
    }

    private boolean pageMoving() {
        return gesture == Gesture.PAGE_DRAG || pageAnimStart >= 0;
    }

    private void goToPage(int target, float from) {
        if (target < 0 || target >= pages.size()) return;
        if (target != page) click();
        viewFrom = from;
        page = target;
        pageAnimStart = System.nanoTime();
        focus = Math.min(focus, Math.max(0, pages.get(page).apps().size() - 1));
    }

    private void turnPage(int delta) {
        goToPage(page + delta, page);
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
        PhoneHomeGeometry home = geo.home();
        PhoneFrameRenderer.Transition t = PhoneFrameRenderer.transition(openedNanos);
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.drawDevice(g, geo.device(), style, t);
        int shift = l.x() - geo.device().x();
        PhoneUi ui = new PhoneUi(g, font, geo.scale());
        PhoneUi labels = new PhoneUi(g, font, geo.labelScale());
        PhoneTheme os = ui.colors();
        g.pose().pushMatrix();
        g.pose().translate(shift, 0);
        int mxs = mx - shift;
        PhoneFrameRenderer.Layout d = geo.device();
        boolean shadeOpen = shade.isOpen();

        // Pages: the grid slides; the wallpaper, status bar and dock stay put. Mouse hover and keyboard focus are
        // separate states with different frames; hover is tested against the same bounds as clicks.
        float view = viewPosition(d.dw());
        int hovered = keyboardMode || shadeOpen ? -1 : tileAt(geo, mxs, my);
        int focused = keyboardMode && !shadeOpen ? focus : -1;
        g.enableScissor(d.dx(), geo.statusBottom(), d.dx() + d.dw(), home.dotsY() - 2);
        for (int p = 0; p < pages.size(); p++) {
            float offset = (p - view) * d.dw();
            if (Math.abs(offset) >= d.dw()) continue;
            boolean current = p == page && !pageMoving();
            drawPage(ui, labels, geo, pages.get(p), Math.round(offset), current, current ? hovered : -1, current ? focused : -1);
        }
        g.disableScissor();

        // Page indicator, only when there is more than one page.
        int cx = d.dx() + d.dw() / 2;
        if (pages.size() > 1) ui.pageIndicator(cx, home.dotsY(), pages.size(), page, mainPage);

        // Favourites dock: a quiet rounded panel, icons only, equal gaps outside and between (room for up to five).
        ui.roundRect(home.dockX(), home.dockY(), home.dockW(), home.dockH(), os.dock());
        ui.roundOutline(home.dockX(), home.dockY(), home.dockW(), home.dockH(), os.dockLine());
        int dockHover = keyboardMode || shadeOpen ? -1 : dockAt(geo, mxs, my);
        for (int i = 0; i < dock.size(); i++) {
            App app = dock.get(i);
            int x = home.dockIconX(i), y = home.dockIconY();
            PhoneUi.IconState state = pressedDock(i) ? PhoneUi.IconState.PRESSED
                    : i == dockHover ? PhoneUi.IconState.ACTIVE : PhoneUi.IconState.NORMAL;
            ui.appIcon(x, y, home.dockIcon(), app.abbreviation(), app.icon(), 32, state);
            if (i == dockHover) ui.iconFrame(home.frame(x, y, home.dockIcon()), false, false);
            labels.badge(x + home.dockIcon() + 2, y - 2, PhonePrototype.badge(app.label()));
        }

        // Notification shade over everything below the status bar; the status bar stays visible.
        shade.draw(ui, d, geo.statusBottom(), mxs, my);
        drawStatusBar(ui, geo);
        if (PhonePrototype.showBounds) drawBounds(g, geo);
        g.pose().popMatrix();

        // Locked apps can still be selected: the tooltip gives the reason.
        int active = keyboardMode ? focused : hovered;
        if (!shadeOpen && active >= 0 && active < pages.get(page).apps().size()) {
            App app = pages.get(page).apps().get(active);
            if (!app.unlocked() && app.lockReason() != null) {
                int[] pos = home.iconPos(active);
                int tx = keyboardMode ? pos[0] + shift : mx, ty = keyboardMode ? pos[1] + home.icon() : my;
                g.setTooltipForNextFrame(font, Component.literal(app.lockReason()), tx, ty);
            }
        }
        PhoneFrameRenderer.finishDisplay(g, l, style, t);
    }

    private void drawPage(PhoneUi ui, PhoneUi labels, Geometry geo, Page p, int offset, boolean current,
                          int hovered, int focused) {
        PhoneTheme os = ui.colors();
        PhoneHomeGeometry home = geo.home();
        if (p.apps().isEmpty() && p.note() != null) {
            PhoneFrameRenderer.Layout d = geo.device();
            ui.textCentered(p.note(), d.dx() + d.dw() / 2 + offset, (home.gridY() + home.gridBottom()) / 2, os.textFaint());
            return;
        }
        boolean pressLive = pressed >= 0 && System.nanoTime() - pressedNanos < PRESS_NANOS;
        int labelLine = labels.lineHeight();
        for (int i = 0; i < p.apps().size(); i++) {
            App app = p.apps().get(i);
            int[] pos = home.iconPos(i);
            int x = pos[0] + offset, y = pos[1];
            boolean highlighted = i == hovered || i == focused;
            PhoneUi.IconState state = !app.unlocked() ? PhoneUi.IconState.LOCKED
                    : current && pressLive && pressed == i ? PhoneUi.IconState.PRESSED
                    : highlighted ? PhoneUi.IconState.ACTIVE : PhoneUi.IconState.NORMAL;
            ui.appIcon(x, y, home.icon(), app.abbreviation(), app.icon(), 32, state);
            if (highlighted) ui.iconFrame(home.frame(x, y), i == focused, !app.unlocked());
            // Badges belong to real apps only (never to development-page test labels).
            if (p.note() == null) labels.badge(x + home.icon() + 2, y - 2, PhonePrototype.badge(app.label()));
            int color = !app.unlocked() ? os.textFaint() : highlighted ? os.accentBright() : os.text();
            int ly = home.labelY(y);
            int lx = home.cellX(i) + offset + home.cellW() / 2;
            for (String line : labels.wrapLabel(displayLabel(geo, app, labels), home.cellW() - 2)) {
                labels.textCentered(line, lx, ly, color);
                ly += labelLine;
            }
        }
    }

    /** Development overlay ({@code /totalityphone bounds}): every interactive region exactly as hit-testing sees it. */
    private void drawBounds(GuiGraphicsExtractor g, Geometry geo) {
        PhoneHomeGeometry home = geo.home();
        List<int[]> rects = new ArrayList<>();
        PhoneFrameRenderer.Layout d = geo.device();
        rects.add(new int[] {d.dx(), d.dy(), d.dw(), home.statusH()});
        if (shade.isOpen()) {
            rects.addAll(shade.interactiveBounds());
        } else {
            if (!pageMoving()) for (int i = 0; i < pages.get(page).apps().size(); i++) rects.addAll(List.of(appBounds(geo, i)));
            for (int i = 0; i < dock.size(); i++) rects.add(home.frame(home.dockIconX(i), home.dockIconY(), home.dockIcon()));
            int cx = d.dx() + d.dw() / 2;
            if (pages.size() > 1) {
                for (int i = 0; i < pages.size(); i++) {
                    rects.add(new int[] {PhoneUi.pageMarkerX(cx, pages.size(), i) - 3, home.dotsY() - 3, 7, 10});
                }
            }
        }
        for (int[] r : rects) {
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0x40FF40FF);
            PhoneUi.outlineRect(g, r, 0xC0FF40FF);
        }
    }

    private boolean pressedDock(int i) {
        return pressedDock == i && System.nanoTime() - pressedNanos < PRESS_NANOS;
    }

    /** One compact row: game time (real time), then notifications, signal and battery on the right. */
    private void drawStatusBar(PhoneUi ui, Geometry geo) {
        PhoneTheme os = ui.colors();
        PhoneFrameRenderer.Layout d = geo.device();
        int lh = ui.lineHeight();
        int textY = d.dy() + (geo.home().statusH() - lh) / 2 + 1;
        int glyphY = d.dy() + (geo.home().statusH() - PhoneUi.GLYPH_H) / 2;
        ui.text(currentTimeString(), d.dx() + 4, textY, os.text());
        int x = d.dx() + d.dw() - 4 - PhoneUi.BATTERY_W;
        ui.battery(x, glyphY, os.text());
        x -= 4 + PhoneUi.SIGNAL_W;
        ui.signal(x, glyphY, os.text());
        PhoneNotificationShade.Severity severity = shade.highest();
        if (severity != null) {
            String count = Integer.toString(shade.count());
            x -= 5 + ui.width(count);
            ui.text(count, x, textY, os.severity(severity));
            x -= 1 + PhoneUi.severityGlyphWidth(severity);
            ui.severityGlyph(x, glyphY, severity);
        }
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
        List<App> apps = pages.get(page).apps();
        if (index < 0 || index >= apps.size()) return;
        App app = apps.get(index);
        if (!app.unlocked()) return;
        click();
        pressed = index;
        pressedNanos = System.nanoTime();
        if (app.action() != null) app.action().run();
    }

    private void activateDock(int index) {
        App app = dock.get(index);
        click();
        pressedDock = index;
        pressedNanos = System.nanoTime();
        if (app.action() != null) app.action().run();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        if (mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouse, doubleClick);
        // The action happens on release, so that a press can still become a drag (pull, scroll, swipe).
        gesture = Gesture.PENDING;
        // Layout coordinates: the drawn phone may still be sliding in, so map the pointer back through the slide.
        pressX = mouse.x() - slide();
        pressY = mouse.y();
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent mouse, double dx, double dy) {
        if (gesture == Gesture.NONE) return super.mouseDragged(mouse, dx, dy);
        double tx = mouse.x() - slide() - pressX, ty = mouse.y() - pressY;
        Geometry geo = geometry();
        if (gesture == Gesture.PENDING) {
            if (Math.abs(tx) < DRAG_SLOP && Math.abs(ty) < DRAG_SLOP) return true;
            boolean sideways = Math.abs(tx) > Math.abs(ty);
            if (shade.isOpen()) {
                swipeEntry = sideways ? shade.cardAt(pressX, pressY) : null;
                if (swipeEntry != null) {
                    gesture = Gesture.SHADE_SWIPE;
                    shade.beginSwipe(swipeEntry);
                } else {
                    gesture = Gesture.SHADE_SCROLL;
                }
            } else if (!sideways && ty > 0 && geo.device().inDisplay(pressX, pressY) && pressY < geo.statusBottom() + 4) {
                gesture = Gesture.PULL_SHADE;
            } else if (sideways && pages.size() > 1 && pressY >= geo.statusBottom() && pressY < geo.home().dotsY()
                    && geo.device().inDisplay(pressX, pressY)) {
                gesture = Gesture.PAGE_DRAG;
                pageAnimStart = -1;
            } else {
                gesture = Gesture.NONE;
                return true;
            }
        }
        switch (gesture) {
            case PULL_SHADE -> shade.pull(ty);
            case SHADE_SCROLL -> shade.dragVertical(dy);
            case SHADE_SWIPE -> shade.swipe(tx);
            case PAGE_DRAG -> {
                // Resist past the first and last page.
                int w = geo.device().dw();
                boolean edge = (tx > 0 && page == 0) || (tx < 0 && page == pages.size() - 1);
                pageDrag = (float) Math.max(-w, Math.min(w, edge ? tx / 3 : tx));
            }
            default -> { }
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent mouse) {
        Gesture g = gesture;
        gesture = Gesture.NONE;
        if (mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseReleased(mouse);
        switch (g) {
            case PENDING -> tap(pressX, pressY);
            case PULL_SHADE -> shade.releasePull();
            case SHADE_SCROLL -> shade.endVertical();
            case SHADE_SWIPE -> shade.endSwipe();
            case PAGE_DRAG -> {
                float from = page - pageDrag / geometry().device().dw();
                int target = page;
                if (pageDrag < -geometry().device().dw() / 5f) target = page + 1;
                else if (pageDrag > geometry().device().dw() / 5f) target = page - 1;
                pageDrag = 0;
                goToPage(Math.max(0, Math.min(pages.size() - 1, target)), from);
            }
            default -> {
                return super.mouseReleased(mouse);
            }
        }
        return true;
    }

    /** A press and release without a drag. */
    private void tap(double mx, double my) {
        Geometry geo = geometry();
        if (geo.device().inDisplay(mx, my) && my < geo.statusBottom()) {
            // The status bar toggles the shade (a click alternative to pulling it down).
            click();
            if (shade.isOpen()) shade.close();
            else shade.open();
            return;
        }
        if (shade.isOpen()) {
            shade.click(mx, my);
            return;
        }
        int tile = tileAt(geo, mx, my);
        if (tile >= 0) {
            focus = tile;
            activate(tile);
            return;
        }
        int marker = pageMarkerAt(geo, mx, my);
        if (marker >= 0) {
            goToPage(marker, viewPosition(geo.device().dw()));
            return;
        }
        int slot = dockAt(geo, mx, my);
        if (slot >= 0) activateDock(slot);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (shade.isOpen()) {
            shade.scrollBy(-scrollY * 12);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE && shade.isOpen()) {
            shade.close();
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_TAB) {
            Minecraft.getInstance().gui.setScreen(null);
            return true;
        }
        if (shade.isOpen()) return super.keyPressed(event);
        int count = pages.get(page).apps().size();
        int col = focus % COLUMNS, row = focus / COLUMNS;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> {
                keyboardMode = true;
                if (col > 0) focus--;
                else if (page > 0) { turnPage(-1); focus = Math.max(0, Math.min(row * COLUMNS + COLUMNS - 1, pages.get(page).apps().size() - 1)); }
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                keyboardMode = true;
                if (col < COLUMNS - 1 && focus + 1 < count) focus++;
                else if (page < pages.size() - 1) { turnPage(1); focus = Math.max(0, Math.min(row * COLUMNS, pages.get(page).apps().size() - 1)); }
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

    /** Centre of app {@code index}'s icon on the current page, GUI pixels (for the dev capture run). */
    public double[] tileCentre(int index) {
        PhoneHomeGeometry home = geometry().home();
        int[] pos = home.iconPos(index);
        return new double[] {pos[0] + home.icon() / 2.0, pos[1] + home.icon() / 2.0};
    }

    /** Centre of dock entry {@code index}, GUI pixels (for the dev capture run). */
    public double[] dockCentre(int index) {
        PhoneHomeGeometry home = geometry().home();
        return new double[] {home.dockIconX(index) + home.dockIcon() / 2.0, home.dockIconY() + home.dockIcon() / 2.0};
    }

    /** Centre of page marker {@code index}, GUI pixels (for the dev capture run). */
    public double[] pageMarkerCentre(int index) {
        Geometry geo = geometry();
        int cx = geo.device().dx() + geo.device().dw() / 2;
        return new double[] {PhoneUi.pageMarkerX(cx, pages.size(), index), geo.home().dotsY() + 1};
    }

    /** The pure home geometry for the current window (for the dev capture run and diagnostics). */
    public PhoneHomeGeometry homeGeometry() {
        return geometry().home();
    }

    /** App {@code index}'s interactive rectangles on the current page (for the dev capture run). */
    public int[][] appBounds(int index) {
        return appBounds(geometry(), index);
    }

    /** The app on the current page whose interactive bounds contain the point, or -1 (for the dev capture run). */
    public int appAt(double mx, double my) {
        return tileAt(geometry(), mx, my);
    }

    /** The dock entry whose interactive bounds contain the point, or -1 (for the dev capture run). */
    public int dockIndexAt(double mx, double my) {
        return dockAt(geometry(), mx, my);
    }

    /** The label app {@code index} currently shows (full or short form; for the dev capture run). */
    public String shownLabel(int index) {
        Geometry geo = geometry();
        return displayLabel(geo, pages.get(page).apps().get(index), new PhoneUi(null, font, geo.labelScale()));
    }

    /** The label lines app {@code index} shows (for the dev capture run: "..." means it was cut short). */
    public List<String> shownLabelLines(int index) {
        Geometry geo = geometry();
        PhoneUi labels = new PhoneUi(null, font, geo.labelScale());
        return labels.wrapLabel(displayLabel(geo, pages.get(page).apps().get(index), labels), geo.home().cellW() - 2);
    }

    /** Shows page {@code index} immediately, without the slide (development command and capture run). */
    public void showPage(int index) {
        if (index < 0 || index >= pages.size()) return;
        page = index;
        viewFrom = index;
        pageAnimStart = -1;
    }

    /** Whether app {@code index} on the current page is showing its press feedback (for the dev capture run). */
    public boolean isPressed(int index) {
        return pressed == index && System.nanoTime() - pressedNanos < PRESS_NANOS;
    }

    /** Keyboard focus index, or -1 while the mouse drives the selection (for the dev capture run). */
    public int keyboardFocus() {
        return keyboardMode ? focus : -1;
    }

    /** A point in the status bar, GUI pixels (for the dev capture run). */
    public double[] statusBarPoint() {
        Geometry geo = geometry();
        return new double[] {geo.device().dx() + geo.device().dw() / 2.0, geo.device().dy() + geo.home().statusH() / 2.0};
    }

    /** Centre of the display, GUI pixels (for the dev capture run). */
    public double[] displayCentre() {
        PhoneFrameRenderer.Layout l = geometry().device();
        return new double[] {l.dx() + l.dw() / 2.0, l.dy() + l.dh() / 2.0};
    }

    public PhoneNotificationShade shade() {
        return shade;
    }

    public int page() {
        return page;
    }

    public int mainPage() {
        return mainPage;
    }

    public int pageCount() {
        return pages.size();
    }

    /** Label of app {@code index} on the current page (for the dev capture run). */
    public String appLabel(int index) {
        List<App> apps = pages.get(page).apps();
        return index >= 0 && index < apps.size() ? apps.get(index).label() : null;
    }

    /** Label of dock entry {@code index} (for the dev capture run). */
    public String dockLabel(int index) {
        return index >= 0 && index < dock.size() ? dock.get(index).label() : null;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
