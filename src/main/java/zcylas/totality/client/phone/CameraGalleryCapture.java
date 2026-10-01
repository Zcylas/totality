package zcylas.totality.client.phone;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.camera.CameraMode;
import zcylas.totality.client.camera.CameraSession;
import zcylas.totality.client.camera.CameraViewfinder;
import zcylas.totality.client.camera.CameraZoom;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.photo.Gallery;
import zcylas.totality.client.photo.PhotoCapture;
import zcylas.totality.client.photo.PhotoMetadata;
import zcylas.totality.client.photo.PhotoTextures;
import zcylas.totality.init.ModKeybinds;
import zcylas.totality.screen.phone.GalleryScreen;
import zcylas.totality.screen.phone.PhoneAppGridScreen;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneOrigin;
import zcylas.totality.screen.phone.PhotoViewerScreen;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Camera & Gallery V1 real-client verification (capture scene 65; development only, inert in normal play).
 *
 * <p>Every input goes through Minecraft's REAL entry points ({@code KeyboardHandler.keyPress},
 * {@code MouseHandler.onButton/onScroll/turnPlayer}, reached by reflection because GLFW cannot be driven headless), so
 * vanilla's own routing and the Camera's mixins are what is tested. {@code -Dtotality.hologram.capture.camera=}
 * <ul>
 *   <li>{@code fresh} (default): the full scene — photography, zoom, shutter frames, input safety, movement, cursor,
 *       navigation, Gallery, viewer, rename/favourite/delete, GUI scales, development capture — then writes
 *       {@code camera-capture-manifest.json};</li>
 *   <li>{@code persist}: a later launch of the same game directory, world and player: the Gallery must match the
 *       manifest exactly;</li>
 *   <li>{@code isolated}: another player (different {@code --username}) or another world
 *       ({@code -Dtotality.hologram.capture.world}): the Gallery must be empty and the first one untouched.</li>
 * </ul>
 */
final class CameraGalleryCapture {

    static final String MODE = System.getProperty(HologramCapture.PROPERTY + ".camera", "fresh");
    static final String MANIFEST = "camera-capture-manifest.json";

    /** Stage origin (block position the player stood on when the stage was built). */
    private static BlockPos stage = BlockPos.ZERO;

    private CameraGalleryCapture() {}

    // ── Real input ────────────────────────────────────────────────────────────

    private static Object invoke(Object target, String name, Class<?>[] types, Object... args) {
        try {
            Method m = target.getClass().getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            HologramCapture.log("FAIL: cannot call " + name + ": " + e);
            return null;
        }
    }

    private static void setField(Object target, Class<?> owner, String name, double value) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(target, value);
        } catch (ReflectiveOperationException e) {
            HologramCapture.log("FAIL: cannot set " + name + ": " + e);
        }
    }

    private static long window() {
        return Minecraft.getInstance().getWindow().handle();
    }

    /** A key press (action 1) or release (0) through {@code KeyboardHandler.keyPress}. */
    static void keyEvent(int key, int action) {
        invoke(Minecraft.getInstance().keyboardHandler, "keyPress", new Class<?>[] {long.class, int.class, KeyEvent.class},
                window(), action, new KeyEvent(key, 0, 0));
    }

    static Step key(String label, int key) {
        return run("key " + label, () -> {
            keyEvent(key, 1);
            keyEvent(key, 0);
        });
    }

    static void button(int button, boolean press) {
        invoke(Minecraft.getInstance().mouseHandler, "onButton", new Class<?>[] {long.class, MouseButtonInfo.class, int.class},
                window(), new MouseButtonInfo(button, 0), press ? 1 : 0);
    }

    static Step buttonStep(String label, int button, boolean press) {
        return run((press ? "press " : "release ") + label, () -> button(button, press));
    }

    static Step scroll(double notches) {
        return run("wheel " + notches, () -> invoke(Minecraft.getInstance().mouseHandler, "onScroll",
                new Class<?>[] {long.class, double.class, double.class}, window(), 0.0, notches));
    }

    /** Puts the pointer at GUI coordinates (what a real cursor position would be). */
    static void pointer(double[] gui) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        setField(mc.mouseHandler, MouseHandler.class, "xpos", gui[0] * scale);
        setField(mc.mouseHandler, MouseHandler.class, "ypos", gui[1] * scale);
    }

    /** Moves the pointer to {@code at} and clicks there (press, then release one tick later). */
    static void clickAt(List<Step> s, String label, Supplier<double[]> at) {
        s.add(run("pointer to " + label, () -> pointer(at.get())));
        s.add(buttonStep(label, GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
        s.add(buttonStep(label, GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
    }

    /** Mouse-look: {@code dx} raw pointer counts through {@code MouseHandler.turnPlayer} (the real sensitivity path). */
    static void look(double dx) {
        Minecraft mc = Minecraft.getInstance();
        setField(mc.mouseHandler, MouseHandler.class, "accumulatedDX", dx);
        setField(mc.mouseHandler, MouseHandler.class, "accumulatedDY", 0);
        invoke(mc.mouseHandler, "turnPlayer", new Class<?>[] {double.class}, 0.05);
        setField(mc.mouseHandler, MouseHandler.class, "accumulatedDX", 0);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** A check that logs FAIL (instead of crashing the client) when its lookup throws. */
    private static Step check(String label, BooleanSupplier condition) {
        return HologramCapture.check(label, () -> {
            try {
                return condition.getAsBoolean();
            } catch (RuntimeException e) {
                HologramCapture.log("error in check '" + label + "': " + e);
                return false;
            }
        });
    }

    /** An action that logs FAIL (instead of crashing the client) when it throws. */
    private static Step run(String label, Runnable action) {
        return HologramCapture.run(label, () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                HologramCapture.log("FAIL: '" + label + "' threw " + e);
            }
        });
    }

    private static Step shot(String name) {
        return HologramCapture.screenshot("cam_" + name);
    }

    private static void frames(List<Step> s, String name, int count) {
        for (int i = 1; i <= count; i++) s.add(shot(name + "_t" + String.format("%02d", i)));
    }

    private static Step stageCommand(Supplier<String> command) {
        return mc -> HologramCapture.command(command.get()).tick(mc);
    }

    private static String at(int dx, int dy, int dz) {
        return (stage.getX() + dx) + " " + (stage.getY() + dy) + " " + (stage.getZ() + dz);
    }

    private static Gallery gallery() {
        return Gallery.current().orElse(null);
    }

    private static int photoCount() {
        Gallery g = gallery();
        return g == null ? -1 : g.photos().size();
    }

    private static PhotoMetadata latest() {
        Gallery g = gallery();
        return g == null ? null : g.latest();
    }

    private static <T> T serverValue(Supplier<T> query) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        return server == null ? null : server.submit(query::get).join();
    }

    private static boolean serverBlockIs(BlockPos pos, boolean stone) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        Boolean r = serverValue(() -> server.overworld().getBlockState(pos).is(stone ? Blocks.STONE : Blocks.AIR));
        return Boolean.TRUE.equals(r);
    }

    private static float fov() {
        return Minecraft.getInstance().gameRenderer.mainCamera().getFov();
    }

    private static float baseFov() {
        return Minecraft.getInstance().options.fov().get();
    }

    private static boolean near(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }

    private static CameraSession camera() {
        return CameraSession.active();
    }

    private static <T> T screenAs(Class<T> type) {
        Object s = Minecraft.getInstance().gui.screen();
        return type.isInstance(s) ? type.cast(s) : null;
    }

    private static CameraViewfinder.Layout viewfinder() {
        Minecraft mc = Minecraft.getInstance();
        return CameraViewfinder.layout(mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    private static double[] chip(int i) {
        CameraViewfinder.Layout l = viewfinder();
        int n = CameraZoom.BASIC.presets().size();
        return new double[] {l.chipX(i, n) + l.chipW() / 2.0, l.chipY() + l.chipH() / 2.0};
    }

    /** Reads a saved photograph (main thread; development only). */
    private static NativeImage readPhoto(PhotoMetadata m) {
        Gallery g = gallery();
        if (g == null || m == null) return null;
        try (InputStream in = Files.newInputStream(g.store().imagePath(m))) {
            return NativeImage.read(in);
        } catch (IOException e) {
            HologramCapture.log("FAIL: cannot read photo " + m.id() + ": " + e);
            return null;
        }
    }

    /**
     * Fraction of pure-white pixels on the shutter ring's circle in a photo: the viewfinder draws it pure white, so a
     * clean photograph has (almost) none there while a full-screen capture is full of them.
     */
    private static double shutterRingWhiteness(NativeImage image) {
        Minecraft mc = Minecraft.getInstance();
        CameraViewfinder.Layout l = viewfinder();
        double scale = image.getWidth() / (double) mc.getWindow().getGuiScaledWidth();
        double cx = l.shutterX() * scale, cy = l.shutterY() * scale, r = (l.shutterR() - l.u()) * scale;
        int white = 0, n = 0;
        for (int a = 0; a < 360; a += 5) {
            int x = (int) Math.round(cx + r * Math.cos(Math.toRadians(a))), y = (int) Math.round(cy + r * Math.sin(Math.toRadians(a)));
            if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) continue;
            n++;
            if ((image.getPixel(x, y) & 0xFFFFFF) == 0xFFFFFF) white++;
        }
        return n == 0 ? 0 : white / (double) n;
    }

    /** Waits until {@code done} (checked each tick), logging a TIMEOUT after {@code ticks}. */
    private static Step waitFor(String label, BooleanSupplier done, int ticks) {
        return HologramCapture.until(label, done, () -> { }, ticks);
    }

    // ── Scenes ────────────────────────────────────────────────────────────────

    static List<Step> scenes() {
        return switch (MODE) {
            case "persist" -> persistScenes();
            case "isolated" -> isolatedScenes();
            default -> freshScenes();
        };
    }

    private static List<Step> setup() {
        List<Step> s = new ArrayList<>();
        s.add(run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(run("first person, hand empty", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        return s;
    }

    private static List<Step> freshScenes() {
        List<Step> s = setup();
        s.add(run("note gallery location", () -> {
            Gallery g = gallery();
            HologramCapture.log("gallery dir: " + (g == null ? "none" : g.store().directory()));
            HologramCapture.log("window active: " + Minecraft.getInstance().isWindowActive());
        }));
        s.add(waitFor("gallery loaded", () -> gallery() != null && gallery().ready(), 100));
        s.add(check("a fresh run starts with an empty Gallery", () -> photoCount() == 0));
        s.addAll(stageScene());
        s.addAll(homeAndOpen());
        s.addAll(zoomScene());
        s.addAll(shutterScene());
        s.addAll(movementScene());
        s.addAll(cursorScene());
        s.addAll(navigationScene());
        s.addAll(galleryScene());
        s.addAll(tabScene());
        s.addAll(reducedMotionScene());
        s.addAll(guiScaleScene());
        s.addAll(fullScreenScene());
        s.add(run("write manifest", CameraGalleryCapture::writeManifest));
        s.add(run("screens back to normal", () -> HologramCapture.allowScreens = false));
        return s;
    }

    /** A flat pad with three coloured pillars at 6, 14 and 24 blocks, two still animals, and a walking villager. */
    private static List<Step> stageScene() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("tp @s ~ ~25 ~"));
        s.add(HologramCapture.waitTicks(10));
        s.add(run("stage origin", () -> stage = Minecraft.getInstance().player.blockPosition()));
        s.add(stageCommand(() -> "fill " + at(-15, -1, -32) + " " + at(15, -1, 2) + " minecraft:grass_block"));
        s.add(stageCommand(() -> "fill " + at(-15, 0, -32) + " " + at(15, 15, 2) + " minecraft:air"));
        s.add(stageCommand(() -> "fill " + at(-15, -2, -32) + " " + at(15, -2, 2) + " minecraft:dirt"));
        s.add(stageCommand(() -> "fillbiome " + at(-15, -2, -32) + " " + at(15, 15, 2) + " minecraft:plains"));
        s.add(stageCommand(() -> "fill " + at(-3, 0, -8) + " " + at(-3, 3, -8) + " minecraft:red_wool"));
        s.add(stageCommand(() -> "fill " + at(4, 0, -16) + " " + at(4, 5, -16) + " minecraft:yellow_wool"));
        s.add(stageCommand(() -> "fill " + at(-6, 0, -27) + " " + at(-6, 7, -27) + " minecraft:light_blue_wool"));
        s.add(stageCommand(() -> "summon minecraft:sheep " + at(2, 0, -11) + " {NoAI:1b,Color:6b,Rotation:[150f,0f]}"));
        s.add(stageCommand(() -> "summon minecraft:cow " + at(-1, 0, -19) + " {NoAI:1b,Rotation:[200f,0f]}"));
        s.add(stageCommand(() -> "summon minecraft:villager " + at(6, 0, -12) + " {Tags:[\"cam_walker\"]}"));
        s.add(stageCommand(() -> "tp @s " + at(0, 0, 0) + " 180 0"));
        s.add(HologramCapture.command("item replace entity @s weapon.mainhand with minecraft:stone 64"));
        s.add(HologramCapture.waitTicks(30));
        return s;
    }

    private static List<Step> homeAndOpen() {
        List<Step> s = new ArrayList<>();
        s.add(run("open the Phone home screen", () -> Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(PhoneFrame.COPPER))));
        s.add(HologramCapture.waitTicks(15));
        s.add(shot("00_home_main"));
        s.add(check("home: main page plus the secondary page; opens on the main page", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && g.pageCount() == 2 && g.page() == 0 && g.mainPage() == 0;
        }));
        s.add(check("home: the four favourites are unchanged", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && "Character".equals(g.dockLabel(0)) && "Skills".equals(g.dockLabel(1))
                    && "Quests".equals(g.dockLabel(2)) && "Camera".equals(g.dockLabel(3));
        }));
        s.add(check("home: main page still starts Codex, Map, Inventory and ends Settings", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && "Codex".equals(g.appLabel(0)) && "Map".equals(g.appLabel(1)) && "Inventory".equals(g.appLabel(2))
                    && "Settings".equals(g.appLabel(11)) && g.appLabel(12) == null;
        }));
        s.add(PhoneCaptureStates.clickAt("Camera (dock)", () -> PhoneCaptureStates.dock(3)));
        s.add(check("the Camera dock icon opens the full-screen viewfinder (no screen: play continues)",
                () -> CameraSession.isActive() && Minecraft.getInstance().gui.screen() == null));
        s.add(check("the Camera remembers the home page it came from (main)", () -> camera() != null && camera().originPage() == 0));
        frames(s, "01_open", 4);
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("02_viewfinder_1x"));
        s.add(check("1x: the view uses the player's own FOV setting", () -> near(fov(), baseFov(), 0.6)));
        s.add(check("viewfinder hides the HUD (held item, outline, hotbar)", () -> Minecraft.getInstance().gui.hud.isHidden()));
        return s;
    }

    private static List<Step> zoomScene() {
        List<Step> s = new ArrayList<>();
        float[] seen = {0};
        s.add(key("Up (next zoom preset)", GLFW.GLFW_KEY_UP));
        s.add(check("Up selects the 2x preset", () -> camera() != null && camera().zoom().target() == 2f));
        // One frame per tick while the transition runs.
        for (int i = 1; i <= 5; i++) {
            int n = i;
            s.add(mc -> {
                HologramCapture.screenshot("cam_03_zoom_to_2x_t" + n).tick(mc);
                if (n == 2) seen[0] = camera().zoom().current();
                return true;
            });
        }
        s.add(check("the preset change is a smooth transition (an in-between value was rendered)", () -> seen[0] > 1.05f && seen[0] < 1.98f));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("04_zoom_2x"));
        s.add(check("2x: the projection narrows to 2*atan(tan(fov/2)/2)", () -> near(fov(), CameraZoom.fov(baseFov(), 2f), 0.6)));
        s.add(key("Down", GLFW.GLFW_KEY_DOWN));
        s.add(key("Down", GLFW.GLFW_KEY_DOWN));
        frames(s, "05_zoom_to_05x", 5);
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("06_zoom_05x"));
        s.add(check("0.5x: a genuinely wider projection (about 109 degrees from 70)", () -> near(fov(), CameraZoom.fov(baseFov(), 0.5f), 0.6)
                && fov() > baseFov() + 25));
        for (int i = 0; i < 3; i++) s.add(scroll(1));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("three wheel notches: 0.5 x 2^(3/6) = 0.71x", () -> near(camera().zoom().target(), 0.7071, 0.01)));
        s.add(shot("07_wheel_07x"));
        for (int i = 0; i < 6; i++) s.add(scroll(1));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("six more notches: 1.41x, and the indicator reads 1.4x",
                () -> near(camera().zoom().current(), 1.4142, 0.01) && CameraZoom.label(camera().zoom().current()).equals("1.4×")));
        s.add(shot("08_wheel_14x"));
        s.add(key("Down (back to 1x)", GLFW.GLFW_KEY_DOWN));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("Down from 1.4x returns to the 1x preset", () -> camera().zoom().target() == 1f));
        s.add(check("the hotbar slot did not change while scrolling", () -> Minecraft.getInstance().player.getInventory().getSelectedSlot() == 0));
        return s;
    }

    private static List<Step> shutterScene() {
        List<Step> s = new ArrayList<>();
        BlockPos[] target = new BlockPos[1];
        s.add(run("target block 3 ahead at eye level", () -> target[0] = stage.offset(0, 1, -3)));
        s.add(stageCommand(() -> "setblock " + at(0, 1, -3) + " minecraft:stone"));
        s.add(HologramCapture.look(180, 0));
        s.add(HologramCapture.waitTicks(4));
        s.add(check("the crosshair is on the target block", () -> Minecraft.getInstance().hitResult != null
                && Minecraft.getInstance().hitResult.getType() == HitResult.Type.BLOCK));
        s.add(shot("09_before_shutter"));
        // One left click: the shutter. Frames every tick through the whole animation.
        s.add(mc -> {
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, true);
            HologramCapture.log("press left (shutter)");
            return true;
        });
        s.add(mc -> {
            HologramCapture.screenshot("cam_10_shutter_t01").tick(mc);
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
            return true;
        });
        frames(s, "10_shutter_cont", 14);
        s.add(waitFor("the photo is saved", () -> photoCount() == 1, 60));
        s.add(shot("11_after_shutter_thumbnail"));
        s.add(check("a left click takes exactly one photograph", () -> photoCount() == 1 && camera().shotsTaken() == 1));
        s.add(check("the target block was NOT broken (creative breaks instantly on attack)", () -> serverBlockIs(target[0], true)));
        s.add(check("the player did not swing", () -> !Minecraft.getInstance().player.swinging));
        s.add(check("the photo is a PNG at the full window resolution", () -> {
            PhotoMetadata m = latest();
            RenderTarget rt = Minecraft.getInstance().gameRenderer.mainRenderTarget();
            NativeImage img = readPhoto(m);
            if (img == null) return false;
            try (img) {
                HologramCapture.log("photo " + m.id() + " " + img.getWidth() + "x" + img.getHeight() + " (render target "
                        + rt.width + "x" + rt.height + ", window " + Minecraft.getInstance().getWindow().getWidth() + "x"
                        + Minecraft.getInstance().getWindow().getHeight() + ")");
                return img.getWidth() == rt.width && img.getHeight() == rt.height && m.width() == rt.width && m.height() == rt.height
                        && m.fileName().endsWith(".png");
            }
        }));
        s.add(check("the photo is clean: the white shutter ring is not in it", () -> {
            NativeImage img = readPhoto(latest());
            if (img == null) return false;
            try (img) {
                double w = shutterRingWhiteness(img);
                HologramCapture.log("shutter-ring white fraction in the photo: " + w);
                return w < 0.2;
            }
        }));
        s.add(check("metadata: Normal mode, 1x, clean capture, overworld", () -> {
            PhotoMetadata m = latest();
            return m != null && m.mode().equals("normal") && m.zoom() == 1f && m.capture().equals("clean")
                    && m.dimension().equals("minecraft:overworld") && m.name().equals("Photo 1");
        }));
        s.add(check("the thumbnail was made from the capture (cached texture, no extra decode)", () -> PhotoTextures.cachedThumbnails() >= 1));

        // Holding the button: still one photo, still no mining.
        s.add(buttonStep("left (hold)", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
        s.add(HologramCapture.waitTicks(30));
        s.add(buttonStep("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
        s.add(waitFor("second photo saved", () -> photoCount() == 2, 60));
        s.add(check("holding left takes one photo and breaks nothing", () -> photoCount() == 2 && serverBlockIs(target[0], true)
                && Minecraft.getInstance().gameMode != null && !Minecraft.getInstance().gameMode.isDestroying()));
        // Right click with a block in hand: nothing is placed.
        s.add(buttonStep("right", GLFW.GLFW_MOUSE_BUTTON_RIGHT, true));
        s.add(HologramCapture.waitTicks(6));
        s.add(buttonStep("right", GLFW.GLFW_MOUSE_BUTTON_RIGHT, false));
        s.add(HologramCapture.waitTicks(4));
        s.add(check("right click with stone in hand places nothing", () -> serverBlockIs(stage.offset(0, 1, -2), false)
                && photoCount() == 2));
        s.add(check("middle/pick and use never reached gameplay (no item use in progress)", () -> !Minecraft.getInstance().player.isUsingItem()));

        // Consecutive photographs without leaving the Camera.
        for (int i = 0; i < 6; i++) {
            s.add(buttonStep("left (burst " + (i + 1) + ")", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
            s.add(buttonStep("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
            s.add(HologramCapture.waitTicks(2));
        }
        s.add(waitFor("burst saved", () -> photoCount() == 8, 100));
        s.add(check("six consecutive shots: eight photos, distinct ids, names Photo 1..8", () -> {
            Gallery g = gallery();
            if (g == null || g.photos().size() != 8) return false;
            Set<String> ids = new HashSet<>(), names = new HashSet<>();
            for (PhotoMetadata m : g.photos()) {
                ids.add(m.id());
                names.add(m.name());
            }
            Set<String> expected = new HashSet<>();
            for (int i = 1; i <= 8; i++) expected.add("Photo " + i);
            return ids.size() == 8 && names.equals(expected);
        }));
        s.add(check("still in the Camera after every shot", CameraSession::isActive));

        // An entity in the crosshair: clicks never attack it.
        s.add(stageCommand(() -> "setblock " + at(0, 1, -3) + " minecraft:air"));
        s.add(stageCommand(() -> "summon minecraft:pig " + at(0, 0, -2) + " {NoAI:1b,Tags:[\"cam_pig\"],Rotation:[90f,0f]}"));
        s.add(HologramCapture.look(180, 32));
        s.add(HologramCapture.waitTicks(6));
        s.add(check("the crosshair is on the pig", () -> Minecraft.getInstance().crosshairPickEntity instanceof Pig));
        for (int i = 0; i < 3; i++) {
            s.add(buttonStep("left at the pig", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
            s.add(buttonStep("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
            s.add(HologramCapture.waitTicks(3));
        }
        s.add(waitFor("pig photos saved", () -> photoCount() == 11, 100));
        s.add(shot("12_pig_unharmed"));
        s.add(check("the pig was never attacked (full health, no hurt time)", () -> Boolean.TRUE.equals(serverValue(() -> {
            IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
            for (Entity e : server.overworld().getAllEntities()) {
                if (e.entityTags().contains("cam_pig") && e instanceof LivingEntity pig) {
                    return pig.getHealth() == pig.getMaxHealth() && pig.hurtTime == 0 && pig.getLastHurtByMob() == null;
                }
            }
            return false;
        }))));
        s.add(HologramCapture.command("kill @e[tag=cam_pig]"));
        s.add(HologramCapture.look(180, 0));
        s.add(HologramCapture.command("item replace entity @s weapon.mainhand with minecraft:air"));
        return s;
    }

    private static List<Step> movementScene() {
        List<Step> s = new ArrayList<>();
        double[] start = new double[3];
        Minecraft mcRef = Minecraft.getInstance();
        s.add(run("remember position", () -> {
            var p = Minecraft.getInstance().player.position();
            start[0] = p.x;
            start[1] = p.y;
            start[2] = p.z;
        }));
        // Walk and sprint forward for a second, photographing on the way.
        for (int i = 0; i < 20; i++) {
            int n = i;
            s.add(mc -> {
                mc.options.keyUp.setDown(true);
                mc.options.keySprint.setDown(true);
                if (n == 10) button(GLFW.GLFW_MOUSE_BUTTON_LEFT, true);
                if (n == 11) button(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
                if (n == 12) HologramCapture.screenshot("cam_20_walking_photo").tick(mc);
                return true;
            });
        }
        s.add(run("stop", () -> {
            mcRef.options.keyUp.setDown(false);
            mcRef.options.keySprint.setDown(false);
        }));
        s.add(check("the player walks and sprints with the Camera open", () -> {
            var p = Minecraft.getInstance().player.position();
            double d = Math.hypot(p.x - start[0], p.z - start[2]);
            HologramCapture.log("moved " + d + " blocks");
            return d > 3 && CameraSession.isActive();
        }));
        s.add(waitFor("walking photo saved", () -> photoCount() == 12, 60));
        // Fly up and photograph while flying.
        // Start flying the way a player does: double-tap jump (creative), then hold jump to rise.
        s.add(run("remember height", () -> start[1] = Minecraft.getInstance().player.getY()));
        int[] tap = {1, 0, 1};
        for (int t : tap) s.add(mc -> {
            mc.options.keyJump.setDown(t == 1);
            return true;
        });
        for (int i = 0; i < 20; i++) s.add(mc -> {
            mc.options.keyJump.setDown(true);
            return true;
        });
        s.add(run("stop rising", () -> mcRef.options.keyJump.setDown(false)));
        s.add(buttonStep("left (flying)", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
        s.add(buttonStep("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("21_flying_photo"));
        s.add(check("flying works with the Camera open, and the shutter works in flight", () -> {
            HologramCapture.log("rose " + (Minecraft.getInstance().player.getY() - start[1]) + " blocks");
            return Minecraft.getInstance().player.getY() - start[1] > 2 && Minecraft.getInstance().player.getAbilities().flying;
        }));
        s.add(waitFor("flying photo saved", () -> photoCount() == 13, 60));
        s.add(check("the flying photo is above the ground photos", () -> {
            Gallery g = gallery();
            return g != null && g.photos().get(0).y() > g.photos().get(2).y() + 2;
        }));
        s.add(run("land", () -> Minecraft.getInstance().player.getAbilities().flying = false));
        s.add(stageCommand(() -> "tp @s " + at(0, 0, 0) + " 180 0"));
        s.add(HologramCapture.waitTicks(10));

        // Mouse-look through the real sensitivity path: half as fast at 2x.
        float[] turn = new float[2];
        s.add(run("look at 1x", () -> {
            float before = Minecraft.getInstance().player.getYRot();
            look(200);
            turn[0] = Minecraft.getInstance().player.getYRot() - before;
        }));
        s.add(key("Up (2x)", GLFW.GLFW_KEY_UP));
        s.add(HologramCapture.waitTicks(10));
        s.add(run("look at 2x", () -> {
            float before = Minecraft.getInstance().player.getYRot();
            look(200);
            turn[1] = Minecraft.getInstance().player.getYRot() - before;
        }));
        s.add(check("aiming works, and at 2x the same mouse motion turns half as far", () -> {
            HologramCapture.log("yaw change 1x " + turn[0] + ", 2x " + turn[1]);
            return Math.abs(turn[0]) > 1 && near(turn[1] / turn[0], 0.5, 0.05);
        }));
        s.add(HologramCapture.look(180, 0));
        s.add(key("Down (1x)", GLFW.GLFW_KEY_DOWN));
        s.add(HologramCapture.waitTicks(8));
        return s;
    }

    private static List<Step> cursorScene() {
        List<Step> s = new ArrayList<>();
        float[] yaw = new float[1];
        int[] count = new int[1];
        s.add(run("hold Alt", () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, true)));
        s.add(HologramCapture.waitTicks(2));
        s.add(check("holding Alt shows the cursor (the mouse is released)", () -> camera().cursorShown()
                && !Minecraft.getInstance().mouseHandler.isMouseGrabbed()));
        s.add(run("pointer over the 2x chip", () -> pointer(chip(2))));
        s.add(HologramCapture.waitTicks(2));
        s.add(shot("30_alt_hover_2x"));
        clickAt(s, "2x chip", () -> chip(2));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("clicking the 2x chip selects 2x", () -> camera().zoom().target() == 2f));
        clickAt(s, "0.5x chip", () -> chip(0));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("clicking the 0.5x chip selects 0.5x", () -> camera().zoom().target() == 0.5f));
        clickAt(s, "1x chip", () -> chip(1));
        s.add(run("count photos", () -> count[0] = photoCount()));
        clickAt(s, "SCAN label", () -> {
            CameraViewfinder.Layout l = viewfinder();
            return new double[] {l.modeX(1, camera().mode().ordinal()), l.modeY() + 4 * l.u()};
        });
        s.add(HologramCapture.waitTicks(8));
        s.add(check("clicking SCAN selects Scan mode", () -> camera().mode() == CameraMode.SCAN));
        s.add(shot("31_scan_mode"));
        clickAt(s, "shutter in Scan", () -> new double[] {viewfinder().shutterX(), viewfinder().shutterY()});
        s.add(HologramCapture.waitTicks(4));
        s.add(shot("32_scan_unavailable_message"));
        s.add(check("Scan takes no photo and says it comes with the Codex", () -> photoCount() == count[0]
                && camera().message() != null && camera().message().contains("Codex")));
        // Swipe right across the view: back to Normal.
        s.add(run("press in the view", () -> {
            CameraViewfinder.Layout l = viewfinder();
            pointer(new double[] {l.w() * 0.4, l.h() * 0.4});
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, true);
        }));
        s.add(run("drag right and release", () -> {
            CameraViewfinder.Layout l = viewfinder();
            pointer(new double[] {l.w() * 0.4 + 40 * l.u(), l.h() * 0.41});
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        }));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("swiping right returns to Normal", () -> camera().mode() == CameraMode.NORMAL));
        s.add(run("press in the view", () -> {
            CameraViewfinder.Layout l = viewfinder();
            pointer(new double[] {l.w() * 0.6, l.h() * 0.4});
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, true);
        }));
        s.add(run("drag left and release", () -> {
            CameraViewfinder.Layout l = viewfinder();
            pointer(new double[] {l.w() * 0.6 - 40 * l.u(), l.h() * 0.4});
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
        }));
        s.add(HologramCapture.waitTicks(3));
        s.add(check("swiping left goes to Scan", () -> camera().mode() == CameraMode.SCAN));
        s.add(key("Right arrow", GLFW.GLFW_KEY_RIGHT));
        s.add(check("Right arrow (swipe right) returns to Normal", () -> camera().mode() == CameraMode.NORMAL));
        s.add(HologramCapture.waitTicks(6));
        s.add(run("remember yaw", () -> yaw[0] = Minecraft.getInstance().player.getYRot()));
        s.add(run("release Alt", () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, false)));
        s.add(HologramCapture.waitTicks(3));
        s.add(check("releasing Alt hides the cursor without turning the view or taking a photo", () -> {
            HologramCapture.log("mouse grabbed after Alt: " + Minecraft.getInstance().mouseHandler.isMouseGrabbed()
                    + " (window active: " + Minecraft.getInstance().isWindowActive() + ")");
            return !camera().cursorShown() && Minecraft.getInstance().player.getYRot() == yaw[0] && photoCount() == count[0];
        }));
        s.add(check("with the window focused, aiming resumes (mouse grabbed again)", () -> !Minecraft.getInstance().isWindowActive()
                || Minecraft.getInstance().mouseHandler.isMouseGrabbed()));
        return s;
    }

    private static List<Step> navigationScene() {
        List<Step> s = new ArrayList<>();
        s.add(key("Up (2x before visiting the Gallery)", GLFW.GLFW_KEY_UP));
        s.add(HologramCapture.waitTicks(8));
        s.add(run("hold Alt", () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, true)));
        s.add(HologramCapture.waitTicks(2));
        s.add(run("pointer over the Gallery shortcut", () -> {
            CameraViewfinder.Layout l = viewfinder();
            pointer(new double[] {l.thumbX() + l.thumbS() / 2.0, l.thumbY() + l.thumbS() / 2.0});
        }));
        s.add(HologramCapture.waitTicks(2));
        s.add(shot("33_shortcut_hover"));
        clickAt(s, "Gallery shortcut", () -> {
            CameraViewfinder.Layout l = viewfinder();
            return new double[] {l.thumbX() + l.thumbS() / 2.0, l.thumbY() + l.thumbS() / 2.0};
        });
        s.add(run("release Alt", () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, false)));
        s.add(check("the shortcut opens the Gallery, with the Camera waiting behind it", () -> {
            GalleryScreen g = screenAs(GalleryScreen.class);
            return g != null && g.origin().fromCamera() && CameraSession.isSuspended() && !CameraSession.isActive();
        }));
        s.add(HologramCapture.waitTicks(15));
        s.add(shot("40_gallery_from_camera"));
        s.add(check("the Gallery from the Camera shows the same photos", () -> {
            GalleryScreen g = screenAs(GalleryScreen.class);
            return g != null && g.shownCount() == photoCount();
        }));
        s.add(check("the normal FOV is back while the Gallery is up", () -> near(fov(), baseFov(), 0.6)));
        s.add(key("Esc", GLFW.GLFW_KEY_ESCAPE));
        s.add(HologramCapture.waitTicks(10));
        s.add(check("ESC in that Gallery returns to the Camera, zoom kept (2x)", () -> CameraSession.isActive()
                && Minecraft.getInstance().gui.screen() == null && camera().zoom().target() == 2f));
        s.add(shot("41_back_in_camera"));
        s.add(key("Esc", GLFW.GLFW_KEY_ESCAPE));
        s.add(check("ESC in the Camera returns to the home page it came from", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && g.page() == 0 && !CameraSession.isActive();
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(check("closing the Camera restores the normal FOV (zoom was 2x)", () -> near(fov(), baseFov(), 0.6)));
        s.add(check("and first person (the perspective before the Camera)", () -> Minecraft.getInstance().options.getCameraType() == CameraType.FIRST_PERSON));
        s.add(check("the HUD is back", () -> !Minecraft.getInstance().gui.hud.isHidden()));
        s.add(shot("42_home_after_camera"));
        return s;
    }

    private static List<Step> galleryScene() {
        List<Step> s = new ArrayList<>();
        String[] ids = new String[3];
        s.add(run("secondary page", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            if (g != null) g.showPage(1);
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(shot("50_home_secondary_page"));
        s.add(check("the secondary page holds the Gallery app", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && "Gallery".equals(g.appLabel(0));
        }));
        s.add(PhoneCaptureStates.clickAt("Gallery app", () -> PhoneCaptureStates.grid() != null ? PhoneCaptureStates.grid().tileCentre(0) : new double[2]));
        s.add(check("the Gallery app opens the same Gallery, returning to page 1", () -> {
            GalleryScreen g = screenAs(GalleryScreen.class);
            return g != null && !g.origin().fromCamera() && g.origin().page() == 1 && g.shownCount() == photoCount();
        }));
        s.add(HologramCapture.waitTicks(20));
        s.add(shot("51_gallery_grid"));
        s.add(check("the grid holds thumbnails only (bounded cache, no full-size textures)", () -> {
            HologramCapture.log("thumbnails cached: " + PhotoTextures.cachedThumbnails() + ", full texture: " + PhotoTextures.hasFullTexture());
            return PhotoTextures.cachedThumbnails() >= 9 && PhotoTextures.cachedThumbnails() <= PhotoTextures.MAX_THUMBNAILS
                    && !PhotoTextures.hasFullTexture();
        }));
        s.add(scroll(-2));
        s.add(HologramCapture.waitTicks(6));
        s.add(check("the wheel scrolls the grid", () -> {
            GalleryScreen g = screenAs(GalleryScreen.class);
            return g != null && g.scroll() > 0;
        }));
        s.add(shot("52_gallery_scrolled"));
        s.add(scroll(4));
        s.add(HologramCapture.waitTicks(3));
        // Open the newest photo through the real mouse path.
        clickAt(s, "newest photo", () -> screenAs(GalleryScreen.class) != null ? screenAs(GalleryScreen.class).cellCentre(0) : new double[2]);
        s.add(check("selecting a photo opens the full-screen viewer", () -> screenAs(PhotoViewerScreen.class) != null));
        s.add(run("remember ids", () -> {
            ids[0] = screenAs(PhotoViewerScreen.class).currentId();
            ids[1] = gallery().photos().get(1).id();
        }));
        s.add(waitFor("full photo loaded", PhotoTextures::hasFullTexture, 60));
        s.add(HologramCapture.waitTicks(2));
        s.add(shot("53_viewer"));
        s.add(check("the viewer keeps the photo's aspect ratio", () -> {
            PhotoViewerScreen v = screenAs(PhotoViewerScreen.class);
            PhotoMetadata m = gallery().find(v.currentId());
            int[] r = v.photoRect();
            double expected = m.width() / (double) m.height(), shown = r[2] / (double) r[3];
            HologramCapture.log("viewer rect " + r[2] + "x" + r[3] + " for " + m.width() + "x" + m.height());
            return Math.abs(shown - expected) < 2.0 / Math.min(r[2], r[3]) + 0.01;
        }));
        s.add(key("Right", GLFW.GLFW_KEY_RIGHT));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("Right shows the next (older) photo", () -> ids[1].equals(screenAs(PhotoViewerScreen.class).currentId())));
        s.add(shot("54_viewer_next"));
        clickAt(s, "previous arrow", () -> screenAs(PhotoViewerScreen.class).controlCentre("prev"));
        s.add(check("the previous arrow goes back", () -> ids[0].equals(screenAs(PhotoViewerScreen.class).currentId())));

        // Favourite.
        clickAt(s, "favourite", () -> screenAs(PhotoViewerScreen.class).controlCentre("favorite"));
        s.add(HologramCapture.waitTicks(6));
        s.add(check("favourite toggles on and is saved", () -> gallery().find(ids[0]).favorite()
                && sidecar(ids[0]).contains("\"favorite\": true")));
        s.add(shot("55_viewer_favourite"));

        // Rename by double-clicking the name.
        s.add(run("pointer to name", () -> pointer(screenAs(PhotoViewerScreen.class).controlCentre("name"))));
        s.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
        s.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
        s.add(check("a single click does not start editing", () -> !screenAs(PhotoViewerScreen.class).editing()));
        s.add(buttonStep("name (second click)", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
        s.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
        s.add(check("double-clicking the name edits it", () -> screenAs(PhotoViewerScreen.class).editing()));
        s.add(run("type a name", () -> screenAs(PhotoViewerScreen.class).typeName("Sunrise Peaks")));
        s.add(HologramCapture.waitTicks(2));
        s.add(shot("56_rename_editing"));
        s.add(key("Enter", GLFW.GLFW_KEY_ENTER));
        s.add(HologramCapture.waitTicks(6));
        s.add(check("Enter saves the display name; id and files unchanged", () -> {
            PhotoMetadata m = gallery().find(ids[0]);
            return m != null && m.name().equals("Sunrise Peaks") && !screenAs(PhotoViewerScreen.class).editing()
                    && Files.exists(gallery().store().imagePath(m)) && sidecar(ids[0]).contains("Sunrise Peaks");
        }));
        s.add(shot("57_renamed"));
        s.add(run("pointer to name", () -> pointer(screenAs(PhotoViewerScreen.class).controlCentre("name"))));
        for (int i = 0; i < 2; i++) {
            s.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
            s.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
        }
        s.add(run("type", () -> screenAs(PhotoViewerScreen.class).typeName("Should Not Save")));
        s.add(key("Esc", GLFW.GLFW_KEY_ESCAPE));
        s.add(check("ESC cancels an edit (name kept, viewer still open)", () -> screenAs(PhotoViewerScreen.class) != null
                && !screenAs(PhotoViewerScreen.class).editing() && gallery().find(ids[0]).name().equals("Sunrise Peaks")));

        // Delete the next photo: confirmation first.
        s.add(key("Right", GLFW.GLFW_KEY_RIGHT));
        s.add(run("remember the photo to delete", () -> ids[2] = screenAs(PhotoViewerScreen.class).currentId()));
        clickAt(s, "Delete", () -> screenAs(PhotoViewerScreen.class).controlCentre("delete"));
        s.add(check("Delete asks for confirmation first", () -> screenAs(PhotoViewerScreen.class).confirming()
                && gallery().find(ids[2]) != null));
        s.add(HologramCapture.waitTicks(2));
        s.add(shot("58_delete_confirm"));
        clickAt(s, "Cancel", () -> screenAs(PhotoViewerScreen.class).controlCentre("cancel"));
        s.add(check("Cancel keeps it", () -> !screenAs(PhotoViewerScreen.class).confirming() && gallery().find(ids[2]) != null));
        int[] before = new int[1];
        s.add(run("count", () -> before[0] = photoCount()));
        clickAt(s, "Delete", () -> screenAs(PhotoViewerScreen.class).controlCentre("delete"));
        clickAt(s, "confirm Delete", () -> screenAs(PhotoViewerScreen.class).controlCentre("confirm"));
        s.add(HologramCapture.waitTicks(8));
        s.add(check("confirmed delete removes the photo and its files; the viewer moves on", () -> {
            Path dir = gallery().store().directory();
            boolean gone = !Files.exists(dir.resolve("photos").resolve(ids[2] + ".png"))
                    && !Files.exists(dir.resolve("photos").resolve(ids[2] + ".json"))
                    && !Files.exists(dir.resolve("thumbnails").resolve(ids[2] + ".png"));
            PhotoViewerScreen v = screenAs(PhotoViewerScreen.class);
            return gone && gallery().find(ids[2]) == null && photoCount() == before[0] - 1 && v != null && !ids[2].equals(v.currentId());
        }));
        s.add(shot("59_after_delete"));

        // Back to the grid; favourites.
        s.add(key("Esc", GLFW.GLFW_KEY_ESCAPE));
        s.add(check("ESC in the viewer returns to the Gallery", () -> screenAs(GalleryScreen.class) != null));
        s.add(HologramCapture.waitTicks(10));
        s.add(check("the deleted photo is gone from the grid", () -> screenAs(GalleryScreen.class).shownCount() == photoCount()));
        clickAt(s, "Favourites tab", () -> screenAs(GalleryScreen.class).tabCentre(true));
        s.add(HologramCapture.waitTicks(6));
        s.add(check("Favourites shows only the favourite", () -> screenAs(GalleryScreen.class).favoritesOnly()
                && screenAs(GalleryScreen.class).shownCount() == 1));
        s.add(shot("60_gallery_favourites"));
        clickAt(s, "All tab", () -> screenAs(GalleryScreen.class).tabCentre(false));
        s.add(HologramCapture.waitTicks(4));
        s.add(shot("61_gallery_all_named"));
        s.add(key("Esc", GLFW.GLFW_KEY_ESCAPE));
        s.add(check("ESC in the Gallery returns to the home page it was opened from (page 1)", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && g.page() == 1;
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(shot("62_home_returned_page1"));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        s.add(check("TAB closes the Phone", () -> Minecraft.getInstance().gui.screen() == null));
        return s;
    }

    private static String sidecar(String id) {
        try {
            return Files.readString(gallery().store().metadataPath(id));
        } catch (IOException e) {
            return "";
        }
    }

    private static List<Step> tabScene() {
        List<Step> s = new ArrayList<>();
        s.add(run("open home", () -> Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(PhoneFrame.COPPER))));
        s.add(HologramCapture.waitTicks(5));
        s.add(PhoneCaptureStates.clickAt("Camera (dock)", () -> PhoneCaptureStates.dock(3)));
        s.add(HologramCapture.waitTicks(5));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        s.add(check("TAB in the Camera closes the whole Phone session", () -> !CameraSession.isActive()
                && Minecraft.getInstance().gui.screen() == null && near(fov(), baseFov(), 0.6)));
        s.add(run("open the Camera", () -> CameraSession.open(PhoneFrame.COPPER, 0)));
        s.add(HologramCapture.waitTicks(5));
        s.add(run("hold Alt", () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, true)));
        s.add(HologramCapture.waitTicks(2));
        clickAt(s, "Gallery shortcut", () -> {
            CameraViewfinder.Layout l = viewfinder();
            return new double[] {l.thumbX() + l.thumbS() / 2.0, l.thumbY() + l.thumbS() / 2.0};
        });
        s.add(run("release Alt", () -> ModKeybinds.simulatePhysicalPress(ModKeybinds.RADIAL_MODIFIER, false)));
        s.add(HologramCapture.waitTicks(3));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        s.add(HologramCapture.waitTicks(2));
        s.add(check("TAB in a Gallery opened from the Camera ends the session too", () -> !CameraSession.isActive()
                && !CameraSession.isSuspended() && Minecraft.getInstance().gui.screen() == null));
        // The Camera opened from the secondary page returns there.
        s.add(run("open home on page 1", () -> Minecraft.getInstance().gui.setScreen(new PhoneAppGridScreen(PhoneFrame.COPPER, 1))));
        s.add(HologramCapture.waitTicks(4));
        s.add(PhoneCaptureStates.clickAt("Camera (dock, page 1)", () -> PhoneCaptureStates.dock(3)));
        s.add(HologramCapture.waitTicks(4));
        s.add(key("Esc", GLFW.GLFW_KEY_ESCAPE));
        s.add(check("a Camera opened from page 1 returns to page 1", () -> {
            PhoneAppGridScreen g = screenAs(PhoneAppGridScreen.class);
            return g != null && g.page() == 1;
        }));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        return s;
    }

    private static List<Step> reducedMotionScene() {
        List<Step> s = new ArrayList<>();
        double[] effect = new double[1];
        boolean[] flashes = new boolean[1];
        s.add(run("open the Camera (gentle flash, reduced motion)", () -> {
            Minecraft mc = Minecraft.getInstance();
            effect[0] = mc.options.screenEffectScale().get();
            flashes[0] = mc.options.hideLightningFlash().get();
            mc.options.screenEffectScale().set(0.0);
            mc.options.hideLightningFlash().set(true);
            CameraSession.open(PhoneFrame.COPPER, 0);
        }));
        s.add(HologramCapture.waitTicks(8));
        s.add(mc -> {
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, true);
            return true;
        });
        s.add(mc -> {
            HologramCapture.screenshot("cam_63_reduced_t01").tick(mc);
            button(GLFW.GLFW_MOUSE_BUTTON_LEFT, false);
            return true;
        });
        frames(s, "63_reduced_cont", 4);
        s.add(check("reduced motion: no flight, the shortcut updates at once", () -> camera() != null && !camera().animating()));
        s.add(run("restore accessibility options", () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.options.screenEffectScale().set(effect[0]);
            mc.options.hideLightningFlash().set(flashes[0]);
        }));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        return s;
    }

    private static List<Step> guiScaleScene() {
        List<Step> s = new ArrayList<>();
        int[] original = new int[1];
        s.add(run("remember GUI scale", () -> original[0] = Minecraft.getInstance().options.guiScale().get()));
        for (int sc : new int[] {4, 3, 2, 1}) {
            List<Step> per = new ArrayList<>();
            per.add(run("GUI scale " + sc, () -> {
                Minecraft.getInstance().options.guiScale().set(sc);
                Minecraft.getInstance().resizeGui();
            }));
            per.add(run("open the Camera", () -> CameraSession.open(PhoneFrame.COPPER, 0)));
            per.add(HologramCapture.waitTicks(12));
            per.add(shot("70_viewfinder_gui" + sc));
            per.add(check("GUI " + sc + ": viewfinder controls inside the window", () -> {
                CameraViewfinder.Layout l = viewfinder();
                return l.shutterY() + l.shutterR() < l.h() && l.chipX(2, 3) + l.chipW() < l.w() && l.thumbX() > 0;
            }));
            // Scan placeholder: clear of the zoom pill and the top hint at this scale.
            per.add(key("Left (Scan)", GLFW.GLFW_KEY_LEFT));
            per.add(HologramCapture.waitTicks(10));
            per.add(shot("70b_scan_gui" + sc));
            per.add(check("GUI " + sc + ": Scan panel and mode selector keep clear of the zoom controls", () -> {
                CameraViewfinder.Layout l = viewfinder();
                int u = l.u();
                HologramCapture.log("GUI " + sc + ": Scan bottom " + l.scanBottom() + ", zoom pill top " + l.chipPillTop()
                        + ", gap " + (l.chipPillTop() - l.scanBottom()) + " GUI px");
                return camera().mode() == CameraMode.SCAN && l.scanBottom() + 4 * u <= l.chipPillTop()
                        && l.scanTop() >= l.backY() + l.backH() + 2 * u && l.chipPillTop() + l.chipH() + 6 * u <= l.modeY();
            }));
            per.add(key("Right (Normal)", GLFW.GLFW_KEY_RIGHT));
            per.add(run("Gallery", () -> Minecraft.getInstance().gui.setScreen(
                    new GalleryScreen(PhoneOrigin.home(PhoneFrame.COPPER, 1)))));
            per.add(run("leave the camera", CameraSession::reset));
            per.add(HologramCapture.waitTicks(12));
            per.add(shot("71_gallery_gui" + sc));
            per.add(run("viewer", () -> Minecraft.getInstance().gui.setScreen(new PhotoViewerScreen(
                    PhoneOrigin.home(PhoneFrame.COPPER, 1), false, latest().id()))));
            per.add(waitFor("full photo", PhotoTextures::hasFullTexture, 60));
            per.add(HologramCapture.waitTicks(2));
            per.add(shot("72_viewer_gui" + sc));
            per.add(check("GUI " + sc + ": viewer keeps the aspect ratio", () -> {
                PhotoViewerScreen v = screenAs(PhotoViewerScreen.class);
                int[] r = v.photoRect();
                PhotoMetadata m = latest();
                return Math.abs(r[2] / (double) r[3] - m.width() / (double) m.height()) < 0.02;
            }));
            // Rename field: double-click the name, type, check it clears the date line, then ESC (cancel).
            String[] before = new String[1];
            per.add(run("remember name", () -> before[0] = latest().name()));
            per.add(run("pointer to name", () -> pointer(screenAs(PhotoViewerScreen.class).controlCentre("name"))));
            for (int i = 0; i < 2; i++) {
                per.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
                per.add(buttonStep("name", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
            }
            per.add(run("type", () -> screenAs(PhotoViewerScreen.class).typeName("Renamed at GUI " + sc)));
            per.add(HologramCapture.waitTicks(2));
            per.add(shot("72b_rename_gui" + sc));
            per.add(check("GUI " + sc + ": the rename field does not overlap the date/metadata line", () -> {
                PhotoViewerScreen v = screenAs(PhotoViewerScreen.class);
                int[] f = v.nameFieldRect();
                if (f == null) return false;
                HologramCapture.log("GUI " + sc + ": rename field y " + f[1] + ".." + (f[1] + f[3]) + ", details at " + v.detailsTop());
                return f[1] + f[3] + 2 <= v.detailsTop();
            }));
            per.add(key("Esc (cancel)", GLFW.GLFW_KEY_ESCAPE));
            per.add(check("GUI " + sc + ": ESC cancels the rename (name unchanged, viewer open)", () ->
                    screenAs(PhotoViewerScreen.class) != null && !screenAs(PhotoViewerScreen.class).editing()
                            && latest().name().equals(before[0])));
            per.add(key("Tab", GLFW.GLFW_KEY_TAB));
            s.add(when(() -> sc <= maxGuiScale(), "GUI scale " + sc, per));
        }
        s.add(run("restore GUI scale", () -> {
            Minecraft.getInstance().options.guiScale().set(original[0]);
            Minecraft.getInstance().resizeGui();
        }));
        return s;
    }

    private static List<Step> fullScreenScene() {
        List<Step> s = new ArrayList<>();
        int[] count = new int[1];
        s.add(run("development full-screen capture ON", () -> PhotoCapture.setFullScreen(true)));
        s.add(run("open the Camera", () -> CameraSession.open(PhoneFrame.COPPER, 0)));
        s.add(HologramCapture.waitTicks(10));
        s.add(check("development mode keeps the HUD visible", () -> !Minecraft.getInstance().gui.hud.isHidden()));
        s.add(run("count", () -> count[0] = photoCount()));
        s.add(buttonStep("left (full-screen capture)", GLFW.GLFW_MOUSE_BUTTON_LEFT, true));
        s.add(buttonStep("left", GLFW.GLFW_MOUSE_BUTTON_LEFT, false));
        s.add(waitFor("full-screen photo saved", () -> photoCount() == count[0] + 1, 60));
        s.add(check("the development capture includes the interface (shutter ring is in the image)", () -> {
            PhotoMetadata m = latest();
            NativeImage img = readPhoto(m);
            if (img == null) return false;
            try (img) {
                double w = shutterRingWhiteness(img);
                HologramCapture.log("shutter-ring white fraction in the full-screen capture: " + w);
                return w > 0.5 && m.capture().equals("full_screen");
            }
        }));
        s.add(run("development full-screen capture OFF", () -> PhotoCapture.setFullScreen(false)));
        s.add(check("back to clean photography", () -> !PhotoCapture.fullScreen() && Minecraft.getInstance().gui.hud.isHidden()));
        s.add(HologramCapture.waitTicks(4));
        s.add(shot("80_after_dev_capture"));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        return s;
    }

    // ── Persistence and isolation runs ────────────────────────────────────────

    private static void writeManifest() {
        Gallery g = gallery();
        if (g == null) return;
        JsonObject o = new JsonObject();
        o.addProperty("directory", g.store().directory().toString());
        o.addProperty("player", g.scope().player().toString());
        JsonArray photos = new JsonArray();
        for (PhotoMetadata m : g.photos()) {
            JsonObject p = new JsonObject();
            p.addProperty("id", m.id());
            p.addProperty("name", m.name());
            p.addProperty("favorite", m.favorite());
            photos.add(p);
        }
        o.add("photos", photos);
        try {
            Files.writeString(Minecraft.getInstance().gameDirectory.toPath().resolve(MANIFEST), o.toString());
            HologramCapture.log("manifest: " + photos.size() + " photos in " + g.store().directory());
        } catch (IOException e) {
            HologramCapture.log("FAIL: manifest: " + e);
        }
    }

    private static JsonObject manifest() {
        try {
            return JsonParser.parseString(Files.readString(Minecraft.getInstance().gameDirectory.toPath().resolve(MANIFEST))).getAsJsonObject();
        } catch (IOException | RuntimeException e) {
            HologramCapture.log("FAIL: no manifest from the first run: " + e);
            return null;
        }
    }

    private static List<Step> persistScenes() {
        List<Step> s = setup();
        s.add(waitFor("gallery loaded", () -> gallery() != null && gallery().ready(), 100));
        s.add(check("after restarting and reopening the same world as the same player, the Gallery is identical", () -> {
            JsonObject m = manifest();
            Gallery g = gallery();
            if (m == null || g == null) return false;
            JsonArray photos = m.getAsJsonArray("photos");
            if (photos.size() != g.photos().size() || !m.get("directory").getAsString().equals(g.store().directory().toString())) return false;
            for (int i = 0; i < photos.size(); i++) {
                JsonObject p = photos.get(i).getAsJsonObject();
                PhotoMetadata now = g.photos().get(i);
                if (!p.get("id").getAsString().equals(now.id()) || !p.get("name").getAsString().equals(now.name())
                        || p.get("favorite").getAsBoolean() != now.favorite()) return false;
            }
            HologramCapture.log("persisted: " + photos.size() + " photos, ids/names/favourites identical");
            return true;
        }));
        s.add(run("open the Gallery", () -> Minecraft.getInstance().gui.setScreen(
                new GalleryScreen(PhoneOrigin.home(PhoneFrame.COPPER, 1)))));
        s.add(HologramCapture.waitTicks(25));
        s.add(shot("90_persisted_gallery"));
        s.add(run("open the Camera", () -> CameraSession.open(PhoneFrame.COPPER, 0)));
        s.add(HologramCapture.waitTicks(25));
        s.add(shot("91_persisted_camera_shortcut"));
        s.add(check("the Camera shortcut shows the latest saved photo's thumbnail", () -> {
            PhotoMetadata m = latest();
            return m != null && PhotoTextures.thumbnail(gallery(), m) != null;
        }));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        s.add(run("screens back to normal", () -> HologramCapture.allowScreens = false));
        return s;
    }

    private static List<Step> isolatedScenes() {
        List<Step> s = setup();
        s.add(waitFor("gallery loaded", () -> gallery() != null && gallery().ready(), 100));
        s.add(check("another player or world gets its own, empty Gallery", () -> {
            JsonObject m = manifest();
            Gallery g = gallery();
            if (m == null || g == null) return false;
            HologramCapture.log("this gallery: " + g.store().directory() + " (player " + g.scope().player() + ")");
            HologramCapture.log("first gallery: " + m.get("directory").getAsString());
            return g.photos().isEmpty() && !m.get("directory").getAsString().equals(g.store().directory().toString());
        }));
        s.add(check("the first Gallery is untouched", () -> {
            JsonObject m = manifest();
            Path photos = Path.of(m.get("directory").getAsString()).resolve("photos");
            try (var files = Files.list(photos)) {
                long pngs = files.filter(f -> f.toString().endsWith(".png")).count();
                return pngs == m.getAsJsonArray("photos").size();
            } catch (IOException e) {
                return false;
            }
        }));
        s.add(run("open the Gallery", () -> Minecraft.getInstance().gui.setScreen(
                new GalleryScreen(PhoneOrigin.home(PhoneFrame.COPPER, 1)))));
        s.add(HologramCapture.waitTicks(20));
        s.add(shot("95_isolated_empty_gallery"));
        s.add(run("open the Camera", () -> CameraSession.open(PhoneFrame.COPPER, 0)));
        s.add(HologramCapture.waitTicks(20));
        s.add(shot("96_isolated_camera_empty_shortcut"));
        s.add(key("Tab", GLFW.GLFW_KEY_TAB));
        s.add(run("note identity", () -> {
            UUID id = Minecraft.getInstance().getUser().getProfileId();
            HologramCapture.log("player " + Minecraft.getInstance().getUser().getName() + " " + id);
        }));
        s.add(run("screens back to normal", () -> HologramCapture.allowScreens = false));
        return s;
    }

    // ── Shared with PhonePrototypeCapture's idioms ────────────────────────────

    private static int maxGuiScale() {
        Minecraft mc = Minecraft.getInstance();
        return mc.getWindow().calculateScale(0, mc.isEnforceUnicode());
    }

    private static Step when(BooleanSupplier condition, String label, List<Step> steps) {
        int[] next = {-1};
        return mc -> {
            if (next[0] < 0) {
                if (!condition.getAsBoolean()) {
                    HologramCapture.log("skip: " + label + " (not available in this window)");
                    return true;
                }
                next[0] = 0;
            }
            if (next[0] < steps.size() && steps.get(next[0]).tick(mc)) next[0]++;
            return next[0] >= steps.size();
        };
    }
}
