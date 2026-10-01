package zcylas.totality.client.camera;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.photo.Gallery;
import zcylas.totality.client.photo.PhotoCapture;
import zcylas.totality.client.photo.PhotoMetadata;
import zcylas.totality.init.ModKeybinds;
import zcylas.totality.screen.phone.GalleryScreen;
import zcylas.totality.screen.phone.PhoneAppGridScreen;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneOrigin;
import zcylas.totality.screen.phone.PhotoViewerScreen;

/**
 * The Camera app while it is open: a full-screen viewfinder over normal play. It is deliberately NOT a {@code Screen}:
 * with no screen open the player keeps walking, sprinting, flying and aiming with the mouse exactly as usual, while the
 * Camera claims only what would conflict (see {@link #onMouseButton}, {@link #onKey}, {@link #onScroll}).
 *
 * <ul>
 *   <li><b>Left click</b>: shutter (Normal mode). No mouse button ever reaches attack, block breaking, item use or
 *       pick-block while the Camera is open.</li>
 *   <li><b>Hold Alt</b> (Totality's modifier key, rebindable as "Radial Modifier"): the cursor appears so the
 *       on-screen controls can be clicked, and a sideways drag across the viewfinder switches mode. Releasing Alt
 *       re-centres and hides the cursor (vanilla's grab, which ignores the first motion), so aiming resumes without a
 *       jump or a stray click.</li>
 *   <li><b>Wheel</b>: smooth zoom. <b>Up/Down</b>: next/previous zoom preset. <b>Left/Right</b>: switch mode (Left =
 *       swipe left = towards Scan).</li>
 *   <li><b>ESC</b>: back to the Phone home page the Camera was opened from. <b>TAB</b>: close the whole Phone session.</li>
 * </ul>
 *
 * <p>The zoom changes only the Camera's own view (see {@code CameraFovMixin}); the player's FOV setting is never
 * written, so closing the Camera — by any path, including death, disconnect or a world change — restores the normal
 * view. The previous perspective (first/third person) is restored too.
 */
public final class CameraSession {

    /** Pointer travel (GUI px) after which an Alt-cursor press becomes a swipe instead of a click. */
    static final int SWIPE_SLOP = 24;
    static final long MESSAGE_NANOS = 2_200_000_000L;

    private static CameraSession active;
    /** The session behind a Gallery opened from the Camera; it resumes when that Gallery closes. */
    private static CameraSession suspended;

    final PhoneFrame frame;
    final int originPage;
    final CameraZoom zoom = new CameraZoom(CameraZoom.BASIC);
    CameraMode mode = CameraMode.NORMAL;
    /** Mode selector position (fractional while sliding). */
    float modePosition;
    long openedNanos;
    boolean cursor;
    private CameraType previousCameraType;

    // Shutter feedback.
    PhotoCapture.Preview flying;
    long flyingSince;
    /** The newest capture's preview, shown in the Gallery shortcut until its saved thumbnail is ready. */
    PhotoCapture.Preview shortcutPreview;
    String message;
    long messageSince;
    private int shotsTaken;
    private int shotsSaved;

    // Alt-cursor gesture.
    private boolean pressing;
    private double pressX, pressY;

    private CameraSession(PhoneFrame frame, int originPage) {
        this.frame = frame;
        this.originPage = originPage;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** From the Phone: closes the Phone and opens the viewfinder. {@code originPage} is the home page to return to. */
    public static void open(PhoneFrame frame, int originPage) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (active != null) active.restore();
        suspended = null;
        CameraSession s = new CameraSession(frame, originPage);
        active = s;
        mc.gui.setScreen(null);
        s.begin();
        Gallery.current();
    }

    private void begin() {
        Minecraft mc = Minecraft.getInstance();
        openedNanos = System.nanoTime();
        previousCameraType = mc.options.getCameraType();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        LocalPlayer player = mc.player;
        if (player != null && player.isUsingItem() && mc.gameMode != null) mc.gameMode.releaseUsingItem(player);
        drainGameplayClicks();
    }

    /** Undo everything the session changed (no screen changes: also safe during disconnect). */
    private void restore() {
        Minecraft mc = Minecraft.getInstance();
        if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
        if (flying != null) flying.release();
        if (shortcutPreview != null && shortcutPreview != flying) shortcutPreview.release();
        flying = null;
        shortcutPreview = null;
        cursor = false;
        pressing = false;
    }

    /** ESC: back to the originating Phone home page. */
    public void back() {
        close();
        Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(frame, originPage));
    }

    /** TAB: the whole Phone session closes; back to play. */
    public void closeAll() {
        close();
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() != null) mc.gui.setScreen(null);
        else if (!mc.mouseHandler.isMouseGrabbed()) mc.mouseHandler.grabMouse();
    }

    private void close() {
        restore();
        if (active == this) active = null;
    }

    /** The bottom-left shortcut: the shared Gallery, returning here when it closes. */
    public void openGallery() {
        click(1.2f);
        // Keep the perspective/zoom state; the viewfinder is simply hidden while the Gallery is up.
        suspended = this;
        active = null;
        cursor = false;
        pressing = false;
        Minecraft.getInstance().gui.setScreen(new GalleryScreen(PhoneOrigin.camera(frame, originPage)));
    }

    /** The Gallery (opened from the Camera) closed with ESC: the viewfinder comes back as it was. */
    public static void resumeFromGallery() {
        CameraSession s = suspended;
        suspended = null;
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.player == null) {
            mc.gui.setScreen(null);
            return;
        }
        active = s;
        s.openedNanos = System.nanoTime();
        mc.gui.setScreen(null);
        s.drainGameplayClicks();
    }

    /** TAB in a Gallery opened from the Camera: the whole session ends. */
    public static void endSuspended() {
        if (suspended != null) suspended.restore();
        suspended = null;
    }

    /** Disconnect or world change: drop everything without touching screens (they may be tearing down). */
    public static void reset() {
        if (active != null) active.restore();
        if (suspended != null) suspended.restore();
        active = null;
        suspended = null;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static CameraSession active() {
        return active;
    }

    /** Open, and no other screen (chat, pause, inventory...) is taking the input. */
    static boolean live() {
        Minecraft mc = Minecraft.getInstance();
        return active != null && mc.gui.screen() == null && mc.gui.overlay() == null;
    }

    /** The viewfinder hides the HUD, hand and block outline, except during a development full-screen capture. */
    public static boolean hidesHud() {
        return active != null && !PhotoCapture.fullScreen();
    }

    /** Client tick: lifecycle checks. */
    public static void tick(Minecraft mc) {
        if (suspended != null && !(mc.gui.screen() instanceof GalleryScreen) && !(mc.gui.screen() instanceof PhotoViewerScreen)) {
            endSuspended();
        }
        CameraSession s = active;
        if (s == null) return;
        if (mc.player == null || mc.level == null) {
            reset();
            return;
        }
        if (mc.player.isDeadOrDying()) {
            s.close();
            return;
        }
        if (!mc.options.getCameraType().isFirstPerson()) mc.options.setCameraType(CameraType.FIRST_PERSON);
        drainGameplayClicks();
    }

    /** Clicks queued by key mappings bound to keys the Camera doesn't intercept never turn into gameplay actions. */
    static void drainGameplayClicks() {
        Minecraft mc = Minecraft.getInstance();
        while (mc.options.keyAttack.consumeClick()) { }
        while (mc.options.keyUse.consumeClick()) { }
        while (mc.options.keyPickItem.consumeClick()) { }
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
    }

    // ── Per frame ─────────────────────────────────────────────────────────────

    /** The view's FOV for the player's 1× setting {@code baseFov} (called once per frame by the camera). */
    public static float fov(float baseFov) {
        CameraSession s = active;
        if (s == null) return baseFov;
        return CameraZoom.fov(baseFov, s.zoom.update(System.nanoTime()));
    }

    /** Mouse-look sensitivity multiplier while the Camera is open. */
    public static double lookSensitivity() {
        CameraSession s = active;
        return s == null ? 1.0 : CameraZoom.sensitivity(s.zoom.current());
    }

    /** Frame update before drawing: the Alt cursor follows the key. */
    void frame() {
        Minecraft mc = Minecraft.getInstance();
        boolean want = live() && ModKeybinds.isPhysicallyDown(ModKeybinds.RADIAL_MODIFIER);
        if (want != cursor) {
            cursor = want;
            pressing = false;
            if (want) mc.mouseHandler.releaseMouse();
            else if (live()) mc.mouseHandler.grabMouse();
        }
        float target = mode.ordinal();
        modePosition += (target - modePosition) * 0.25f;
        if (Math.abs(target - modePosition) < 0.01f) modePosition = target;
        if (message != null && System.nanoTime() - messageSince > MESSAGE_NANOS) message = null;
        long now = System.nanoTime();
        if (flying != null && now - flyingSince >= ShutterAnimation.duration(reducedMotion())) {
            flying = null;
        }
    }

    static boolean reducedMotion() {
        return Minecraft.getInstance().options.screenEffectScale().get() <= 0.0;
    }

    // ── Input (from the input mixins; only while live) ───────────────────────

    /** A mouse button. True = consumed (always, while live): nothing reaches gameplay. */
    public static boolean onMouseButton(int button, boolean pressed) {
        CameraSession s = active;
        if (s == null || !live()) return false;
        if (!s.cursor) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressed) s.shutter();
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        Window w = mc.getWindow();
        double mx = mc.mouseHandler.getScaledXPos(w), my = mc.mouseHandler.getScaledYPos(w);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        if (pressed) {
            s.pressing = true;
            s.pressX = mx;
            s.pressY = my;
            return true;
        }
        if (!s.pressing) return true;
        s.pressing = false;
        double dx = mx - s.pressX;
        if (Math.abs(dx) >= SWIPE_SLOP && Math.abs(dx) > Math.abs(my - s.pressY)) {
            s.switchMode(dx < 0 ? 1 : -1);
            return true;
        }
        s.activate(CameraViewfinder.layout(w.getGuiScaledWidth(), w.getGuiScaledHeight()).at(s.pressX, s.pressY, s.mode));
        return true;
    }

    /** The wheel zooms. True = consumed. */
    public static boolean onScroll(double notches) {
        CameraSession s = active;
        if (s == null || !live()) return false;
        s.zoom.wheel(notches);
        return true;
    }

    /** Key presses (press and repeat). True = consumed. */
    public static boolean onKey(KeyEvent event) {
        CameraSession s = active;
        if (s == null || !live()) return false;
        Minecraft mc = Minecraft.getInstance();
        int key = event.key();
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            s.back();
            return true;
        }
        if (ModKeybinds.OPEN_MENU.matches(event)) {
            s.closeAll();
            return true;
        }
        if (mc.options.keyTogglePerspective.matches(event)) return true;
        switch (key) {
            case GLFW.GLFW_KEY_LEFT -> s.switchMode(1);
            case GLFW.GLFW_KEY_RIGHT -> s.switchMode(-1);
            case GLFW.GLFW_KEY_UP -> s.zoom.stepPreset(1);
            case GLFW.GLFW_KEY_DOWN -> s.zoom.stepPreset(-1);
            default -> {
                return false;
            }
        }
        return true;
    }

    void activate(CameraViewfinder.Control control) {
        switch (control) {
            case SHUTTER -> shutter();
            case GALLERY -> openGallery();
            case BACK -> {
                click(1f);
                back();
            }
            case MODE_NORMAL -> setMode(CameraMode.NORMAL);
            case MODE_SCAN -> setMode(CameraMode.SCAN);
            case ZOOM_0, ZOOM_1, ZOOM_2 -> {
                int i = control.ordinal() - CameraViewfinder.Control.ZOOM_0.ordinal();
                if (i < zoom.range().presets().size()) {
                    click(1.3f);
                    zoom.setTarget(zoom.range().presets().get(i));
                }
            }
            default -> { }
        }
    }

    /** {@code direction} +1 = swipe left (towards Scan), -1 = swipe right (towards Normal). */
    void switchMode(int direction) {
        int next = mode.ordinal() + direction;
        if (next < 0 || next >= CameraMode.values().length) return;
        setMode(CameraMode.values()[next]);
    }

    void setMode(CameraMode m) {
        if (m == mode) return;
        mode = m;
        click(m == CameraMode.NORMAL ? 1.1f : 1.25f);
    }

    /** Takes a photograph (Normal mode). Scan is reserved for the Codex and only explains itself. */
    void shutter() {
        if (!mode.available()) {
            say("Scan arrives with the Codex.");
            return;
        }
        if (PhotoCapture.pending()) return;
        float fovNow = CameraZoom.fov(Minecraft.getInstance().options.fov().get(), zoom.current());
        boolean queued = PhotoCapture.request(new PhotoCapture.Shot(mode.id(), zoom.current(), fovNow,
                this::onPreview, this::onSaved, () -> say("Couldn't save the photo.")));
        if (!queued) return;
        shotsTaken++;
        play(SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, 1.35f, 0.7f);
    }

    private void onPreview(PhotoCapture.Preview p) {
        if (active != this && suspended != this) {
            p.release();
            return;
        }
        if (shortcutPreview != null && shortcutPreview != p) shortcutPreview.release();
        flying = p;
        shortcutPreview = p;
        flyingSince = System.nanoTime();
    }

    private void onSaved(PhotoMetadata meta) {
        shotsSaved++;
    }

    void say(String text) {
        message = text;
        messageSince = System.nanoTime();
        play(SoundEvents.UI_BUTTON_CLICK.value(), 0.7f, 0.25f);
    }

    private static void click(float pitch) {
        play(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.18f);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }

    /** The shortcut can drop its preview once the Gallery's own thumbnail for that photo exists. */
    void releaseShortcutPreview() {
        if (shortcutPreview != null && shortcutPreview != flying) {
            shortcutPreview.release();
            shortcutPreview = null;
        }
    }

    // ── Diagnostics (development capture run) ─────────────────────────────────

    public CameraMode mode() {
        return mode;
    }

    public CameraZoom zoom() {
        return zoom;
    }

    public boolean cursorShown() {
        return cursor;
    }

    public boolean animating() {
        return flying != null;
    }

    public int shotsTaken() {
        return shotsTaken;
    }

    public int shotsSaved() {
        return shotsSaved;
    }

    public int originPage() {
        return originPage;
    }

    public String message() {
        return message;
    }

    public static boolean isSuspended() {
        return suspended != null;
    }
}
