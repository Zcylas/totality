package zcylas.totality.screen.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.camera.CameraZoom;
import zcylas.totality.client.photo.Gallery;
import zcylas.totality.client.photo.PhotoMetadata;
import zcylas.totality.client.photo.PhotoTextures;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The full-screen photo viewer: the photograph at its own aspect ratio (letterboxed, never stretched), its name and
 * capture details, previous/next, favourite, rename, and delete with confirmation. It browses the list it was opened
 * from (all photos, or favourites only) and returns to that Gallery view.
 *
 * <ul>
 *   <li>Rename: double-click the name; Enter saves, ESC cancels, clicking elsewhere saves. Only the display name
 *       changes, never the photo's id or files. An empty name is not saved.</li>
 *   <li>Delete: the Delete button or key asks first; Enter or the red button confirms, ESC or Cancel keeps it.</li>
 *   <li>Keys: Left/Right browse, ESC back to the Gallery (or cancels an edit/confirmation), TAB closes the Phone.</li>
 * </ul>
 */
public class PhotoViewerScreen extends Screen {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy  HH:mm", Locale.ROOT);
    static final int DELETE_RED = 0xFFD03A3A;
    static final int NAME_FIELD_H = 12;

    private enum Button { NONE, FAVORITE, DELETE, CLOSE, PREV, NEXT, NAME, CONFIRM_DELETE, CANCEL_DELETE }

    private final PhoneOrigin origin;
    private final boolean favoritesOnly;
    private String currentId;
    private EditBox nameField;
    private boolean confirming;

    public PhotoViewerScreen(PhoneOrigin origin, boolean favoritesOnly, String photoId) {
        super(Component.literal("Photo"));
        this.origin = origin;
        this.favoritesOnly = favoritesOnly;
        this.currentId = photoId;
    }

    /** Geometry for the current window; {@code u} is the size unit (same rule as the Camera's controls). */
    record Layout(int w, int h, int u) {
        int barH() { return 28 * u; }
        /** The rename field (vanilla EditBox, fixed 12 GUI px tall) sits on the name line... */
        int nameFieldX() { return 8 * u - 2; }
        int nameFieldY() { return 2 * u; }
        int nameFieldW() { return 140; }
        /** ...and the date/metadata line always starts below it, at every GUI scale. */
        int detailsY() { return Math.max(17 * u, nameFieldY() + NAME_FIELD_H + 3); }
        int buttonH() { return 14 * u; }
        int buttonY() { return (barH() - buttonH()) / 2; }
        int margin() { return 28 * u; }
        int arrowR() { return 10 * u; }

        /** The photo rectangle {x, y, w, h}: as large as fits between the bars, at its own aspect ratio. */
        int[] photoRect(int pw, int ph) {
            int availW = w - 2 * margin(), availH = h - barH() - 16 * u;
            if (pw <= 0 || ph <= 0) return new int[] {margin(), barH(), availW, availH};
            float s = Math.min(availW / (float) pw, availH / (float) ph);
            int rw = Math.round(pw * s), rh = Math.round(ph * s);
            return new int[] {(w - rw) / 2, barH() + (availH - rh) / 2, rw, rh};
        }
    }

    private Layout layout() {
        return new Layout(width, height, Math.max(1, Math.round(height / 253f)));
    }

    private Gallery gallery() {
        return Gallery.current().orElse(null);
    }

    private List<PhotoMetadata> list() {
        Gallery g = gallery();
        if (g == null) return List.of();
        return favoritesOnly ? g.favorites() : g.photos();
    }

    private int index(List<PhotoMetadata> photos) {
        for (int i = 0; i < photos.size(); i++) if (photos.get(i).id().equals(currentId)) return i;
        return -1;
    }

    private PhotoMetadata current() {
        List<PhotoMetadata> photos = list();
        int i = index(photos);
        return i >= 0 ? photos.get(i) : null;
    }

    // ── Button geometry (right-aligned in the top bar: favourite, delete, close) ──

    private int[] button(Layout l, Button b) {
        int u = l.u(), h = l.buttonH(), y = l.buttonY(), gap = 4 * u;
        int close = h, del = (font.width("Delete") + 12) * u, fav = (font.width(GalleryScreen.STAR) + 10) * u;
        int x = l.w() - 8 * u - close;
        if (b == Button.CLOSE) return new int[] {x, y, close, h};
        x -= gap + del;
        if (b == Button.DELETE) return new int[] {x, y, del, h};
        x -= gap + fav;
        return new int[] {x, y, fav, h};
    }

    private int[] nameRect(Layout l, PhotoMetadata m) {
        int u = l.u();
        return new int[] {8 * u, 3 * u, Math.max(40, font.width(m.name()) + 4) * u, 10 * u};
    }

    private int[] dialog(Layout l) {
        int u = l.u(), w = 150 * u, h = 58 * u;
        return new int[] {(l.w() - w) / 2, (l.h() - h) / 2, w, h};
    }

    private int[] dialogButton(Layout l, boolean delete) {
        int[] d = dialog(l);
        int u = l.u(), bw = 60 * u, bh = 14 * u;
        return new int[] {delete ? d[0] + d[2] - 8 * u - bw : d[0] + 8 * u, d[1] + d[3] - 8 * u - bh, bw, bh};
    }

    private Button at(Layout l, double mx, double my) {
        if (confirming) {
            if (in(mx, my, dialogButton(l, true))) return Button.CONFIRM_DELETE;
            if (in(mx, my, dialogButton(l, false))) return Button.CANCEL_DELETE;
            return Button.NONE;
        }
        for (Button b : new Button[] {Button.FAVORITE, Button.DELETE, Button.CLOSE}) if (in(mx, my, button(l, b))) return b;
        PhotoMetadata m = current();
        if (m != null && in(mx, my, nameRect(l, m))) return Button.NAME;
        List<PhotoMetadata> photos = list();
        int i = index(photos);
        int cy = l.barH() + (l.h() - l.barH()) / 2, r = l.arrowR();
        if (i > 0 && near(mx, my, l.margin() / 2.0, cy, r)) return Button.PREV;
        if (i >= 0 && i < photos.size() - 1 && near(mx, my, l.w() - l.margin() / 2.0, cy, r)) return Button.NEXT;
        return Button.NONE;
    }

    private static boolean in(double mx, double my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static boolean near(double mx, double my, double cx, double cy, double r) {
        return (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= r * r;
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
        g.fill(0, 0, width, height, 0xFF05080C);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
        Layout l = layout();
        int u = l.u();
        PhoneTheme os = PhoneTheme.DEFAULT;
        Gallery gallery = gallery();
        List<PhotoMetadata> photos = list();
        int i = index(photos);
        PhotoMetadata m = i >= 0 ? photos.get(i) : null;
        Button hover = at(l, mx, my);

        if (m == null) {
            text(g, gallery == null || !gallery.ready() ? "Loading…" : "This photo is no longer here.",
                    l.w() / 2, l.h() / 2, os.textDim(), u, true);
        } else {
            drawPhoto(g, l, gallery, m, os);
        }

        // Top bar: name and details left; favourite, delete and close right.
        g.fillGradient(0, 0, l.w(), l.barH() + 6 * u, 0xE0000000, 0x00000000);
        if (m != null) {
            if (nameField == null) {
                int[] nr = nameRect(l, m);
                text(g, m.name(), nr[0], nr[1] + u, hover == Button.NAME ? os.accentBright() : 0xFFFFFFFF, u, false);
            }
            String details = DATE.format(Instant.ofEpochMilli(m.capturedAt()).atZone(ZoneId.systemDefault()))
                    + "  ·  " + m.width() + "×" + m.height() + "  ·  " + CameraZoom.label(m.zoom())
                    + ("full_screen".equals(m.capture()) ? "  ·  full screen (dev)" : "");
            text(g, details, 8 * u, l.detailsY(), os.textDim(), u, false);

            drawButton(g, l, Button.FAVORITE, GalleryScreen.STAR, hover, m.favorite() ? GalleryScreen.FAVORITE_COLOR : 0xFFE4EDF5,
                    m.favorite() ? 0xC0402E08 : 0x90000000);
            drawButton(g, l, Button.DELETE, "Delete", hover, 0xFFFFFFFF, hover == Button.DELETE ? 0xC0802020 : 0x90000000);
            drawButton(g, l, Button.CLOSE, "✕", hover, 0xFFFFFFFF, 0x90000000);

            int cy = l.barH() + (l.h() - l.barH()) / 2;
            if (i > 0) arrow(g, l, l.margin() / 2, cy, false, hover == Button.PREV);
            if (i < photos.size() - 1) arrow(g, l, l.w() - l.margin() / 2, cy, true, hover == Button.NEXT);
            text(g, (i + 1) + " / " + photos.size() + (favoritesOnly ? "  " + GalleryScreen.STAR : ""), l.w() / 2,
                    l.h() - 12 * u, os.textDim(), u, true);
        }

        if (confirming) drawConfirm(g, l, hover, os);
        super.extractRenderState(g, mx, my, a);
    }

    private void drawPhoto(GuiGraphicsExtractor g, Layout l, Gallery gallery, PhotoMetadata m, PhoneTheme os) {
        int u = l.u();
        if (gallery.isMissing(m.id())) {
            int[] r = l.photoRect(16, 9);
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF0E1620);
            text(g, "The image file is missing.", l.w() / 2, l.h() / 2 - 6 * u, os.text(), u, true);
            text(g, "You can delete this entry.", l.w() / 2, l.h() / 2 + 6 * u, os.textDim(), u, true);
            return;
        }
        PhotoTextures.Loaded full = PhotoTextures.full(gallery, m);
        PhotoTextures.Loaded shown = full != null ? full : PhotoTextures.thumbnail(gallery, m);
        int[] r = l.photoRect(m.width() > 0 ? m.width() : shown != null ? shown.width() : 16,
                m.height() > 0 ? m.height() : shown != null ? shown.height() : 9);
        g.fill(r[0] - 1, r[1] - 1, r[0] + r[2] + 1, r[1] + r[3] + 1, 0xFF1E2E40);
        if (shown != null) {
            g.blit(shown.texture().getTextureView(), PhotoTextures.sampler(), r[0], r[1], r[0] + r[2], r[1] + r[3], 0, 1, 0, 1);
        }
        if (full == null) text(g, "Loading…", r[0] + r[2] / 2, r[1] + r[3] - 12 * u, 0xC0FFFFFF, u, true);
    }

    private void drawButton(GuiGraphicsExtractor g, Layout l, Button b, String label, Button hover, int color, int fill) {
        int[] r = button(l, b);
        int u = l.u();
        roundRect(g, r[0], r[1], r[2], r[3], fill);
        if (hover == b) roundOutline(g, r[0], r[1], r[2], r[3], PhoneTheme.DEFAULT.accent());
        text(g, label, r[0] + r[2] / 2, r[1] + (r[3] - 8 * u) / 2, color, u, true);
    }

    private void arrow(GuiGraphicsExtractor g, Layout l, int cx, int cy, boolean right, boolean hot) {
        int r = l.arrowR(), u = l.u();
        roundRect(g, cx - r, cy - r, 2 * r, 2 * r, hot ? 0xC0173A52 : 0x90000000);
        if (hot) roundOutline(g, cx - r, cy - r, 2 * r, 2 * r, PhoneTheme.DEFAULT.accent());
        text(g, right ? "›" : "‹", cx, cy - 4 * u * 2 + u, 0xFFFFFFFF, 2 * u, true);
    }

    private void drawConfirm(GuiGraphicsExtractor g, Layout l, Button hover, PhoneTheme os) {
        int u = l.u();
        g.fill(0, 0, l.w(), l.h(), 0x90000000);
        int[] d = dialog(l);
        roundRect(g, d[0], d[1], d[2], d[3], 0xF00E1620);
        roundOutline(g, d[0], d[1], d[2], d[3], os.cardLine());
        text(g, "Delete this photograph?", l.w() / 2, d[1] + 8 * u, 0xFFFFFFFF, u, true);
        text(g, "This cannot be undone.", l.w() / 2, d[1] + 20 * u, os.textDim(), u, true);
        int[] cancel = dialogButton(l, false), del = dialogButton(l, true);
        roundRect(g, cancel[0], cancel[1], cancel[2], cancel[3], hover == Button.CANCEL_DELETE ? os.tileActive() : os.tile());
        roundRect(g, del[0], del[1], del[2], del[3], hover == Button.CONFIRM_DELETE ? 0xFFE04848 : DELETE_RED);
        text(g, "Cancel", cancel[0] + cancel[2] / 2, cancel[1] + (cancel[3] - 8 * u) / 2, 0xFFFFFFFF, u, true);
        text(g, "Delete", del[0] + del[2] / 2, del[1] + (del[3] - 8 * u) / 2, 0xFFFFFFFF, u, true);
    }

    private void text(GuiGraphicsExtractor g, String s, int x, int y, int color, int scale, boolean centred) {
        g.pose().pushMatrix();
        g.pose().translate(centred ? x - font.width(s) * scale / 2f : x, y);
        g.pose().scale(scale, scale);
        g.text(font, s, 0, 0, color, true);
        g.pose().popMatrix();
    }

    private static void roundRect(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static void roundOutline(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.fill(x + 1, y, x + w - 1, y + 1, color);
        g.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        g.fill(x, y + 1, x + 1, y + h - 1, color);
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    // ── Actions ───────────────────────────────────────────────────────────────

    private void step(int delta) {
        List<PhotoMetadata> photos = list();
        int i = index(photos) + delta;
        if (i < 0 || i >= photos.size()) return;
        click();
        currentId = photos.get(i).id();
    }

    private void toggleFavorite() {
        PhotoMetadata m = current();
        Gallery g = gallery();
        if (m == null || g == null) return;
        click();
        // Unfavouriting while browsing favourites removes it from that list: move on to a neighbour first.
        if (favoritesOnly && m.favorite()) moveAfterRemoval();
        g.setFavorite(m.id(), !m.favorite());
        if (currentId == null) backToGallery();
    }

    private void beginRename() {
        PhotoMetadata m = current();
        if (m == null || nameField != null) return;
        Layout l = layout();
        nameField = new EditBox(font, l.nameFieldX(), l.nameFieldY(), l.nameFieldW(), NAME_FIELD_H, Component.literal("Photo name"));
        nameField.setMaxLength(PhotoMetadata.MAX_NAME_LENGTH);
        nameField.setValue(m.name());
        nameField.moveCursorToEnd(false);
        nameField.setHighlightPos(0);
        addRenderableWidget(nameField);
        setFocused(nameField);
    }

    /** Ends editing; saves when {@code save} and the name is not blank. */
    private void endRename(boolean save) {
        if (nameField == null) return;
        PhotoMetadata m = current();
        Gallery g = gallery();
        if (save && m != null && g != null) g.rename(m.id(), nameField.getValue());
        removeWidget(nameField);
        nameField = null;
        setFocused(null);
    }

    private void deleteCurrent() {
        PhotoMetadata m = current();
        Gallery g = gallery();
        confirming = false;
        if (m == null || g == null) return;
        moveAfterRemoval();
        g.delete(m.id());
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 0.8F, 0.6F));
        if (currentId == null) backToGallery();
    }

    /** Selects the photo that takes the current one's place in this list (the next, else the previous), or none. */
    private void moveAfterRemoval() {
        List<PhotoMetadata> photos = list();
        int i = index(photos);
        if (i < 0) return;
        if (i + 1 < photos.size()) currentId = photos.get(i + 1).id();
        else if (i > 0) currentId = photos.get(i - 1).id();
        else currentId = null;
    }

    private void backToGallery() {
        Minecraft.getInstance().gui.setScreen(new GalleryScreen(origin, favoritesOnly, currentId));
    }

    private static void click() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        if (mouse.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return super.mouseClicked(mouse, doubleClick);
        if (nameField != null) {
            if (nameField.isMouseOver(mouse.x(), mouse.y())) return super.mouseClicked(mouse, doubleClick);
            endRename(true);
            return true;
        }
        Layout l = layout();
        switch (at(l, mouse.x(), mouse.y())) {
            case FAVORITE -> toggleFavorite();
            case DELETE -> {
                click();
                confirming = true;
            }
            case CLOSE -> {
                click();
                backToGallery();
            }
            case PREV -> step(-1);
            case NEXT -> step(1);
            case NAME -> {
                // The first click is accepted (so the second one counts as a double click); the double click edits.
                if (doubleClick) beginRename();
            }
            case CONFIRM_DELETE -> deleteCurrent();
            case CANCEL_DELETE -> {
                click();
                confirming = false;
            }
            default -> {
                return confirming || super.mouseClicked(mouse, doubleClick);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (nameField == null && !confirming && scrollY != 0) step(scrollY > 0 ? -1 : 1);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        boolean enter = key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
        if (key == GLFW.GLFW_KEY_TAB) {
            endRename(false);
            origin.closeAll();
            return true;
        }
        if (nameField != null) {
            if (enter || key == GLFW.GLFW_KEY_ESCAPE) {
                endRename(enter);
                return true;
            }
            return super.keyPressed(event);
        }
        if (confirming) {
            if (enter) deleteCurrent();
            else if (key == GLFW.GLFW_KEY_ESCAPE) confirming = false;
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> backToGallery();
            case GLFW.GLFW_KEY_LEFT -> step(-1);
            case GLFW.GLFW_KEY_RIGHT -> step(1);
            case GLFW.GLFW_KEY_DELETE -> confirming = current() != null;
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public void removed() {
        PhotoTextures.releaseFull();
    }

    // ── Diagnostics (development capture run) ─────────────────────────────────

    public String currentId() {
        return currentId;
    }

    public boolean editing() {
        return nameField != null;
    }

    public boolean confirming() {
        return confirming;
    }

    /** Centre of a top-bar control: "favorite", "delete", "close", "name", "prev", "next", "confirm", "cancel". */
    public double[] controlCentre(String which) {
        Layout l = layout();
        int[] r = switch (which) {
            case "favorite" -> button(l, Button.FAVORITE);
            case "delete" -> button(l, Button.DELETE);
            case "close" -> button(l, Button.CLOSE);
            case "name" -> current() != null ? nameRect(l, current()) : new int[4];
            case "confirm" -> dialogButton(l, true);
            case "cancel" -> dialogButton(l, false);
            default -> null;
        };
        if (r != null) return new double[] {r[0] + r[2] / 2.0, r[1] + r[3] / 2.0};
        int cy = l.barH() + (l.h() - l.barH()) / 2;
        return new double[] {which.equals("prev") ? l.margin() / 2.0 : l.w() - l.margin() / 2.0, cy};
    }

    /** The photo's drawn rectangle {x, y, w, h} (aspect-ratio check). */
    public int[] photoRect() {
        PhotoMetadata m = current();
        return layout().photoRect(m != null ? m.width() : 16, m != null ? m.height() : 9);
    }

    /** The live rename field {x, y, w, h}, or null when not editing (development capture run). */
    public int[] nameFieldRect() {
        return nameField == null ? null : new int[] {nameField.getX(), nameField.getY(), nameField.getWidth(), nameField.getHeight()};
    }

    /** Top of the date/metadata line (development capture run). */
    public int detailsTop() {
        return layout().detailsY();
    }

    /** Types into the name field as a player would (development capture run). */
    public void typeName(String value) {
        if (nameField != null) nameField.setValue(value);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
    @Override public boolean isInGameUi()        { return false; }
    @Override public boolean isPauseScreen()     { return false; }
}
