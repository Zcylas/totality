package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.camera.ShutterAnimation;
import zcylas.totality.client.photo.Gallery;
import zcylas.totality.client.photo.PhotoMetadata;
import zcylas.totality.client.photo.PhotoTextures;

import java.util.List;

/**
 * The Gallery app, inside the Phone: a scrollable grid of the current world/server's photographs (newest first) with
 * their names and favourite marks, an All / Favourites switch, and empty, loading and unavailable states. Selecting a
 * photo opens the full-screen {@link PhotoViewerScreen}.
 *
 * <p>The grid only ever draws thumbnails ({@link PhotoTextures}, 256 px wide, cached), never full photographs.
 * ESC returns to where the Gallery was opened from ({@link PhoneOrigin}: the Camera or a home page); TAB closes the
 * Phone. Mouse: click a tab or a photo, wheel to scroll. Keyboard: arrows move the selection, Enter opens it.
 */
public class GalleryScreen extends Screen {

    static final int COLUMNS = 3;
    static final int FAVORITE_COLOR = 0xFFFFC94A;
    static final String STAR = "★";

    private final PhoneOrigin origin;
    private final PhoneDeviceStyle style;
    private final long openedNanos;
    private boolean favoritesOnly;
    private float scroll;
    private int focus = -1;
    private boolean keyboardMode;
    /** A photo to bring into view on the first frame (returning from the viewer). */
    private String revealId;

    public GalleryScreen(PhoneOrigin origin) {
        this(origin, false, null);
    }

    GalleryScreen(PhoneOrigin origin, boolean favoritesOnly, String revealId) {
        super(Component.literal("Gallery"));
        this.origin = origin;
        this.style = PhoneDeviceStyle.of(origin.frame());
        this.openedNanos = PhoneFrameRenderer.beginOpen();
        this.favoritesOnly = favoritesOnly;
        this.revealId = revealId;
    }

    /** Everything drawing and input share for one window size. */
    record Grid(PhoneFrameRenderer.Layout device, float scale, int headerH, int tabsY, int tabsH, int top, int bottom,
                int pad, int gap, int cell, int labelH) {
        int rowH() { return cell + labelH + gap; }
        int cellX(int i) { return device.dx() + pad + (i % COLUMNS) * (cell + gap); }
        int cellY(int i, float scroll) { return top + (i / COLUMNS) * rowH() - Math.round(scroll); }
        int tabW() { return (device.dw() - 2 * pad - gap) / 2; }
        int tabX(boolean favorites) { return device.dx() + pad + (favorites ? tabW() + gap : 0); }
        int contentHeight(int count) { return count == 0 ? 0 : ((count + COLUMNS - 1) / COLUMNS) * rowH() - gap; }
        float maxScroll(int count) { return Math.max(0, contentHeight(count) - (bottom - top)); }
    }

    private Grid grid() {
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.layout(width, height, style);
        float scale = PhoneUi.displayScale(l);
        PhoneUi ui = new PhoneUi(null, font, scale);
        int headerH = ui.headerHeight();
        int pad = 4, gap = 3;
        int tabsY = l.dy() + headerH + 3, tabsH = ui.lineHeight() + 5;
        int cell = (l.dw() - 2 * pad - (COLUMNS - 1) * gap) / COLUMNS;
        return new Grid(l, scale, headerH, tabsY, tabsH, tabsY + tabsH + 4, l.dy() + l.dh() - 3, pad, gap, cell, ui.lineHeight() + 2);
    }

    private Gallery gallery() {
        return Gallery.current().orElse(null);
    }

    private List<PhotoMetadata> list() {
        Gallery g = gallery();
        if (g == null) return List.of();
        return favoritesOnly ? g.favorites() : g.photos();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        // Left intentionally empty — the game world stays visible around the phone.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        super.extractRenderState(g, mx, my, a);
        Grid geo = grid();
        PhoneFrameRenderer.Transition t = PhoneFrameRenderer.transition(openedNanos);
        PhoneFrameRenderer.Layout l = PhoneFrameRenderer.drawDevice(g, geo.device(), style, t);
        int shift = l.x() - geo.device().x();
        g.pose().pushMatrix();
        g.pose().translate(shift, 0);
        int mxs = mx - shift;
        PhoneUi ui = new PhoneUi(g, font, geo.scale());
        PhoneTheme os = ui.colors();
        PhoneFrameRenderer.Layout d = geo.device();
        Gallery gallery = gallery();
        List<PhotoMetadata> photos = list();
        clampScroll(geo, photos.size());
        if (revealId != null) reveal(geo, photos);

        ui.header(d.dx(), d.dy(), d.dw(), "GALLERY", "[ESC] Back");

        // All / Favourites.
        int all = gallery != null ? gallery.photos().size() : 0, favs = gallery != null ? gallery.favorites().size() : 0;
        drawTab(ui, geo, false, "All " + all, mxs, my);
        drawTab(ui, geo, true, STAR + " " + favs, mxs, my);

        int cx = d.dx() + d.dw() / 2, midY = (geo.top() + geo.bottom()) / 2;
        if (gallery == null || !gallery.ready()) {
            ui.textCentered("Loading…", cx, midY - ui.lineHeight() / 2, os.textDim());
        } else if (gallery.failed()) {
            ui.textCentered("Gallery unavailable", cx, midY - ui.lineHeight(), os.text());
            ui.textCentered("See the game log.", cx, midY + 2, os.textDim());
        } else if (photos.isEmpty()) {
            ui.textCentered(favoritesOnly ? "No favourites yet" : "No photos yet", cx, midY - ui.lineHeight(), os.text());
            String hint = favoritesOnly ? "Mark photos with " + STAR : "Take one with the Camera.";
            ui.textCentered(ui.ellipsize(hint, d.dw() - 8), cx, midY + 2, os.textDim());
        } else {
            int hovered = keyboardMode ? -1 : cellAt(geo, photos.size(), mxs, my);
            g.enableScissor(d.dx(), geo.top() - 1, d.dx() + d.dw(), geo.bottom());
            for (int i = 0; i < photos.size(); i++) {
                int y = geo.cellY(i, scroll);
                if (y + geo.rowH() < geo.top() || y > geo.bottom()) continue;
                drawCell(g, ui, geo, gallery, photos.get(i), geo.cellX(i), y, i == hovered, keyboardMode && i == focus);
            }
            g.disableScissor();
            float max = geo.maxScroll(photos.size());
            if (max > 0) {
                int track = geo.bottom() - geo.top();
                int knob = Math.max(8, Math.round(track * track / (float) (track + max)));
                int ky = geo.top() + Math.round((track - knob) * (scroll / max));
                g.fill(d.dx() + d.dw() - 2, ky, d.dx() + d.dw() - 1, ky + knob, os.textDim());
            }
        }
        g.pose().popMatrix();
        PhoneFrameRenderer.finishDisplay(g, l, style, t);
    }

    private void drawTab(PhoneUi ui, Grid geo, boolean favorites, String label, int mx, int my) {
        PhoneTheme os = ui.colors();
        int x = geo.tabX(favorites), w = geo.tabW();
        boolean on = favorites == favoritesOnly;
        boolean hot = in(mx, my, x, geo.tabsY(), w, geo.tabsH());
        ui.roundRect(x, geo.tabsY(), w, geo.tabsH(), on ? os.tileActive() : os.tile());
        if (on || hot) ui.roundOutline(x, geo.tabsY(), w, geo.tabsH(), on ? os.accent() : os.textDim());
        ui.textCentered(label, x + w / 2, geo.tabsY() + (geo.tabsH() - ui.lineHeight()) / 2 + 1,
                on ? os.accentBright() : favorites ? FAVORITE_COLOR : os.text());
    }

    private void drawCell(GuiGraphicsExtractor g, PhoneUi ui, Grid geo, Gallery gallery, PhotoMetadata m, int x, int y,
                          boolean hovered, boolean focused) {
        PhoneTheme os = ui.colors();
        int s = geo.cell();
        g.fill(x, y, x + s, y + s, os.tileDark());
        if (gallery.isMissing(m.id())) {
            ui.textCentered("missing", x + s / 2, y + (s - ui.lineHeight()) / 2, os.textFaint());
        } else {
            PhotoTextures.Loaded t = PhotoTextures.thumbnail(gallery, m);
            if (t != null) {
                float[] uv = ShutterAnimation.cropUv(t.width(), t.height(), 1f);
                g.blit(t.texture().getTextureView(), PhotoTextures.sampler(), x, y, x + s, y + s, uv[0], uv[2], uv[1], uv[3]);
            }
        }
        if (m.favorite()) {
            int bw = ui.width(STAR) + 3, bh = ui.lineHeight() + 1;
            ui.roundRect(x + s - bw - 1, y + 1, bw, bh, 0xB0000000);
            ui.text(STAR, x + s - bw + 1, y + 2, FAVORITE_COLOR);
        }
        if (hovered || focused) ui.roundOutline(x - 1, y - 1, s + 2, s + 2, focused ? os.accentBright() : os.accent());
        ui.textCentered(ui.ellipsize(m.name(), s), x + s / 2, y + s + 2, hovered || focused ? os.accentBright() : os.text());
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private static boolean in(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private int cellAt(Grid geo, int count, double mx, double my) {
        if (my < geo.top() || my >= geo.bottom()) return -1;
        for (int i = 0; i < count; i++) {
            if (in(mx, my, geo.cellX(i), geo.cellY(i, scroll), geo.cell(), geo.cell() + geo.labelH())) return i;
        }
        return -1;
    }

    private void clampScroll(Grid geo, int count) {
        scroll = Math.max(0, Math.min(geo.maxScroll(count), scroll));
    }

    /** Scrolls so photo {@code index} is fully visible. */
    private void ensureVisible(Grid geo, int index) {
        int y = geo.top() + (index / COLUMNS) * geo.rowH();
        if (y - scroll < geo.top()) scroll = y - geo.top();
        if (y + geo.rowH() - scroll > geo.bottom()) scroll = y + geo.rowH() - geo.bottom();
    }

    private void reveal(Grid geo, List<PhotoMetadata> photos) {
        for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).id().equals(revealId)) {
                focus = i;
                ensureVisible(geo, i);
                clampScroll(geo, photos.size());
                break;
            }
        }
        revealId = null;
    }

    private int slide() {
        return PhoneFrameRenderer.transition(openedNanos).slide();
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        if (mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouse, doubleClick);
        Grid geo = grid();
        double mx = mouse.x() - slide(), my = mouse.y();
        PhoneFrameRenderer.Layout d = geo.device();
        keyboardMode = false;
        if (in(mx, my, d.dx(), d.dy(), d.dw(), geo.headerH())) {
            click();
            origin.back();
            return true;
        }
        for (boolean fav : new boolean[] {false, true}) {
            if (in(mx, my, geo.tabX(fav), geo.tabsY(), geo.tabW(), geo.tabsH())) {
                setFavoritesOnly(fav);
                return true;
            }
        }
        List<PhotoMetadata> photos = list();
        int i = cellAt(geo, photos.size(), mx, my);
        if (i >= 0) {
            open(photos.get(i));
            return true;
        }
        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Grid geo = grid();
        scroll -= (float) (scrollY * geo.rowH() / 2);
        clampScroll(geo, list().size());
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            origin.back();
            return true;
        }
        if (key == GLFW.GLFW_KEY_TAB) {
            origin.closeAll();
            return true;
        }
        List<PhotoMetadata> photos = list();
        if (photos.isEmpty()) return super.keyPressed(event);
        int move = switch (key) {
            case GLFW.GLFW_KEY_LEFT -> -1;
            case GLFW.GLFW_KEY_RIGHT -> 1;
            case GLFW.GLFW_KEY_UP -> -COLUMNS;
            case GLFW.GLFW_KEY_DOWN -> COLUMNS;
            default -> 0;
        };
        if (move != 0) {
            keyboardMode = true;
            focus = Math.max(0, Math.min(photos.size() - 1, focus < 0 ? 0 : focus + move));
            Grid geo = grid();
            ensureVisible(geo, focus);
            clampScroll(geo, photos.size());
            return true;
        }
        if ((key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) && focus >= 0
                && focus < photos.size()) {
            open(photos.get(focus));
            return true;
        }
        return super.keyPressed(event);
    }

    private void setFavoritesOnly(boolean fav) {
        if (fav == favoritesOnly) return;
        click();
        favoritesOnly = fav;
        scroll = 0;
        focus = -1;
    }

    private void open(PhotoMetadata m) {
        click();
        Minecraft.getInstance().gui.setScreen(new PhotoViewerScreen(origin, favoritesOnly, m.id()));
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ── Diagnostics (development capture run) ─────────────────────────────────

    /** Centre of photo cell {@code index} (GUI px, current scroll). */
    public double[] cellCentre(int index) {
        Grid geo = grid();
        return new double[] {geo.cellX(index) + geo.cell() / 2.0, geo.cellY(index, scroll) + geo.cell() / 2.0};
    }

    /** Centre of the All ({@code false}) or Favourites tab. */
    public double[] tabCentre(boolean favorites) {
        Grid geo = grid();
        return new double[] {geo.tabX(favorites) + geo.tabW() / 2.0, geo.tabsY() + geo.tabsH() / 2.0};
    }

    public boolean favoritesOnly() {
        return favoritesOnly;
    }

    public int shownCount() {
        return list().size();
    }

    public PhoneOrigin origin() {
        return origin;
    }

    public float scroll() {
        return scroll;
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
