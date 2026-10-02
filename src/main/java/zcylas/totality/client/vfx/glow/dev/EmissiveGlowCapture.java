package zcylas.totality.client.vfx.glow.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.vfx.glow.EmissiveGlow;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * VFX Experiment 1 footage and measurements for the opt-in development capture run (scene 66, prefix {@code GLW_});
 * inert in normal play. Stages snow, lava, glowstone, a sea lantern and a white wall (bright vanilla surfaces that must
 * NOT glow) behind client-only test objects, then photographs the glow off/on, at several settings, by night and behind
 * an occluder, and logs GPU timings of the glow passes for 1-400 sources and 2-6 levels.
 * {@code -Dtotality.vfx.capture.tag=<tag>} labels a run (e.g. the backend).
 */
final class EmissiveGlowCapture {

    private static final double EYE = 1.62;
    private static final String TAG = System.getProperty("totality.vfx.capture.tag", "");

    private EmissiveGlowCapture() {}

    private static String shot(String name) {
        return "GLW_" + (TAG.isEmpty() ? "" : TAG + "_") + name;
    }

    private static Step screenshot(String name) {
        return HologramCapture.screenshot(shot(name));
    }

    private static Step settle() {
        return HologramCapture.waitTicks(8);
    }

    private static Step glow(boolean on, float intensity, int levels) {
        return HologramCapture.run(String.format(Locale.ROOT, "glow %s intensity %.2f levels %d", on ? "on" : "off", intensity, levels), () -> {
            EmissiveGlow.settings().setEnabled(on);
            EmissiveGlow.settings().setIntensity(intensity);
            EmissiveGlow.settings().setLevels(levels);
        });
    }

    private static Step place(int count) {
        return HologramCapture.run("place " + count + " test objects", () -> EmissiveGlowDevCommand.placeInFront(count));
    }

    private static Step clear() {
        return HologramCapture.run("clear test objects", EmissiveTestScene.INSTANCE::clear);
    }

    private static ServerPlayer me(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Puts the camera at {@code stage + eye offset} looking at {@code stage + look}. */
    private static Step camera(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server == null) return true;
            Vec3 eye = new Vec3(stage[0] + ex, stage[1] + ey, stage[2] + ez);
            Vec3 at = new Vec3(stage[0] + lx, stage[1] + ly, stage[2] + lz);
            double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            server.execute(() -> {
                ServerPlayer p = me(server);
                p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
            });
            return true;
        };
    }

    private static Step fill(double[] stage, String from, String to, String block) {
        return mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill %s %s %s",
                stage[0], stage[1], stage[2], from, to, block)).tick(mc);
    }

    /** Samples the GPU time of the glow passes and the frame rate for {@code ticks} ticks, then logs the averages. */
    private static Step measure(String label, int ticks) {
        long[] sum = {0};
        int[] n = {0};
        int[] fps = {0};
        int[] t = {0};
        return mc -> {
            long ns = EmissiveGlow.lastGpuNanos();
            if (ns > 0) {
                sum[0] += ns;
                n[0]++;
            }
            fps[0] += mc.getFps();
            if (++t[0] < ticks) return false;
            int w = mc.gameRenderer.mainRenderTarget().width, h = mc.gameRenderer.mainRenderTarget().height;
            HologramCapture.log(String.format(Locale.ROOT, "timing: %s | %dx%d | %d quads | glow GPU %s | fps avg %d (%d samples)",
                    label, w, h, EmissiveGlow.lastQuadCount(),
                    n[0] == 0 ? "n/a" : String.format(Locale.ROOT, "%.3f ms", sum[0] / (double) n[0] / 1.0e6),
                    fps[0] / t[0], n[0]));
            return true;
        };
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("gamerule advance_time false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("hide HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (!hud.isHidden()) hud.toggle();
        }));
        s.add(HologramCapture.run("remember the stage", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY()) + 20;
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A floating 17 x 18 stage 20 blocks above spawn: snow floor, lava pool (left), glowstone and a sea lantern
        // (right), white concrete back wall: every one of them is bright and must not glow.
        s.add(fill(stage, "~-8 ~-2 ~-3", "~8 ~8 ~14", "minecraft:air"));
        s.add(fill(stage, "~-8 ~-2 ~-3", "~8 ~-2 ~14", "minecraft:snow_block"));
        s.add(fill(stage, "~-7 ~-2 ~4", "~-4 ~-2 ~8", "minecraft:lava"));
        s.add(fill(stage, "~4 ~-1 ~6", "~5 ~-1 ~7", "minecraft:glowstone"));
        s.add(fill(stage, "~4 ~0 ~6", "~4 ~0 ~6", "minecraft:sea_lantern"));
        s.add(fill(stage, "~-8 ~-1 ~12", "~8 ~6 ~12", "minecraft:white_concrete"));
        s.add(camera(stage, 0, EYE - 1.0, 0, 0, EYE - 1.3, 4));
        s.add(HologramCapture.waitTicks(40));
        s.add(glow(true, 1.0f, 5));
        s.add(HologramCapture.run("GPU timing on", () -> EmissiveGlow.setTiming(true)));

        // ── 1. Baseline, normal, emissive, restored ──
        s.add(screenshot("01_baseline_no_sources"));
        s.add(place(4));
        s.add(glow(false, 1.0f, 5));
        s.add(settle());
        s.add(screenshot("02_normal_glow_off"));
        s.add(glow(true, 1.0f, 5));
        s.add(settle());
        s.add(screenshot("03_emissive_glow_on"));
        s.add(HologramCapture.check("glow layer holds buffers while sources glow", EmissiveGlow::holdsBuffers));

        // ── 2. Settings ──
        s.add(glow(true, 0.5f, 5));
        s.add(settle());
        s.add(screenshot("04_intensity_0_5"));
        s.add(glow(true, 2.5f, 5));
        s.add(settle());
        s.add(screenshot("05_intensity_2_5"));
        s.add(glow(true, 1.0f, 2));
        s.add(settle());
        s.add(screenshot("06_levels_2"));
        s.add(glow(true, 1.0f, 6));
        s.add(settle());
        s.add(screenshot("07_levels_6"));
        s.add(glow(true, 0.0f, 5));
        s.add(settle());
        s.add(screenshot("08_intensity_0"));

        // ── 3. Night ──
        s.add(HologramCapture.command("time set 18000"));
        s.add(glow(false, 1.0f, 5));
        s.add(HologramCapture.waitTicks(20));
        s.add(screenshot("09_night_glow_off"));
        s.add(glow(true, 1.0f, 5));
        s.add(settle());
        s.add(screenshot("10_night_glow_on"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.waitTicks(10));

        // ── 4. Occlusion: a stone pillar in front of the second object hides it and its glow ──
        s.add(fill(stage, "~-1 ~-1 ~2", "~-1 ~2 ~2", "minecraft:stone"));
        s.add(settle());
        s.add(screenshot("11_occluded_glow_on"));
        s.add(fill(stage, "~-1 ~-1 ~2", "~-1 ~2 ~2", "minecraft:air"));

        // ── 5. Restored: no sources again, must match the baseline ──
        s.add(clear());
        s.add(settle());
        s.add(screenshot("12_restored_no_sources"));

        // ── 6. Measurements (GPU timestamps around the glow passes; fps for context) ──
        s.add(measure("0 sources (layer idle)", 60));
        for (int count : new int[] {1, 16, 100, 400}) {
            s.add(place(count));
            s.add(HologramCapture.waitTicks(20));
            s.add(measure(count + " sources, levels 5", 100));
        }
        s.add(screenshot("13_grid_400_sources"));
        for (int levels = 2; levels <= 6; levels++) {
            s.add(glow(true, 1.0f, levels));
            s.add(place(16));
            s.add(HologramCapture.waitTicks(20));
            s.add(measure("16 sources, levels " + levels, 100));
        }
        s.add(glow(true, 1.0f, 5));
        s.add(place(16));
        s.add(glow(false, 1.0f, 5));
        s.add(HologramCapture.waitTicks(20));
        s.add(measure("16 sources, glow OFF", 100));
        s.add(glow(true, 1.0f, 5));

        // ── 7. Release: with nothing to draw the buffers are freed ──
        s.add(clear());
        s.add(HologramCapture.until("glow buffers released when idle", () -> !EmissiveGlow.holdsBuffers(), () -> { }, 400));
        s.add(HologramCapture.check("glow buffers released when idle", () -> !EmissiveGlow.holdsBuffers()));
        s.add(HologramCapture.run("show HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
        s.add(HologramCapture.command("gamemode creative @s"));
        return s;
    }
}
