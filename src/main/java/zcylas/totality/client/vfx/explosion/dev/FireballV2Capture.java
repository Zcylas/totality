package zcylas.totality.client.vfx.explosion.dev;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.spell.destruction.FireballSpell;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.particle.fireball.FireballDetonationParticle;
import zcylas.totality.client.particle.fireball.FireballV2;
import zcylas.totality.client.vfx.explosion.FireExplosionRenderer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.projectile.FireballProjectileVfx;
import zcylas.totality.client.vfx.screen.ScreenFx;
import zcylas.totality.client.vfx.screen.ScreenFxFrame;
import zcylas.totality.entity.magic.FireballParticleBudget;
import zcylas.totality.entity.magic.FireballVfx;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fireball V2 phase B2 footage and measurements for the opt-in development capture run (scene 69, prefix
 * {@code FB2_}); inert in normal play. Casts go through {@link FireballSpell#onActivate}, so the real projectile, the
 * server's explosion centre (delivered by the unchanged explode packet) and its unchanged damage and ignition are used.
 * V1 and V2 are captured in the same run from the same camera positions ({@code setLegacyExplosion}). The camera
 * player is a spectator whenever a blast can reach it. One frame per game tick; {@code /tick rate 4} slows the world
 * (and the client tick) so each frame is a distinct tick. Every frame logs the renderer and Screen FX state.
 *
 * <p>{@code -Dtotality.fireball.v2.quick=true} runs a short look-development subset;
 * {@code -Dtotality.fireball.v2.refine=true} runs the phase B2.1 comparison set (the same set before and after the
 * refinement): real casts, isolated explosions, the development radius marker, a 4× slow-motion expansion and the
 * 1 / 20 explosion measurements.
 */
final class FireballV2Capture {

    private FireballV2Capture() {}

    static final double EYE = 1.62;
    private static final boolean QUICK = Boolean.getBoolean("totality.fireball.v2.quick");
    private static final boolean REFINE = Boolean.getBoolean("totality.fireball.v2.refine");
    private static final boolean FINAL = Boolean.getBoolean("totality.fireball.v2.final");

    static Step onServer(Consumer<MinecraftServer> action) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server));
            return true;
        };
    }

    static ServerPlayer me(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!(p instanceof TotalityFakePlayer)) return p;
        return server.getPlayerList().getPlayers().getFirst();
    }

    static void camera(ServerPlayer p, Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
    }

    static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    static Step cam(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> camera(me(server), at(stage, ex, ey, ez), at(stage, lx, ly, lz)));
    }

    /** The player casts from {@code from} toward {@code target} through the spell, then returns to the camera spot. */
    static Step cast(double[] stage, double[] from, double[] target) {
        return onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            camera(p, at(stage, from[0], from[1], from[2]), at(stage, target[0], target[1], target[2]));
            new FireballSpell().onActivate(p, null);
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        });
    }

    static Step legacy(boolean on) {
        return HologramCapture.run(on ? "Fireball: V1 (legacy projectile and detonation)" : "Fireball: V2", () -> FireballV2Dev.setLegacy(on));
    }

    static Step clearFire(double[] stage) {
        return onServer(server -> {
            ServerLevel level = (ServerLevel) me(server).level();
            BlockPos corner = BlockPos.containing(stage[0], stage[1], stage[2]);
            for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-16, -2, -12), corner.offset(18, 14, 34))) {
                if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        });
    }

    static String fx(String label) {
        ScreenFxFrame f = ScreenFx.lastFrame();
        return String.format(Locale.ROOT, "fx: %s explosions=%d elements=%d draws=%d vbKiB=%d particles=%s shake=%.3f flash=%.3f sfxLive=%d "
                        + "projectiles=%d tracked=%d fbParticles=%d glowDemand=%.2f glowScale=%.2f castBursts=%d",
                label, FireExplosionRenderer.lastExplosions(), FireExplosionRenderer.lastElements(), FireExplosionRenderer.lastDraws(),
                FireExplosionRenderer.vertexBufferBytes() / 1024, Minecraft.getInstance().particleEngine.countParticles(),
                f.shake(), f.flash(), ScreenFx.liveRequests(), FireballProjectileVfx.lastProjectiles(),
                FireballProjectileVfx.trackedProjectiles(), FireballParticleBudget.alive(),
                EmissiveGlow.budget().groupDemand(FireballV2.GLOW_GROUP), EmissiveGlow.budget().groupScale(FireballV2.GLOW_GROUP),
                FireballVfx.castBursts());
    }

    /** {@code n} frames, one per tick, each also logging the renderer and Screen FX state. */
    static void frames(List<Step> s, String name, int n) {
        for (int i = 0; i < n; i++) {
            String shot = String.format(Locale.ROOT, "FB2_%s_%02d", name, i);
            Step screenshot = HologramCapture.screenshot(shot);
            s.add(mc -> {
                HologramCapture.log(fx(shot));
                return screenshot.tick(mc);
            });
        }
    }

    /** Logs per-tick state without screenshots (Screen FX checks). */
    static void watch(List<Step> s, String name, int n, double[] peak) {
        for (int i = 0; i < n; i++) {
            String label = String.format(Locale.ROOT, "%s_t%02d", name, i);
            s.add(mc -> {
                ScreenFxFrame f = ScreenFx.lastFrame();
                peak[0] = Math.max(peak[0], f.shake());
                peak[1] = Math.max(peak[1], f.flash());
                HologramCapture.log(fx(label));
                return true;
            });
        }
        s.add(HologramCapture.run("summary " + name, () -> HologramCapture.log(String.format(Locale.ROOT,
                "fx-summary: %s peak shake %.3f, peak flash %.3f", name, peak[0], peak[1]))));
    }

    static Step fill(double[] stage, String region, String block) {
        return mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill %s %s",
                stage[0], stage[1], stage[2], region, block)).tick(mc);
    }

    /** A sequence: camera, a cast, then {@code n} frames, cleanup. */
    static void sequence(List<Step> s, double[] stage, String name, double[] camEye, double[] camAt, double[] from,
                                 double[] target, int n) {
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, camEye[0], camEye[1], camEye[2], camAt[0], camAt[1], camAt[2]));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, from, target));
        frames(s, name, n);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(30));
    }

    /** A client-only Fireball V2 explosion on the floor at z = 12 (development look check; not a cast). */
    static void isolated(List<Step> s, double[] stage, double[] camEye, double[] camAt) {
        isolated(s, stage, "v2_ground_isolated", camEye, camAt, 34, 1.0, false);
    }

    /**
     * A client-only explosion on the floor at z = 12, optionally played at {@code timeScale} × speed (slow motion) and
     * with the development radius marker.
     */
    static void isolated(List<Step> s, double[] stage, String name, double[] camEye, double[] camAt, int n,
                                 double timeScale, boolean marker) {
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, camEye[0], camEye[1], camEye[2], camAt[0], camAt[1], camAt[2]));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("client-only explosion, time scale " + timeScale + ", marker " + marker, () -> {
            // Particles left by the previous casts (debris, fire-block smoke) would draw over the fire.
            Minecraft.getInstance().particleEngine.clearParticles();
            FireballParticleBudget.reset();
            FireExplosionRenderer.setTimeScale(timeScale);
            FireballV2Dev.setMarker(marker);
            FireballV2Dev.spawn(at(stage, 0, 0.25, 12));
        }));
        frames(s, name, n);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.run("time scale 1, marker off", () -> {
            FireExplosionRenderer.setTimeScale(1.0);
            FireballV2Dev.setMarker(false);
        }));
    }

    /** Phase B2.1 comparison set: identical before and after the refinement. */
    private static void refineSet(List<Step> s, double[] stage, double[] origin, double[] floor12, double[] wall,
                                  double[] groundCam, double[] groundAt, double[] wallCam, double[] wallAt) {
        double[] highCam = {7.0, 12.0, 3.0}, highAt = {0, 0, 12};
        double[] topCam = {0.5, 17.0, 4.5}, topAt = {0, 0, 12};
        double[] frontCam = {0, 3.0, -3.0}, frontAt = {0, 2.5, 12};
        sequence(s, stage, "v2_ground", groundCam, groundAt, origin, floor12, 44);
        isolated(s, stage, "v2_ground_isolated", groundCam, groundAt, 34, 1.0, false);
        isolated(s, stage, "v2_marker_front", frontCam, frontAt, 34, 1.0, true);
        isolated(s, stage, "v2_marker_top", topCam, topAt, 34, 1.0, true);
        isolated(s, stage, "v2_slowmo_marker", frontCam, frontAt, 40, 0.25, true);
        isolated(s, stage, "v2_slowmo", groundCam, groundAt, 40, 0.25, false);
        sequence(s, stage, "v2_wall", wallCam, wallAt, new double[]{0, EYE, 8}, wall, 44);
        sequence(s, stage, "v2_high_angle", highCam, highAt, origin, floor12, 44);
        s.add(HologramCapture.command("time set 18000"));
        sequence(s, stage, "v2_night", groundCam, groundAt, origin, floor12, 44);
        s.add(HologramCapture.command("time set 6000"));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,
                "summon minecraft:armor_stand %.2f %.2f %.2f {NoGravity:1b,Invulnerable:1b,Tags:[\"fb2_target\"]}",
                stage[0], stage[1] + 9.0, stage[2] + 14)).tick(mc));
        sequence(s, stage, "v2_air", new double[]{-12.0, 6.0, 5.0}, new double[]{0, 8.5, 14}, origin, new double[]{0, 9.9, 14}, 40);
        s.add(HologramCapture.command("kill @e[type=minecraft:armor_stand,tag=fb2_target]"));
        sequence(s, stage, "v2_camera_near_7", new double[]{0, EYE, 5.0}, new double[]{0, 1.0, 12}, new double[]{0, EYE, 2}, floor12, 36);
        sequence(s, stage, "v2_camera_inside_3", new double[]{0, EYE, 9.0}, new double[]{0, 1.0, 12}, new double[]{0, EYE, 4}, floor12, 36);
        isolated(s, stage, "v2_camera_inside_isolated", new double[]{0, EYE, 9.0}, new double[]{0, 1.0, 12}, 30, 1.0, false);
    }

    /** Game rules, a flat stone-brick range with a wall and a bunker, timing on, Screen FX defaults, HUD hidden. */
    static void setup(List<Step> s, double[] stage) {
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("remember the stage; timing on; Screen FX defaults", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
            FireballV2Dev.setTiming(true);
            ScreenFx.settings().setShake(1.0f);
            ScreenFx.settings().setFlash(1.0f);
        }));
        // Flat stone-brick range along +z (split fills), a deepslate wall at z = 24, a stone bunker at x 9..15, z 8..16
        // with a doorway on its -x side.
        for (int[] z : new int[][]{{-12, 3}, {4, 18}, {19, 34}}) {
            s.add(fill(stage, String.format(Locale.ROOT, "~-16 ~ ~%d ~18 ~14 ~%d", z[0], z[1]), "minecraft:air"));
            s.add(fill(stage, String.format(Locale.ROOT, "~-16 ~-1 ~%d ~18 ~-1 ~%d", z[0], z[1]), "minecraft:stone_bricks"));
        }
        s.add(fill(stage, "~-6 ~ ~24 ~6 ~6 ~24", "minecraft:polished_deepslate"));
        s.add(fill(stage, "~9 ~ ~8 ~15 ~4 ~16", "minecraft:stone outline"));
        s.add(fill(stage, "~9 ~ ~11 ~9 ~2 ~13", "minecraft:air"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.run("hide HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (!hud.isHidden()) hud.toggle();
        }));
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        setup(s, stage);
        if (FINAL) {
            FireballV2FinalCapture.scenes(s, stage);
            cleanupChecks(s);
            return s;
        }
        double[] origin = {0, EYE, 0};
        double[] floor12 = {0, 0, 12};
        double[] wall = {0, EYE - 0.1, 24};
        double[] groundCam = {-9.0, 5.0, 2.0}, groundAt = {-1.0, 0.3, 12.0};
        double[] wallCam = {6.5, 3.2, 16.0}, wallAt = {0, 1.8, 23.5};

        // Warm-up (shaders, sounds), not captured.
        s.add(legacy(false));
        s.add(cast(stage, origin, floor12));
        s.add(HologramCapture.waitTicks(60));
        s.add(clearFire(stage));

        if (QUICK) {
            // Look-development run (-Dtotality.fireball.v2.quick=true): isolated explosions only (no casts).
            isolated(s, stage, groundCam, groundAt);
            isolated(s, stage, "v2_slowmo_marker", new double[]{0, 3.0, -3.0}, new double[]{0, 2.5, 12}, 40, 0.25, true);
            isolated(s, stage, "v2_marker_top", new double[]{0.5, 17.0, 4.5}, new double[]{0, 0, 12}, 34, 1.0, true);
            return s;
        }
        if (REFINE) {
            refineSet(s, stage, origin, floor12, wall, groundCam, groundAt, wallCam, wallAt);
            measureAndClean(s, stage);
            return s;
        }

        // 1/2. Ground impact: V1 then V2, same camera.
        s.add(legacy(true));
        sequence(s, stage, "v1_ground", groundCam, groundAt, origin, floor12, 44);
        s.add(legacy(false));
        sequence(s, stage, "v2_ground", groundCam, groundAt, origin, floor12, 44);

        // 2b. The same explosion alone (client-only test explosion at the same point: no projectile, no gameplay
        //     ignition, no damage), to judge the effect without the vanilla fire blocks the real cast places.
        isolated(s, stage, groundCam, groundAt);

        // 3. Wall impact: V1 then V2.
        s.add(legacy(true));
        sequence(s, stage, "v1_wall", wallCam, wallAt, new double[]{0, EYE, 8}, wall, 44);
        s.add(legacy(false));
        sequence(s, stage, "v2_wall", wallCam, wallAt, new double[]{0, EYE, 8}, wall, 44);

        // 4. Another viewing angle (high, from the side).
        sequence(s, stage, "v2_high_angle", new double[]{7.0, 12.0, 3.0}, new double[]{0, 0, 12}, origin, floor12, 44);

        // 5. Open air: a fireball hits a floating target 9 blocks up (no ground within the radius).
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,
                "summon minecraft:armor_stand %.2f %.2f %.2f {NoGravity:1b,Invulnerable:1b,Tags:[\"fb2_target\"]}",
                stage[0], stage[1] + 9.0, stage[2] + 14)).tick(mc));
        sequence(s, stage, "v2_air", new double[]{-12.0, 6.0, 5.0}, new double[]{0, 8.5, 14}, origin, new double[]{0, 9.9, 14}, 40);
        s.add(HologramCapture.command("kill @e[type=minecraft:armor_stand,tag=fb2_target]"));

        // 6. Confined: into the stone bunker through its doorway.
        sequence(s, stage, "v2_bunker", new double[]{5.0, 3.5, 6.5}, new double[]{11.0, 1.5, 12.0},
                new double[]{3.0, EYE, 12.0}, new double[]{12.5, 1.0, 12.0}, 40);

        // 7. Night: V1 then V2.
        s.add(HologramCapture.command("time set 18000"));
        s.add(legacy(true));
        sequence(s, stage, "v1_night", groundCam, groundAt, origin, floor12, 44);
        s.add(legacy(false));
        sequence(s, stage, "v2_night", groundCam, groundAt, origin, floor12, 44);
        // Emissive contribution off, same night view (to judge the explosion with and without bloom).
        s.add(HologramCapture.run("emissive off", () -> FireExplosionRenderer.setEmissiveEnabled(false)));
        sequence(s, stage, "v2_night_no_emissive", groundCam, groundAt, origin, floor12, 44);
        s.add(HologramCapture.run("emissive on", () -> FireExplosionRenderer.setEmissiveEnabled(true)));
        s.add(HologramCapture.command("time set 6000"));

        // 8. Camera near (7 blocks) and inside (3 blocks) the sphere, first person (spectator).
        sequence(s, stage, "v2_camera_near_7", new double[]{0, EYE, 5.0}, new double[]{0, 1.0, 12}, new double[]{0, EYE, 2}, floor12, 36);
        sequence(s, stage, "v2_camera_inside_3", new double[]{0, EYE, 9.0}, new double[]{0, 1.0, 12}, new double[]{0, EYE, 4}, floor12, 36);

        // 9. Screen FX disabled (Totality shake 0, flash 0), ground view.
        s.add(HologramCapture.run("Screen FX: shake 0, flash 0", () -> {
            ScreenFx.settings().setShake(0.0f);
            ScreenFx.settings().setFlash(0.0f);
        }));
        sequence(s, stage, "v2_ground_screenfx_off", groundCam, groundAt, origin, floor12, 44);
        s.add(HologramCapture.run("Screen FX: shake 1, flash 1", () -> {
            ScreenFx.settings().setShake(1.0f);
            ScreenFx.settings().setFlash(1.0f);
        }));

        // 10. Screen FX integration at real speed: nearby (camera 7 blocks away), distant (~45 blocks), behind the camera.
        double[] peak = new double[2];
        s.add(cam(stage, 0, EYE, 5.0, 0, 1.0, 12));
        s.add(HologramCapture.waitTicks(5));
        s.add(HologramCapture.run("reset peaks", () -> peak[0] = peak[1] = 0));
        s.add(cast(stage, new double[]{0, EYE, 2}, floor12));
        watch(s, "sfx_nearby_7", 24, peak);
        s.add(clearFire(stage));
        s.add(cam(stage, 0, 20.0, -30.0, 0, 0, 12));
        s.add(HologramCapture.waitTicks(5));
        s.add(HologramCapture.run("reset peaks", () -> peak[0] = peak[1] = 0));
        s.add(cast(stage, new double[]{0, EYE, 2}, floor12));
        watch(s, "sfx_distant_45", 24, peak);
        s.add(clearFire(stage));
        s.add(cam(stage, 0, EYE, 6.0, 0, EYE, -10));
        s.add(HologramCapture.waitTicks(5));
        s.add(HologramCapture.run("reset peaks", () -> peak[0] = peak[1] = 0));
        s.add(cast(stage, new double[]{0, EYE, 2}, floor12));
        watch(s, "sfx_behind_camera_6", 24, peak);
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // 11. Twenty real Fireballs at once (one tick), from above and behind; frames + per-tick state.
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, -2.0, 11.0, -7.0, 0, 0, 15));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("reset measurements", FireExplosionRenderer::resetMeasurements));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            for (int i = 0; i < 20; i++) {
                double tx = (i % 5 - 2) * 4.0, tz = 9 + (i / 5) * 4.0;
                camera(p, at(stage, tx * 0.1, EYE, 0), at(stage, tx, 0, tz));
                new FireballSpell().onActivate(p, null);
            }
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        frames(s, "v2_twenty", 44);
        s.add(HologramCapture.run("twenty summary", () -> HologramCapture.log("perf: twenty real casts (tick rate 4): " + FireballV2Dev.stats())));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(30));

        measureAndClean(s, stage);
        return s;
    }

    /** Measurement at real speed (1 and 20 looping client-only explosions, 120 ticks' GPU average each), then cleanup checks. */
    static void measureAndClean(List<Step> s, double[] stage) {
        s.add(cam(stage, -2.0, 11.0, -7.0, 0, 0, 15));
        for (int count : new int[]{1, 20}) {
            s.add(HologramCapture.run("loop " + count, () -> FireballV2Dev.loop(count)));
            s.add(HologramCapture.waitTicks(30));
            s.add(HologramCapture.run("reset measurements", FireExplosionRenderer::resetMeasurements));
            s.add(HologramCapture.waitTicks(120));
            s.add(HologramCapture.run("perf " + count, () -> HologramCapture.log(String.format(Locale.ROOT,
                    "perf: loop %d explosions, real speed, %d GPU samples: %s, fps %d", count, FireExplosionRenderer.gpuSamples(),
                    FireballV2Dev.stats(), Minecraft.getInstance().getFps()))));
        }
        s.add(HologramCapture.run("loop off", () -> FireballV2Dev.loop(0)));
        s.add(HologramCapture.waitTicks(40));
        cleanupChecks(s);
    }

    /** Nothing of any Fireball is left (explosions, projectiles, Screen FX, emitters), then development state restored. */
    static void cleanupChecks(List<Step> s) {
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.check("cleanup: no projectile tracked", () -> FireballProjectileVfx.trackedProjectiles() == 0));
        s.add(HologramCapture.check("cleanup: no explosion left", () -> FireExplosionRenderer.lastExplosions() == 0));
        s.add(HologramCapture.check("cleanup: no Screen FX request left", () -> ScreenFx.liveRequests() == 0));
        s.add(HologramCapture.check("detonation emitters all finished", () -> FireballDetonationParticle.running() == 0));
        s.add(HologramCapture.run("restore", () -> {
            FireballV2Dev.setLegacy(false);
            FireballV2Dev.setTiming(false);
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
    }
}
