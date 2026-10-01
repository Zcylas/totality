package zcylas.totality.client.hologram.dev;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.client.hologram.HologramManager;
import zcylas.totality.client.hologram.HologramStack;
import zcylas.totality.client.hologram.TargetNameplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.TreeMap;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Opt-in, development-only screenshot run for Notification V2: {@code -Dtotality.hologram.capture=true}
 * in a Fabric development environment. It creates (or reopens) a disposable creative world called
 * {@code HologramCapture} (or the name in {@code -Dtotality.hologram.capture.world}) in the CURRENT game directory —
 * launch it with a throwaway {@code --gameDir}, never the everyday {@code run/} — stages each scene with ordinary commands on the integrated server
 * (time, weather, a dark stone room, a test block), shows real holograms, drives the real input path
 * (a left click counted exactly like a mouse press) and saves screenshots with Minecraft's own screenshot function into
 * {@code <gameDir>/screenshots}. A line-per-step log goes to {@code <gameDir>/hologram-capture.log};
 * the client closes itself at the end. Inert in normal play.
 */
public final class HologramCapture {

    public static final String PROPERTY = "totality.hologram.capture";
    /** The capture world's name; {@code -Dtotality.hologram.capture.world=<name>} picks another (world-isolation runs). */
    static final String WORLD = System.getProperty(PROPERTY + ".world", "HologramCapture");

    private static final TreeMap<Integer, List<Step>> SCENES = new TreeMap<>();
    private static final Deque<Step> PENDING = new ArrayDeque<>();
    private static final List<String> LOG = new ArrayList<>();
    private static Stage stage = Stage.TITLE;
    private static int wait;
    private static int stageTicks;

    private enum Stage { TITLE, CREATING, JOINING, SETTLING, RUNNING, DONE }

    /** One scripted step; {@link #tick} returns true when finished. */
    public interface Step {
        boolean tick(Minecraft mc);
    }

    private HologramCapture() {}

    /** {@code -Dtotality.hologram.capture.server=host:port}: join that (dedicated) server instead of a local world. */
    static final String SERVER_PROPERTY = PROPERTY + ".server";

    /** The run joins a dedicated server (scenes that need the integrated server must not run). */
    public static boolean multiplayer() {
        return System.getProperty(SERVER_PROPERTY) != null;
    }

    private static int consoleSeq;

    /** Set by scenes that photograph a GUI screen: the runner then leaves open screens alone. */
    public static volatile boolean allowScreens;

    /**
     * Multiplayer runs: asks the operator of the dedicated server's console (the capture shell script) to
     * run {@code command} — the script forwards {@code <gameDir>/capture-console/*.cmd} files to the
     * server's standard input. The client itself gains no power from this.
     */
    public static Step console(String command) {
        return mc -> {
            try {
                Path dir = mc.gameDirectory.toPath().resolve("capture-console");
                Files.createDirectories(dir);
                String name = String.format(java.util.Locale.ROOT, "%04d", ++consoleSeq);
                Path tmp = dir.resolve(name + ".tmp");
                Files.writeString(tmp, command + "\n");
                Files.move(tmp, dir.resolve(name + ".cmd"), java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                log("server console: " + command);
            } catch (IOException e) {
                log("FAIL: could not request server console command: " + e);
            }
            return true;
        };
    }

    public static boolean requested() {
        return VerificationReporter.isDevEnvironment() && Boolean.getBoolean(PROPERTY);
    }

    public static void registerIfRequested() {
        if (!requested()) return;
        Totality.LOGGER.warn("[Totality Hologram Capture] ENABLED — disposable world '{}' in {}", WORLD,
                Minecraft.getInstance().gameDirectory.getAbsolutePath());
        addScene(0, environment());
        addScene(20, builtInScenes());
        addScene(22, thirdPersonScenes());
        addScene(40, mobScenes());
        addScene(46, IncenseCapture.scenes());
        addScene(47, RawMeatCapture.scenes());
        addScene(48, ForestBoarCapture.scenes());
        addScene(49, FireboltCapture.scenes());
        addScene(50, VisualPortalCapture.scenes());
        addScene(51, BackSlotCapture.scenes());
        addScene(52, CapeCapture.scenes());
        addScene(53, CapeCapture.geometryScenes());
        addScene(54, SkateboardCapture.scenes());
        addScene(55, KitchenGardenCapture.graphiteScenes());
        addScene(56, KitchenGardenCapture.garlicScenes());
        addScene(57, KitchenGardenCapture.cuttingBoardScenes());
        addScene(58, FireballCapture.scenes());
        addScene(59, SteelSwordCapture.scenes());
        addScene(60, JueyunChiliCapture.scenes());
        addScene(61, DiceRollCapture.scenes());
        addScene(62, NameplateCapture.scenes());
        addScene(63, GateCapture.scenes());
        ClientTickEvents.END_CLIENT_TICK.register(HologramCapture::tick);
    }

    /** Adds steps at an ordering slot (lower runs first). Used by other client packages (voice). */
    public static void addScene(int order, List<Step> steps) {
        SCENES.computeIfAbsent(order, k -> new ArrayList<>()).addAll(steps);
    }

    // ── Step factories ────────────────────────────────────────────────────────

    public static Step run(String label, Runnable action) {
        return mc -> {
            log("run: " + label);
            action.run();
            return true;
        };
    }

    public static Step waitTicks(int ticks) {
        int[] left = {ticks};
        return mc -> --left[0] <= 0;
    }

    /** Runs {@code each} every tick until {@code done} holds (true) or the timeout passes (logged). */
    public static Step until(String label, BooleanSupplier done, Runnable each, int timeoutTicks) {
        int[] t = {0};
        return mc -> {
            if (done.getAsBoolean()) {
                log("ok: " + label + " after " + t[0] + " ticks");
                return true;
            }
            if (++t[0] > timeoutTicks) {
                log("TIMEOUT: " + label);
                return true;
            }
            each.run();
            return false;
        };
    }

    public static Step check(String label, BooleanSupplier condition) {
        return mc -> {
            log((condition.getAsBoolean() ? "PASS: " : "FAIL: ") + label);
            return true;
        };
    }

    public static Step screenshot(String name) {
        return mc -> {
            String file = "notification_v2_" + name + ".png";
            Screenshot.grab(mc.gameDirectory, file, mc.gameRenderer.mainRenderTarget(), 1,
                    message -> log("screenshot: " + file));
            return true;
        };
    }

    public static Step command(String command) {
        return mc -> {
            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null || mc.player == null) return true;
            java.util.UUID id = mc.player.getUUID();
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null) server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            });
            log("command: /" + command);
            return true;
        };
    }

    public static Step look(float yaw, float pitch) {
        return mc -> {
            if (mc.player == null) return true;
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            return true;
        };
    }

    /** Presses a key mapping's bound key exactly as a real key press would be counted. */
    public static Step press(String label, Supplier<KeyMapping> mapping) {
        return mc -> {
            KeyMapping.click(InputConstants.getKey(mapping.get().saveString()));
            log("press: " + label);
            return true;
        };
    }

    // ── Built-in scenes ───────────────────────────────────────────────────────

    private static List<Step> environment() {
        List<Step> s = new ArrayList<>();
        s.add(command("gamerule send_command_feedback false"));
        s.add(command("gamerule advance_time false"));
        s.add(command("weather clear"));
        s.add(command("time set 6000"));
        s.add(look(-35, 8));
        s.add(waitTicks(40));
        return s;
    }

    private static List<Step> builtInScenes() {
        List<Step> s = new ArrayList<>();

        s.add(run("feed samples", HologramShowcase::feed));
        s.add(waitTicks(8));
        s.add(screenshot("00_feed_day"));
        s.add(waitTicks(100));

        s.add(run("system hologram", () -> HologramManager.show(HologramShowcase.dailyQuest())));
        s.add(waitTicks(3));
        s.add(screenshot("03a_system_opening"));
        s.add(waitTicks(30));
        s.add(screenshot("03_system_day"));
        s.addAll(touch("dismiss"));
        s.add(waitTicks(2));
        s.add(screenshot("03b_system_closing"));
        s.add(waitTicks(20));
        s.add(check("left-click on DISMISS closed the hologram", () -> HologramManager.activeKey() == null));
        s.add(look(-35, 8));

        // Touch Confirm with a stone block right behind the button: in creative a leaked left click
        // would break it instantly. Afterwards an ordinary click must break it (control).
        s.add(run("confirm hologram", () -> HologramManager.show(HologramShowcase.confirm())));
        s.add(waitTicks(30));
        s.add(screenshot("04_confirm_idle"));
        s.add(until("crosshair aimed at 'cancel'", () -> "cancel".equals(HologramManager.hoveredAction()),
                () -> steerTowards("cancel"), 200));
        s.add(waitTicks(2));
        s.add(screenshot("05a_cancel_arming"));
        s.add(waitTicks(8));
        s.add(screenshot("05b_cancel_armed"));
        s.add(until("crosshair aimed at 'confirm'", () -> "confirm".equals(HologramManager.hoveredAction()),
                () -> steerTowards("confirm"), 200));
        s.add(placeBlockOnCrosshair());
        s.add(waitTicks(8));
        s.add(check("a stone block is behind the aimed Confirm button", HologramCapture::crosshairOnPlacedBlock));
        s.add(screenshot("05_confirm_armed"));
        s.add(run("clear showcase log", HologramShowcase.PERFORMED::clear));
        s.add(press("Attack (left click) on armed Confirm", () -> Minecraft.getInstance().options.keyAttack));
        s.add(waitTicks(1));
        s.add(check("left click consumed: Confirm handler ran once", () -> HologramShowcase.PERFORMED.equals(List.of("confirm"))));
        s.add(check("hologram closing after the touch", () -> HologramManager.activeKey() == null
                || HologramManager.activePhase() == HologramStack.Phase.CLOSING));
        s.add(check("Attack key released for this press", () -> !Minecraft.getInstance().options.keyAttack.isDown()));
        s.add(check("touch plays the arm swing (visual only)", () -> Minecraft.getInstance().player.swinging));
        s.add(screenshot("05c_confirm_touched_swing"));
        s.add(waitTicks(5));
        s.add(check("no leak: the block behind the button was NOT broken", HologramCapture::placedBlockPresent));
        s.add(check("no leak: no block is being destroyed", () -> !Minecraft.getInstance().gameMode.isDestroying()));
        s.add(waitTicks(30));
        s.add(press("control: ordinary left click with no hologram", () -> Minecraft.getInstance().options.keyAttack));
        s.add(waitTicks(5));
        s.add(check("control: an ordinary left click still breaks the block", () -> !placedBlockPresent()));
        s.add(look(-35, 8));

        s.add(command("time set 18000"));
        s.add(waitTicks(20));
        s.add(run("warning hologram", () -> HologramManager.show(HologramShowcase.warning())));
        s.add(waitTicks(30));
        s.add(screenshot("06_warning_night"));
        s.add(check("a resting crosshair never arms a button", () -> HologramManager.hoveredAction() == null));
        s.add(run("clear", HologramManager::clear));

        s.add(run("queue: NORMAL notice", () -> HologramManager.show(HologramShowcase.dailyQuest())));
        s.add(waitTicks(40));
        s.add(run("queue: CRITICAL interrupt", () -> HologramManager.show(HologramShowcase.urgent())));
        s.add(waitTicks(30));
        s.add(check("NORMAL notice suspended, CRITICAL displayed", () -> HologramManager.suspendedCount() == 1
                && "totality:showcase/urgent".equals(HologramManager.activeKey())));
        s.add(screenshot("07_error_interrupt_night"));
        s.addAll(touch("acknowledge"));
        s.add(waitTicks(30));
        s.add(check("NORMAL notice resumed", () -> "totality:showcase/daily_quest".equals(HologramManager.activeKey())
                && HologramManager.suspendedCount() == 0));
        s.add(screenshot("08_resumed_night"));
        s.add(run("clear", HologramManager::clear));

        s.add(command("time set 6000"));
        s.add(look(140, 20));
        s.add(waitTicks(30));
        s.add(run("success hologram", () -> HologramManager.show(HologramShowcase.questComplete())));
        s.add(waitTicks(30));
        s.add(screenshot("09_success_day_terrain"));
        s.add(run("clear", HologramManager::clear));

        s.add(command("execute at @s run fill ~-4 -40 ~-4 ~4 -35 ~4 air"));
        s.add(command("execute at @s run setblock ~3 -40 ~3 torch"));
        s.add(command("execute at @s run tp @s ~ -40 ~ -35 5"));
        s.add(waitTicks(40));
        s.add(run("loading hologram", () -> HologramManager.show(HologramShowcase.loading())));
        s.add(waitTicks(30));
        s.add(screenshot("11_cave_loading"));
        s.add(run("confirm hologram", () -> HologramManager.show(HologramShowcase.confirm())));
        s.add(waitTicks(30));
        s.add(screenshot("12_cave_confirm_over_loading"));
        s.add(run("clear", HologramManager::clear));
        s.add(command("execute at @s run tp @s ~ ~120 ~"));
        s.add(look(-35, 8));
        s.add(waitTicks(30));
        return s;
    }

    /** Third person: rear/front windows, physical depth probes, walking, turning, steep camera. */
    private static List<Step> thirdPersonScenes() {
        List<Step> s = new ArrayList<>();
        s.add(command("time set 6000"));
        s.add(waitTicks(40));                                  // settle (e.g. landing after the cave scene)
        s.add(look(-35, 5));
        for (CameraType view : List.of(CameraType.THIRD_PERSON_BACK, CameraType.THIRD_PERSON_FRONT)) {
            String name = view == CameraType.THIRD_PERSON_BACK ? "rear" : "front";
            s.add(run("third person " + name, () -> Minecraft.getInstance().options.setCameraType(view)));
            s.add(run("system hologram", () -> HologramManager.show(HologramShowcase.dailyQuest())));
            s.add(waitTicks(30));
            String face = view == CameraType.THIRD_PERSON_BACK ? "third_person" : "third_person_rear_face";
            s.add(check("third person " + name + ": drawn in front of the character, " + (view == CameraType.THIRD_PERSON_BACK
                    ? "readable face toward character and camera" : "camera sees the projection's REAR face"),
                    () -> face.equals(HologramManager.placement())));
            s.add(screenshot("10_third_person_" + name));
            // Physical depth: in the rear view the character stands in front of the window, so the pixels
            // over its head must be untouched by the hologram; in the front view the window stands in
            // front of the character, so they must show it (translucently). The patch beside the head
            // must show the window in both views.
            s.addAll(depthProbe(name + "_level", view == CameraType.THIRD_PERSON_FRONT));
            // Walking forward (the real forward key), then turning: a short frame sequence each.
            s.add(run("walk forward", () -> Minecraft.getInstance().options.keyUp.setDown(true)));
            for (int f = 0; f < 6; f++) {
                s.add(waitTicks(5));
                s.add(screenshot("14_" + name + "_walk_" + f));
            }
            s.add(run("stop walking", () -> Minecraft.getInstance().options.keyUp.setDown(false)));
            for (int f = 0; f < 6; f++) {
                s.add(turn(3.5f, 4));
                s.add(screenshot("15_" + name + "_turn_" + f));
            }
            s.add(waitTicks(10));
            // Sharp up/down camera: the window stays a mostly upright pane and never tips into the character.
            for (float pitch : new float[] {-60, 60}) {
                String dir = pitch < 0 ? "up" : "down";
                s.add(pitch(pitch));
                s.add(waitTicks(25));
                s.add(screenshot("16_" + name + "_look_" + dir));
                if (view == CameraType.THIRD_PERSON_BACK) s.addAll(depthProbe(name + "_look_" + dir, false));
            }
            s.add(pitch(5));
            s.add(waitTicks(25));
            if (view == CameraType.THIRD_PERSON_BACK) {
                s.addAll(touch("dismiss"));
                s.add(waitTicks(2));
                s.add(screenshot("10b_third_person_rear_touched_swing"));
                s.add(waitTicks(20));
                s.add(check("third person rear: left-click touch closed the hologram", () -> HologramManager.activeKey() == null));
            } else {
                s.add(check("third person front: nothing on the rear face can be aimed at", () -> HologramManager.hoveredAction() == null));
                s.add(run("clear", HologramManager::clear));
            }
            s.add(look(-35, 5));
        }
        s.add(run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(run("clear", HologramManager::clear));

        return s;
    }

    private static Step pitch(float pitch) {
        return mc -> {
            if (mc.player != null) {
                mc.player.setXRot(pitch);
                mc.player.xRotO = pitch;
            }
            return true;
        };
    }

    // ── Third-person depth probe ──────────────────────────────────────────────

    private static final java.util.Map<String, int[][]> PROBES = new java.util.HashMap<>();

    /**
     * Compares the frame with and without the (daily-quest) hologram at the same pose. In third person the
     * character's eyes are at the screen centre, so a patch just above the centre is the character's head;
     * a patch to the right at the same height is empty space the window covers. {@code headCovered}: the
     * window is expected in front of the head (front view) rather than behind it.
     */
    private static List<Step> depthProbe(String name, boolean headCovered) {
        List<Step> s = new ArrayList<>();
        s.add(probe(name + "_with"));
        s.add(run("hide hologram for the depth probe", HologramManager::clear));
        s.add(waitTicks(25));
        s.add(probe(name + "_without"));
        s.add(screenshot("17_depth_" + name + "_without_hologram"));
        s.add(mc -> {
            double head = probeDiff(name, 0), side = probeDiff(name, 1);
            boolean ok = (headCovered ? head > 6 : head < 1.5) && (side > 6 || name.contains("look"));
            log(String.format(java.util.Locale.ROOT, "%s: depth probe %s — head patch changed by %.2f (%s), side patch by %.2f",
                    ok ? "PASS" : "FAIL", name, head,
                    headCovered ? "window in front of the character: expected visible" : "character in front of the window: expected untouched",
                    side));
            return true;
        });
        s.add(run("system hologram", () -> HologramManager.show(HologramShowcase.dailyQuest())));
        s.add(waitTicks(30));
        return s;
    }

    private static Step probe(String key) {
        boolean[] requested = {false};
        return mc -> {
            if (!requested[0]) {
                requested[0] = true;
                Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), image -> {
                    try (image) {
                        int w = image.getWidth(), h = image.getHeight(), cx = w / 2, cy = h / 2;
                        double k = h / 1080.0;
                        int top = cy - (int) (38 * k), bottom = cy - (int) (18 * k);
                        PROBES.put(key, new int[][] {
                                patch(image, cx - (int) (12 * k), top, cx + (int) (12 * k), bottom),
                                patch(image, cx + (int) (140 * k), top, cx + (int) (164 * k), bottom)});
                    }
                });
            }
            return PROBES.containsKey(key);
        };
    }

    private static int[] patch(com.mojang.blaze3d.platform.NativeImage image, int x0, int y0, int x1, int y1) {
        int[] out = new int[(x1 - x0) * (y1 - y0)];
        int i = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) out[i++] = image.getPixel(x, y);
        return out;
    }

    /** Mean absolute per-channel difference (0–255) of one patch between the with/without frames. */
    private static double probeDiff(String name, int patch) {
        int[] a = PROBES.get(name + "_with")[patch], b = PROBES.get(name + "_without")[patch];
        long sum = 0;
        for (int i = 0; i < a.length; i++) {
            for (int shift = 0; shift < 24; shift += 8) sum += Math.abs(((a[i] >> shift) & 0xFF) - ((b[i] >> shift) & 0xFF));
        }
        return sum / (a.length * 3.0);
    }

    /** Turns the view {@code degreesPerTick} to the right for {@code ticks} ticks. */
    private static Step turn(float degreesPerTick, int ticks) {
        int[] left = {ticks};
        return mc -> {
            if (mc.player != null) {
                float y = mc.player.getYRot() + degreesPerTick;
                mc.player.setYRot(y);
                mc.player.yRotO = y - degreesPerTick;
            }
            return --left[0] <= 0;
        };
    }

    // ── Mob HUD V1 target nameplate ───────────────────────────────────────────

    static List<Step> mobScenes() {
        List<Step> s = new ArrayList<>();
        s.add(command("time set 6000"));
        // A clear, flat arena so the mobs (and the circling camera) are not inside the hillside.
        s.add(command("execute at @s run fill ~-10 ~ ~-8 ~10 ~8 ~14 minecraft:air"));
        s.add(command("execute at @s run fill ~-10 ~-1 ~-8 ~10 ~-1 ~14 minecraft:grass_block"));
        s.add(look(0, -30));                                   // above the mobs' heads: no target
        s.add(waitTicks(5));
        s.add(command("execute at @s run summon minecraft:husk ~-3 ~ ~6 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}"));
        s.add(command("execute at @s run summon minecraft:creeper ~0 ~ ~7 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}"));
        s.add(command("execute at @s run summon minecraft:spider ~3.5 ~ ~6 {NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}"));
        s.add(waitTicks(30));
        s.add(check("no label while no mob is looked at", () -> TargetNameplate.visibleCount() == 0));
        s.add(screenshot("30_mobs_no_target"));
        s.add(lookAt("minecraft:husk"));
        s.add(waitTicks(20));
        s.add(check("looking at the husk: exactly one label, on the husk", () -> TargetNameplate.visibleCount() == 1
                && isType(TargetNameplate.shownTarget(), "minecraft:husk")));
        s.add(screenshot("31_mob_label_husk"));
        s.add(sweep("minecraft:spider", 60));
        s.add(lookAt("minecraft:creeper"));
        s.add(waitTicks(20));
        s.add(check("looking at the creeper: its label only", () -> isType(TargetNameplate.shownTarget(), "minecraft:creeper")));
        s.add(screenshot("32_mob_label_creeper"));
        s.add(lookAt("minecraft:spider"));
        s.add(waitTicks(20));
        s.add(screenshot("33_mob_label_spider"));
        s.add(look(-75, 5));                                   // away from every mob, spider still in view
        s.add(waitTicks(4));
        s.add(check("looking away: the label lingers briefly", () -> TargetNameplate.visibleCount() == 1));
        s.add(screenshot("34_mob_label_lingering"));
        s.add(waitTicks(30));
        s.add(check("looking away: the label is gone", () -> TargetNameplate.visibleCount() == 0));
        // Circle the husk: the label stays readable (billboarded) from the front, flanks and behind.
        String[] sides = {"front", "left", "behind", "right"};
        double[][] offsets = {{0, 4}, {4, 0}, {0, -4}, {-4, 0}};
        for (int i = 0; i < sides.length; i++) {
            double[] o = offsets[i];
            s.add(aroundHusk(o[0], o[1]));
            s.add(waitTicks(8));
            s.add(lookAt("minecraft:husk"));
            s.add(waitTicks(14));
            s.add(check("circling: husk labelled from " + sides[i], () -> isType(TargetNameplate.shownTarget(), "minecraft:husk")));
            s.add(screenshot("35_mob_label_circle_" + i + "_" + sides[i]));
        }
        s.add(run("third person", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK)));
        s.add(lookAt("minecraft:husk"));
        s.add(waitTicks(20));
        s.add(screenshot("36_mob_label_third_person"));
        s.add(run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(command("kill @e[type=!minecraft:player,distance=..40]"));
        s.add(waitTicks(20));
        return s;
    }

    /** Turns toward {@code id} for {@code ticks} ticks, then reports the most labels seen on any tick. */
    private static Step sweep(String id, int ticks) {
        int[] state = {0, 0};
        return mc -> {
            state[1] = Math.max(state[1], TargetNameplate.visibleCount());
            if (++state[0] < ticks) {
                nudgeYawTowards(id);
                return false;
            }
            log((state[1] <= 1 ? "PASS" : "FAIL") + ": sweeping across three mobs, at most " + state[1] + " label(s) on any tick");
            return true;
        };
    }

    private static boolean isType(net.minecraft.world.entity.@org.jetbrains.annotations.Nullable Entity e, String id) {
        return e != null && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).toString().equals(id);
    }

    private static net.minecraft.world.entity.@org.jetbrains.annotations.Nullable Entity nearest(Minecraft mc, String id) {
        if (mc.player == null || mc.level == null) return null;
        net.minecraft.world.entity.Entity best = null;
        for (net.minecraft.world.entity.Entity e : mc.level.entitiesForRendering()) {
            if (isType(e, id) && (best == null || e.distanceToSqr(mc.player) < best.distanceToSqr(mc.player))) best = e;
        }
        return best;
    }

    private static float[] anglesTo(Minecraft mc, net.minecraft.world.entity.Entity e) {
        net.minecraft.world.phys.Vec3 d = e.position().add(0, e.getBbHeight() * 0.6, 0).subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        return new float[] {yaw, pitch};
    }

    private static Step lookAt(String id) {
        return mc -> {
            net.minecraft.world.entity.Entity e = nearest(mc, id);
            if (e == null) {
                log("TIMEOUT: no " + id + " to look at");
                return true;
            }
            float[] a = anglesTo(mc, e);
            return look(a[0], a[1]).tick(mc);
        };
    }

    private static void nudgeYawTowards(String id) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.world.entity.Entity e = nearest(mc, id);
        if (e == null || mc.player == null) return;
        float target = anglesTo(mc, e)[0];
        float y = mc.player.getYRot() + Math.clamp(net.minecraft.util.Mth.wrapDegrees(target - mc.player.getYRot()), -1.2f, 1.2f);
        mc.player.setYRot(y);
        mc.player.yRotO = y;
    }

    private static Step aroundHusk(double dx, double dz) {
        return mc -> {
            net.minecraft.world.entity.Entity z = nearest(mc, "minecraft:husk");
            if (z == null) return true;
            return command(String.format(java.util.Locale.ROOT, "tp @s %.2f %.2f %.2f", z.getX() + dx, z.getY(), z.getZ() + dz)).tick(mc);
        };
    }

    /** Steer onto a button, let it arm, then press Attack exactly as a left mouse click is counted. */
    public static List<Step> touch(String actionId) {
        return List.of(
                // Glance off the buttons first, as a player would: a button that was already under a
                // resting crosshair stays disarmed until the aim leaves it.
                mc -> {
                    lastOffset = null;
                    steerSign[0] = steerSign[1] = 1;
                    if (mc.player != null) {
                        float p = mc.player.getXRot() - 6;
                        mc.player.setXRot(p);
                        mc.player.xRotO = p;
                    }
                    return true;
                },
                waitTicks(3),
                until("crosshair aimed at '" + actionId + "'", () -> actionId.equals(HologramManager.hoveredAction()),
                        () -> steerTowards(actionId), 240),
                waitTicks(8),
                mc -> {
                    // Only a click a player would make: on the aimed, armed button — never on the world.
                    if (actionId.equals(HologramManager.hoveredAction())) {
                        return press("left click on '" + actionId + "'", () -> mc.options.keyAttack).tick(mc);
                    }
                    log("SKIPPED click: '" + actionId + "' was not aimed at");
                    return true;
                });
    }

    private static net.minecraft.core.@org.jetbrains.annotations.Nullable BlockPos placedBlock;

    /** Places stone a few blocks along the current view ray (behind the aimed hologram button). */
    private static Step placeBlockOnCrosshair() {
        return mc -> {
            if (mc.player == null) return true;
            net.minecraft.world.phys.Vec3 eye = mc.player.getEyePosition();
            placedBlock = net.minecraft.core.BlockPos.containing(eye.add(mc.player.getLookAngle().scale(3.5)));
            return command("setblock " + placedBlock.getX() + " " + placedBlock.getY() + " " + placedBlock.getZ()
                    + " minecraft:stone").tick(mc);
        };
    }

    private static boolean crosshairOnPlacedBlock() {
        Minecraft mc = Minecraft.getInstance();
        return placedBlock != null && mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult hit
                && hit.getBlockPos().equals(placedBlock);
    }

    private static boolean placedBlockPresent() {
        Minecraft mc = Minecraft.getInstance();
        return placedBlock != null && mc.level != null
                && mc.level.getBlockState(placedBlock).is(net.minecraft.world.level.block.Blocks.STONE);
    }

    private static final float[] steerSign = {1, 1};
    private static float @org.jetbrains.annotations.Nullable [] lastOffset;

    /**
     * Turns the view a small step toward a button's centre, as a player glancing at it would. Adaptive:
     * if a step made an axis worse it flips that axis — in the mirrored front third-person view the
     * camera orbits the character, so the window drifts opposite to the mouse.
     */
    static void steerTowards(String actionId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        float[] offset = HologramManager.aimOffsetTo(actionId);
        if (offset != null && lastOffset != null) {
            for (int axis = 0; axis < 2; axis++) {
                if (Math.abs(offset[axis]) > Math.abs(lastOffset[axis]) + 0.5f) steerSign[axis] = -steerSign[axis];
            }
        }
        lastOffset = offset;
        float dYaw = offset == null ? 0 : steerSign[0] * Math.clamp(offset[0] * 0.04f, -0.5f, 0.5f);
        float dPitch = offset == null ? -0.4f : steerSign[1] * Math.clamp(offset[1] * 0.04f, -0.5f, 0.5f);
        float yaw = mc.player.getYRot() + dYaw, pitch = mc.player.getXRot() + dPitch;
        mc.player.setYRot(yaw);
        mc.player.yRotO = yaw;
        mc.player.setXRot(pitch);
        mc.player.xRotO = pitch;
    }

    // ── Driver ────────────────────────────────────────────────────────────────

    private static void tick(Minecraft mc) {
        stageTicks++;
        switch (stage) {
            case TITLE -> {
                if (!(mc.gui.screen() instanceof TitleScreen title)) return;
                if (multiplayer()) {
                    String address = System.getProperty(SERVER_PROPERTY);
                    log("joining dedicated server " + address);
                    net.minecraft.client.gui.screens.ConnectScreen.startConnecting(title, mc,
                            net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(address),
                            new net.minecraft.client.multiplayer.ServerData("Totality capture", address,
                                    net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                    enter(Stage.JOINING);
                } else if (mc.getLevelSource().levelExists(WORLD)) {
                    log("opening existing world " + WORLD);
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> {});
                    enter(Stage.JOINING);
                } else {
                    log("creating world " + WORLD);
                    CreateWorldScreen.openFresh(mc, () -> {});
                    enter(Stage.CREATING);
                }
            }
            case CREATING -> {
                if (!(mc.gui.screen() instanceof CreateWorldScreen screen) || stageTicks < 20) return;
                WorldCreationUiState ui = screen.getUiState();
                ui.setName(WORLD);
                ui.setSeed("totality-notification-v2");
                ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                ui.setAllowCommands(true);
                pressCreate(screen);
                enter(Stage.JOINING);
            }
            case JOINING -> {
                if (mc.player != null && mc.level != null) enter(Stage.SETTLING);
                else if (stageTicks > 20 * 180) finish(mc, "world did not load");
            }
            case SETTLING -> {
                if (mc.gui.screen() != null) mc.gui.setScreen(null);
                if (stageTicks < 20 * 8) return;
                // Optional dev filter: -Dtotality.hologram.capture.scenes=0,22 runs only those slots.
                String only = System.getProperty(PROPERTY + ".scenes", "");
                java.util.Set<String> wanted = java.util.Set.of(only.split(","));
                SCENES.forEach((order, steps) -> {
                    if (only.isBlank() || wanted.contains(String.valueOf(order))) PENDING.addAll(steps);
                });
                log("running " + PENDING.size() + " steps");
                enter(Stage.RUNNING);
            }
            case RUNNING -> {
                if (mc.player == null) {
                    finish(mc, "player left the world");
                    return;
                }
                if (mc.gui.screen() != null && !allowScreens) mc.gui.setScreen(null);
                while (!PENDING.isEmpty()) {
                    if (!PENDING.peekFirst().tick(mc)) return;
                    PENDING.removeFirst();
                    // One screenshot/command/wait per tick keeps frames between steps.
                    return;
                }
                if (wait++ < 40) return;
                finish(mc, "all steps done");
            }
            case DONE -> { }
        }
    }

    private static void pressCreate(CreateWorldScreen screen) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents t
                    && t.getKey().equals("selectWorld.create")) {
                button.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
                return;
            }
        }
        screen.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
    }

    private static void enter(Stage next) {
        stage = next;
        stageTicks = 0;
    }

    private static void finish(Minecraft mc, String why) {
        log("finished: " + why);
        enter(Stage.DONE);
        try {
            Files.write(Path.of(mc.gameDirectory.getAbsolutePath(), "hologram-capture.log"), LOG);
        } catch (IOException e) {
            Totality.LOGGER.warn("[Totality Hologram Capture] could not write log", e);
        }
        mc.stop();
    }

    public static void log(String line) {
        LOG.add(line);
        Totality.LOGGER.info("[Totality Hologram Capture] {}", line);
    }
}
