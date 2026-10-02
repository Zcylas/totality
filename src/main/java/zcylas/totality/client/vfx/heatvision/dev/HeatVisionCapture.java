package zcylas.totality.client.vfx.heatvision.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.renderer.ability.HeatVisionBeam;
import zcylas.totality.client.renderer.ability.HeatVisionBeamRenderer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.networking.ability.ClientAbilityManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Heat Vision before/after footage for the opt-in development capture run (scene 67, prefix {@code HV_}); inert in
 * normal play. The beam is shown by setting the CLIENT channelling flag, exactly the state the beam renderer reads, so
 * the server-side ability (mana drain, damage, block scorching) does not run and the stage stays intact between
 * frames. {@code -Dtotality.vfx.capture.tag=<tag>} labels a run (e.g. before / after_gl / after_vk).
 *
 * <p>Views 01-09 are shared by the before run (pre-V2 renderer) and the after runs; the rest (10+) exist from V2 on:
 * Emissive Rendering Layer off/on, a side-on depth probe (a test beam passing behind a pillar, classic vs V2), beams at
 * three distances, an impact close-up, GPU timings for 2-258 beams with both renderers, and the clean-up check.
 */
public final class HeatVisionCapture {

    private static final Identifier HEAT_VISION = Identifier.fromNamespaceAndPath("totality", "heat_vision");
    private static final double EYE = 1.62;
    private static final String TAG = System.getProperty("totality.vfx.capture.tag", "");

    private HeatVisionCapture() {}

    static String shot(String name) {
        return "HV_" + (TAG.isEmpty() ? "" : TAG + "_") + name;
    }

    public static Step screenshot(String name) {
        return HologramCapture.screenshot(shot(name));
    }

    public static Step beam(boolean on) {
        return HologramCapture.run("heat vision beam " + (on ? "on" : "off"),
                () -> ClientAbilityManager.setChanneling(on ? HEAT_VISION : null));
    }

    private static Step cameraType(CameraType type) {
        return HologramCapture.run("camera " + type, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    /** Puts the player's eyes at {@code stage + eye offset} looking at {@code stage + look}. */
    public static Step eyes(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) return true;
            Vec3 eye = new Vec3(stage[0] + ex, stage[1] + ey, stage[2] + ez);
            Vec3 at = new Vec3(stage[0] + lx, stage[1] + ly, stage[2] + lz);
            double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().getFirst();
                p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
            });
            return true;
        };
    }

    private static Step fill(double[] stage, String from, String to, String block) {
        return mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill %s %s %s",
                stage[0], stage[1], stage[2], from, to, block)).tick(mc);
    }

    private static Step settle() {
        return HologramCapture.waitTicks(10);
    }

    public static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("gamerule advance_time false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.run("hide HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (!hud.isHidden()) hud.toggle();
        }));
        s.add(HologramCapture.run("remember the stage", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY()) + 24;
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A floating stage 24 blocks above spawn: stone-brick floor, a far andesite wall 18 blocks ahead, a near
        // deepslate block 6 blocks ahead on the right, open sky above.
        s.add(fill(stage, "~-9 ~-1 ~-6", "~9 ~10 ~20", "minecraft:air"));
        s.add(fill(stage, "~-9 ~-1 ~-6", "~9 ~-1 ~20", "minecraft:stone_bricks"));
        s.add(fill(stage, "~-7 ~0 ~18", "~7 ~7 ~18", "minecraft:polished_andesite"));
        s.add(fill(stage, "~2 ~0 ~6", "~4 ~2 ~6", "minecraft:polished_deepslate"));
        s.add(cameraType(CameraType.FIRST_PERSON));
        s.add(eyes(stage, 0, EYE, 0, 0, 2.2, 18));
        s.add(HologramCapture.waitTicks(40));

        // ── Day: first person, far wall / near block / sky ──
        s.add(beam(false));
        s.add(settle());
        s.add(screenshot("01_fp_far_off"));
        s.add(beam(true));
        s.add(settle());
        s.add(screenshot("02_fp_far_on"));
        s.add(eyes(stage, 0, EYE, 0, 3, 1.2, 6));
        s.add(settle());
        s.add(screenshot("03_fp_near_on"));
        s.add(eyes(stage, 0, EYE, 0, 0, 14, 18));
        s.add(settle());
        s.add(screenshot("04_fp_sky_on"));

        // ── Third person (back and front) looking at the far wall ──
        s.add(eyes(stage, 0, EYE, 0, 0, 2.2, 18));
        s.add(cameraType(CameraType.THIRD_PERSON_BACK));
        s.add(settle());
        s.add(screenshot("05_tp_back_on"));
        s.add(cameraType(CameraType.THIRD_PERSON_FRONT));
        s.add(settle());
        s.add(screenshot("06_tp_front_on"));
        s.add(cameraType(CameraType.FIRST_PERSON));

        // ── Night ──
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.waitTicks(20));
        s.add(screenshot("07_fp_far_night_on"));
        s.add(eyes(stage, 0, EYE, 0, 3, 1.2, 6));
        s.add(settle());
        s.add(screenshot("08_fp_near_night_on"));
        s.add(beam(false));
        s.add(settle());
        s.add(screenshot("09_fp_near_night_off"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.waitTicks(10));

        // ── After-run additions (V2): emissive layer off/on, depth probe, distance, impact, measurements ──
        afterRun(s, stage);
        s.add(beam(false));
        s.add(HologramCapture.run("show HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
        return s;
    }

    private static Step glowLayer(boolean on) {
        return HologramCapture.run("emissive rendering layer " + (on ? "on" : "off"), () -> EmissiveGlow.settings().setEnabled(on));
    }

    private static Step classic(boolean on) {
        return HologramCapture.run("heat vision renderer " + (on ? "classic (pre-V2)" : "V2"), () -> HeatVisionBeamRenderer.setClassic(on));
    }

    private static Step testBeams(double[] stage, double[][] segments) {
        return HologramCapture.run(segments.length + " test beam(s)", () -> {
            List<HeatVisionBeam> beams = new ArrayList<>();
            for (double[] g : segments) {
                beams.add(HeatVisionDev.beam(new Vec3(stage[0] + g[0], stage[1] + g[1], stage[2] + g[2]),
                        new Vec3(stage[0] + g[3], stage[1] + g[4], stage[2] + g[5])));
            }
            HeatVisionBeamRenderer.setTestBeams(beams);
        });
    }

    private static Step fan(int count) {
        return HologramCapture.run(count + " fan test beams", () -> HeatVisionDev.fanInFront(count));
    }

    private static Step clearTestBeams() {
        return HologramCapture.run("clear test beams", () -> HeatVisionBeamRenderer.setTestBeams(List.of()));
    }

    /** Samples GPU time of the beam draws and of the emissive layer for {@code ticks} ticks, then logs averages. */
    private static Step measure(String label, int ticks) {
        long[] beam = {0, 0};
        long[] glow = {0, 0};
        int[] fps = {0};
        int[] t = {0};
        return mc -> {
            long b = HeatVisionBeamRenderer.lastGpuNanos();
            if (b > 0) {
                beam[0] += b;
                beam[1]++;
            }
            long g = EmissiveGlow.lastGpuNanos();
            if (g > 0) {
                glow[0] += g;
                glow[1]++;
            }
            fps[0] += mc.getFps();
            if (++t[0] < ticks) return false;
            HologramCapture.log(String.format(Locale.ROOT,
                    "timing: %s | %dx%d | %s | %d beams, %d draw calls | beam GPU %s | emissive layer GPU %s (%d quads) | vertex buffers %d KiB | fps avg %d",
                    label, mc.gameRenderer.mainRenderTarget().width, mc.gameRenderer.mainRenderTarget().height,
                    HeatVisionBeamRenderer.classic() ? "classic" : "V2", HeatVisionBeamRenderer.lastBeamCount(),
                    HeatVisionBeamRenderer.lastDrawCalls(), ms(beam), ms(glow), EmissiveGlow.lastQuadCount(),
                    HeatVisionBeamRenderer.vertexBufferBytes() / 1024, fps[0] / t[0]));
            return true;
        };
    }

    private static String ms(long[] sum) {
        return sum[1] == 0 ? "n/a" : String.format(Locale.ROOT, "%.3f ms", sum[0] / (double) sum[1] / 1.0e6);
    }

    private static void afterRun(List<Step> s, double[] stage) {
        s.add(HologramCapture.run("GPU timing on", () -> {
            HeatVisionBeamRenderer.setTiming(true);
            EmissiveGlow.setTiming(true);
        }));
        s.add(classic(false));
        s.add(glowLayer(true));

        // ── Emissive Rendering Layer off vs on (same V2 beam) ──
        s.add(eyes(stage, 0, EYE, 0, 0, 2.2, 18));
        s.add(beam(true));
        s.add(glowLayer(false));
        s.add(settle());
        s.add(screenshot("10_fp_far_layer_off"));
        s.add(glowLayer(true));
        s.add(settle());
        s.add(screenshot("11_fp_far_layer_on"));
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.waitTicks(20));
        s.add(glowLayer(false));
        s.add(settle());
        s.add(screenshot("12_fp_far_night_layer_off"));
        s.add(glowLayer(true));
        s.add(settle());
        s.add(screenshot("13_fp_far_night_layer_on"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(beam(false));
        s.add(HologramCapture.waitTicks(20));

        // ── Depth probe: a test beam crossing the view 10 blocks ahead, BEHIND a stone pillar 6 blocks ahead ──
        s.add(fill(stage, "~0 ~0 ~6", "~0 ~4 ~6", "minecraft:stone"));
        s.add(testBeams(stage, new double[][] {{-6, 2.0, 10, 6, 2.0, 10}}));
        s.add(eyes(stage, 0, EYE, 0, 0, 2.0, 10));
        s.add(classic(true));
        s.add(settle());
        s.add(screenshot("14_depth_probe_classic"));
        s.add(classic(false));
        s.add(settle());
        s.add(screenshot("15_depth_probe_v2"));
        s.add(fill(stage, "~0 ~0 ~6", "~0 ~4 ~6", "minecraft:air"));

        // ── Distance comparison: beams crossing the view at about 5, 10 and 17 blocks ──
        s.add(testBeams(stage, new double[][] {{-8, 1.0, 4.5, 8, 1.0, 4.5}, {-8, 2.6, 10, 8, 2.6, 10}, {-8, 4.2, 17, 8, 4.2, 17}}));
        s.add(eyes(stage, 0, EYE, 0, 0, 2.4, 10));
        s.add(classic(true));
        s.add(settle());
        s.add(screenshot("16_distance_classic"));
        s.add(classic(false));
        s.add(settle());
        s.add(screenshot("17_distance_v2"));
        s.add(clearTestBeams());

        // ── Impact close-up: 2.5 blocks from the deepslate block, by day and night ──
        s.add(eyes(stage, 1.0, EYE, 3.6, 3, 1.3, 6));
        s.add(beam(true));
        s.add(HologramCapture.waitTicks(30));
        s.add(screenshot("18_impact_close"));
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.waitTicks(20));
        s.add(screenshot("19_impact_close_night"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.waitTicks(10));

        // ── Measurements: player beam (2 beams) plus 0 / 16 / 64 / 256 fan beams, V2 then classic ──
        s.add(eyes(stage, 0, EYE, 0, 0, 2.2, 18));
        for (boolean c : new boolean[] {false, true}) {
            s.add(classic(c));
            s.add(clearTestBeams());
            s.add(HologramCapture.waitTicks(20));
            s.add(measure("player beam", 100));
            for (int n : new int[] {16, 64, 256}) {
                s.add(fan(n));
                s.add(HologramCapture.waitTicks(20));
                s.add(measure("player beam + " + n + " test beams", 100));
            }
        }
        s.add(classic(false));
        s.add(fan(64));
        s.add(HologramCapture.waitTicks(5));
        s.add(screenshot("20_fan_64_v2"));
        s.add(clearTestBeams());

        // ── Clean-up: after the beam stops (and fades) Heat Vision leaves the emissive layer ──
        s.add(beam(false));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("Heat Vision left the Emissive Rendering Layer after the beam stopped",
                () -> EmissiveGlow.sourceCount() == 0));
        s.add(screenshot("21_after_stop"));
    }
}
