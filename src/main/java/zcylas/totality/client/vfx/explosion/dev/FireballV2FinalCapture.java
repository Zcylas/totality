package zcylas.totality.client.vfx.explosion.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.spell.destruction.FireballSpell;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.client.particle.fireball.FireballDetonationParticle;
import zcylas.totality.client.vfx.explosion.FireExplosionRenderer;
import zcylas.totality.client.vfx.glow.EmissiveGlow;
import zcylas.totality.client.vfx.projectile.FireballProjectileVfx;
import zcylas.totality.client.vfx.screen.ScreenFx;
import zcylas.totality.entity.magic.FireballParticleBudget;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.entity.magic.FireballVfx;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.List;
import java.util.Locale;
import java.util.Set;

import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.EYE;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.at;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.cam;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.camera;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.cast;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.clearFire;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.frames;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.isolated;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.legacy;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.me;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.onServer;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.sequence;
import static zcylas.totality.client.vfx.explosion.dev.FireballV2Capture.watch;

/**
 * The completed Fireball V2 (VFX Experiment 3, B3-B5) as one spell, for the opt-in capture run of scene 69 with
 * {@code -Dtotality.fireball.v2.final=true} (prefix {@code FB2_}): real casts through {@link FireballSpell#onActivate}
 * from cast to cleanup, V1 and V2 from the same cameras ({@link FireballV2Dev#setLegacy}), first and third person,
 * distances, wall / ground / air, day and night, camera near and inside, several projectiles and explosions, Screen FX
 * off, the Emissive Rendering Layer off, a simulated mid-flight sighting, repeated casts with cleanup checks, a
 * slow-motion explosion and the measurements. {@code -Dtotality.fireball.v2.projectileOnly=true} keeps only the
 * projectile look-development sequences. One frame per game tick ({@code /tick rate 4} while filming).
 */
final class FireballV2FinalCapture {

    private static final boolean PROJECTILE_ONLY = Boolean.getBoolean("totality.fireball.v2.projectileOnly");
    /** Optimisation runs ({@code -Dtotality.fireball.v2.perf=true}): only the measurements. */
    private static final boolean PERF_ONLY = Boolean.getBoolean("totality.fireball.v2.perf");

    private FireballV2FinalCapture() {}

    private static Step gamemode(String mode) {
        return HologramCapture.command("gamemode " + mode + " @s");
    }

    private static Step cameraType(CameraType type) {
        return HologramCapture.run("camera " + type, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    private static Step hudHidden(boolean hidden) {
        return HologramCapture.run("HUD hidden " + hidden, () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden() != hidden) hud.toggle();
        });
    }

    /** The player casts from where it stands toward {@code target} (no teleport: first and third person). */
    private static Step castHere(double[] stage, double[] target) {
        return castHere(stage, target, null);
    }

    /**
     * As {@link #castHere(double[], double[])}, then turns the player to look at {@code lookAfter} (when not null). In
     * third person the camera follows the look line, so a fireball flying straight along it stays hidden behind the
     * caster's head; looking a little aside after the cast (the spell has already taken its aim) shows the flight.
     */
    private static Step castHere(double[] stage, double[] target, double[] lookAfter) {
        return onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 eye = p.getEyePosition();
            camera(p, eye, at(stage, target[0], target[1], target[2]));
            new FireballSpell().onActivate(p, null);
            if (lookAfter != null) camera(p, eye, at(stage, lookAfter[0], lookAfter[1], lookAfter[2]));
        });
    }

    /**
     * One whole spell: camera, cast, {@code n} frames (one per tick), cleanup. Particles left by earlier casts (smoke of
     * the gameplay fire blocks, which are cleared between sequences) are removed first, so they do not drift through
     * the footage.
     */
    private static void spell(List<Step> s, double[] stage, String name, Step camera, Step cast, int n) {
        s.add(HologramCapture.command("tick rate 4"));
        s.add(camera);
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("clear leftover particles", () -> {
            Minecraft.getInstance().particleEngine.clearParticles();
            FireballParticleBudget.reset();
        }));
        s.add(cast);
        frames(s, name, n);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(30));
    }

    static void scenes(List<Step> s, double[] stage) {
        double[] origin = {0, EYE, 0};
        double[] floor12 = {0, 0, 12};
        double[] floor18 = {0, 0, 18};
        double[] wall = {0, EYE - 0.1, 24};
        double[] sideCam = {-7.0, 2.4, 8.0}, sideAt = {0, 1.2, 11.0};
        double[] groundCam = {-9.0, 5.0, 2.0}, groundAt = {-1.0, 0.3, 12.0};

        // Warm-up (shaders, sounds), not captured.
        s.add(legacy(false));
        s.add(cast(stage, origin, floor12));
        s.add(HologramCapture.waitTicks(60));
        s.add(clearFire(stage));
        if (PERF_ONLY) {
            measurements(s, stage);
            return;
        }

        // 1. The whole spell from behind the caster (third person, the caster visible): V2, then V1 for comparison.
        for (boolean v1 : PROJECTILE_ONLY ? new boolean[]{false} : new boolean[]{false, true}) {
            String v = v1 ? "v1" : "v2";
            s.add(legacy(v1));
            s.add(gamemode("creative"));
            s.add(cameraType(CameraType.THIRD_PERSON_BACK));
            spell(s, stage, v + "_spell_third_person", cam(stage, 0, EYE, 0, 1.5, 0.6, 16),
                    castHere(stage, new double[]{4, 0, 17}, new double[]{-3, 1.0, 17}), 92);
            s.add(cameraType(CameraType.FIRST_PERSON));
            s.add(gamemode("spectator"));
            // 2. From the side: the projectile passes about 7 blocks from the camera and lands 18 blocks out.
            spell(s, stage, v + "_spell_side", cam(stage, sideCam[0], sideCam[1], sideCam[2], sideAt[0], sideAt[1], sideAt[2]),
                    cast(stage, origin, floor18), 80);
            // 3. First person (HUD and hand visible) into the wall.
            s.add(gamemode("creative"));
            s.add(hudHidden(false));
            spell(s, stage, v + "_first_person", cam(stage, 0, EYE, 0, 0.3, EYE - 0.2, 24), castHere(stage, wall), 40);
            s.add(hudHidden(true));
            s.add(gamemode("spectator"));
        }
        s.add(legacy(false));

        // 4. Far: about 36 blocks from the impact.
        spell(s, stage, "v2_far", cam(stage, -14.0, 10.0, -10.0, 0, 1.0, 22), cast(stage, origin, wall), 50);

        // 5. Night, from the side: V2, then V1.
        s.add(HologramCapture.command("time set 18000"));
        spell(s, stage, "v2_night_side", cam(stage, sideCam[0], sideCam[1], sideCam[2], sideAt[0], sideAt[1], sideAt[2]),
                cast(stage, origin, floor18), 80);
        if (!PROJECTILE_ONLY) {
            s.add(legacy(true));
            spell(s, stage, "v1_night_side", cam(stage, sideCam[0], sideCam[1], sideCam[2], sideAt[0], sideAt[1], sideAt[2]),
                    cast(stage, origin, floor18), 80);
            s.add(legacy(false));
        }
        s.add(HologramCapture.command("time set 6000"));

        // 6. Simulated mid-flight sighting: a fireball cast by a far stand-in caster is moved into view before it is
        //    added to the world, so this client first sees it far from its caster (as a client that starts tracking a
        //    fireball mid-flight would). Expected: no cast burst, the projectile drawn complete at once.
        int[] bursts = {0};
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, sideCam[0], sideCam[1], sideCam[2], sideAt[0], sideAt[1], sideAt[2]));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("cast bursts before", () -> bursts[0] = FireballVfx.castBursts()));
        s.add(onServer(server -> {
            ServerLevel level = (ServerLevel) me(server).level();
            TotalityFakePlayer far = TotalityFakePlayer.create(level, "FireballFarCaster");
            camera(far, at(stage, 0, EYE, -30), at(stage, 0, EYE, 0));
            FireballProjectileEntity fireball = FireballProjectileEntity.create(level, far, 14);
            Vec3 start = at(stage, 0, EYE, 4);
            fireball.setPos(start.x, start.y, start.z);
            level.addFreshEntity(fireball);
        }));
        frames(s, "v2_midflight_sighting", 30);
        s.add(HologramCapture.check("a fireball first seen mid-flight plays no cast burst",
                () -> FireballVfx.castBursts() == bursts[0]));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(30));
        if (PROJECTILE_ONLY) return;

        // 7. Airborne impact: a floating target 9 blocks up.
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,
                "summon minecraft:armor_stand %.2f %.2f %.2f {NoGravity:1b,Invulnerable:1b,Tags:[\"fb2_target\"]}",
                stage[0], stage[1] + 9.0, stage[2] + 14)).tick(mc));
        sequence(s, stage, "v2_air", new double[]{-12.0, 6.0, 5.0}, new double[]{0, 8.5, 14}, origin, new double[]{0, 9.9, 14}, 60);
        s.add(HologramCapture.command("kill @e[type=minecraft:armor_stand,tag=fb2_target]"));

        // 8. Camera near (7 blocks) and inside (3 blocks), first person (spectator).
        sequence(s, stage, "v2_camera_near_7", new double[]{0, EYE, 5.0}, new double[]{0, 1.0, 12}, new double[]{0, EYE, 2}, floor12, 60);
        sequence(s, stage, "v2_camera_inside_3", new double[]{0, EYE, 9.0}, new double[]{0, 1.0, 12}, new double[]{0, EYE, 4}, floor12, 60);

        // 9. Several projectiles in flight (five casts in one tick, fanned), from behind and above.
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, -2.0, 7.5, -7.0, 0, 1.0, 16));
        s.add(HologramCapture.waitTicks(6));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            double[][] targets = {{-8, 0, 16}, {-3, 1.2, 24}, {0, 0, 10}, {3, 2.4, 24}, {8, 0, 18}};
            for (double[] t : targets) {
                camera(p, at(stage, t[0] * 0.15, EYE, 0), at(stage, t[0], t[1], t[2]));
                new FireballSpell().onActivate(p, null);
            }
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        frames(s, "v2_multi_flight", 60);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(30));

        // 10. Screen FX disabled (Totality shake 0, flash 0), ground view.
        s.add(HologramCapture.run("Screen FX: shake 0, flash 0", () -> {
            ScreenFx.settings().setShake(0.0f);
            ScreenFx.settings().setFlash(0.0f);
        }));
        sequence(s, stage, "v2_screenfx_off", groundCam, groundAt, origin, floor12, 50);
        s.add(HologramCapture.run("Screen FX: shake 1, flash 1", () -> {
            ScreenFx.settings().setShake(1.0f);
            ScreenFx.settings().setFlash(1.0f);
        }));

        // 11. The Emissive Rendering Layer disabled, at night (side view), then on again.
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.run("emissive layer off", () -> EmissiveGlow.settings().setEnabled(false)));
        spell(s, stage, "v2_night_side_layer_off", cam(stage, sideCam[0], sideCam[1], sideCam[2], sideAt[0], sideAt[1], sideAt[2]),
                cast(stage, origin, floor18), 80);
        s.add(HologramCapture.run("emissive layer on", () -> EmissiveGlow.settings().setEnabled(true)));
        s.add(HologramCapture.command("time set 6000"));

        // 12. Twenty real Fireballs at once (one tick), from above and behind.
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, -2.0, 11.0, -7.0, 0, 0, 15));
        s.add(HologramCapture.waitTicks(6));
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
        frames(s, "v2_twenty", 60);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(30));

        // 13. Repeated casts at real speed: ten Fireballs, one every half second, on the same spot; then everything
        //     must be gone within the aftermath (4 s) plus margin.
        double[] peak = new double[2];
        s.add(cam(stage, groundCam[0], groundCam[1], groundCam[2], groundAt[0], groundAt[1], groundAt[2]));
        s.add(HologramCapture.waitTicks(5));
        for (int i = 0; i < 10; i++) {
            s.add(cast(stage, origin, floor12));
            watch(s, "repeat_" + i, 10, peak);
        }
        s.add(HologramCapture.waitTicks(90));
        s.add(HologramCapture.check("repeat: no explosion left", () -> FireExplosionRenderer.lastExplosions() == 0));
        s.add(HologramCapture.check("repeat: no projectile tracked", () -> FireballProjectileVfx.trackedProjectiles() == 0));
        s.add(HologramCapture.check("repeat: no Fireball particle alive", () -> FireballParticleBudget.alive() == 0));
        s.add(HologramCapture.check("repeat: no Screen FX request left", () -> ScreenFx.liveRequests() == 0));
        s.add(HologramCapture.check("repeat: every detonation emitter finished", () -> FireballDetonationParticle.running() == 0));
        s.add(HologramCapture.check("repeat: no emissive source left", () -> EmissiveGlow.sourceCount() == 0));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // 14. Slow motion: the explosion at a quarter speed (client-only, front view; no projectile, no gameplay fire).
        isolated(s, stage, "v2_slowmo_explosion", new double[]{0, 3.0, -3.0}, new double[]{0, 2.5, 12}, 64, 0.25, false);

        measurements(s, stage);
    }

    /**
     * Measurements at real speed: twenty projectiles in flight (aimed up into the open sky, no impact), then one and
     * twenty looping explosions; GPU times of the explosion, the projectile and the emissive layer.
     */
    private static void measurements(List<Step> s, double[] stage) {
        s.add(cam(stage, -2.0, 6.0, -9.0, 0, 8.0, 20));
        s.add(HologramCapture.waitTicks(10));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            for (int i = 0; i < 20; i++) {
                double tx = (i % 5 - 2) * 6.0, ty = 30 + (i / 5) * 6.0;
                camera(p, at(stage, tx * 0.05, EYE, 0), at(stage, tx, ty, 60));
                new FireballSpell().onActivate(p, null);
            }
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        s.add(HologramCapture.waitTicks(4));
        measure(s, "twenty projectiles in flight", 50);
        s.add(HologramCapture.waitTicks(60));
        s.add(cam(stage, -2.0, 11.0, -7.0, 0, 0, 15));
        for (int count : new int[]{1, 20}) {
            s.add(HologramCapture.run("loop " + count, () -> FireballV2Dev.loop(count)));
            s.add(HologramCapture.waitTicks(30));
            measure(s, "loop " + count + " explosions", 120);
        }
        s.add(HologramCapture.run("loop off", () -> FireballV2Dev.loop(0)));
        s.add(HologramCapture.waitTicks(40));
    }

    /** Averages the GPU timers over {@code ticks} ticks at real speed and logs one {@code perf:} line. */
    private static void measure(List<Step> s, String label, int ticks) {
        double[] glow = new double[2];
        s.add(HologramCapture.run("reset measurements " + label, () -> {
            FireballV2Dev.resetMeasurements();
            glow[0] = glow[1] = 0;
        }));
        for (int i = 0; i < ticks; i++) {
            String sample = String.format(Locale.ROOT, "%s_t%03d", label.replace(' ', '_'), i);
            boolean logged = i % 20 == 0;
            s.add(mc -> {
                if (logged) HologramCapture.log(FireballV2Capture.fx(sample));
                long ns = EmissiveGlow.lastGpuNanos();
                if (ns > 0) {
                    glow[0] += ns;
                    glow[1]++;
                }
                return true;
            });
        }
        s.add(HologramCapture.run("perf " + label, () -> HologramCapture.log(String.format(Locale.ROOT,
                "perf: %s, real speed, %d explosion GPU samples, %d projectile GPU samples: %s; emissive layer GPU %s (%d samples); fps %d",
                label, FireExplosionRenderer.gpuSamples(), FireballProjectileVfx.gpuSamples(), FireballV2Dev.stats(),
                glow[1] == 0 ? "n/a" : String.format(Locale.ROOT, "%.3f ms", glow[0] / glow[1] / 1.0e6), (int) glow[1],
                Minecraft.getInstance().getFps()))));
    }
}
