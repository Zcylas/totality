package zcylas.totality.client.hologram.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.spell.destruction.FireballSpell;
import zcylas.totality.client.particle.fireball.FireballDetonationParticle;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.magic.FireballProjectileEntity;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Fireball VFX footage for the opt-in development capture run (scene 58, prefix {@code FBL_}); inert in normal play.
 * Casts go through {@link FireballSpell#onActivate}, the method the spell system calls, so the real projectile, its
 * detonation and its ignition are shown. The camera player is a spectator whenever a blast can reach it (spectators
 * take no explosion knockback). {@code /tick rate} slows the world (one frame per tick, the client slows with it).
 * {@code -Dtotality.fireball.capture.tag=<tag>} labels a run (e.g. original, for the before/after comparison).
 */
final class FireballCapture {

    private FireballCapture() {}

    private static final double EYE = 1.62;
    private static final String TAG = System.getProperty("totality.fireball.capture.tag", "");

    private static String shot(String name) {
        return "FBL_" + (TAG.isEmpty() ? "" : TAG + "_") + name;
    }

    private static void frames(List<Step> s, String name, int n) {
        for (int i = 0; i < n; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "%s_%02d", name, i))));
    }

    private static Step onServer(Consumer<MinecraftServer> action) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server));
            return true;
        };
    }

    private static ServerPlayer me(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) if (!(p instanceof TotalityFakePlayer)) return p;
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void camera(ServerPlayer p, Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
    }

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static Step cam(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> camera(me(server), at(stage, ex, ey, ez), at(stage, lx, ly, lz)));
    }

    /** The player casts from {@code from} toward {@code target}, then (optionally) returns to the camera spot. */
    private static Step cast(double[] stage, double[] from, double[] target, boolean returnToCamera) {
        return onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            camera(p, at(stage, from[0], from[1], from[2]), at(stage, target[0], target[1], target[2]));
            new FireballSpell().onActivate(p, null);
            if (returnToCamera) p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        });
    }

    private static Step hud(boolean hidden) {
        return HologramCapture.run((hidden ? "hide" : "show") + " HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden() != hidden) hud.toggle();
        });
    }

    private static Step cameraType(CameraType type) {
        return HologramCapture.run("camera " + type, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    /** Fireball entities left on the client and on the integrated server (both must reach 0 after each sequence). */
    private static Step count(String label) {
        return mc -> {
            int client = mc.level.getEntitiesOfClass(FireballProjectileEntity.class, mc.player.getBoundingBox().inflate(160)).size();
            MinecraftServer server = mc.getSingleplayerServer();
            int onServer = server == null ? -1 : server.submit(() -> me(server).level().getEntitiesOfClass(
                    FireballProjectileEntity.class, me(server).getBoundingBox().inflate(160)).size()).join();
            HologramCapture.log("info: " + label + ": " + client + " fireball entities on the client, " + onServer + " on the server; "
                    + FireballDetonationParticle.created() + " detonation emitters created so far, " + FireballDetonationParticle.running() + " still running");
            return true;
        };
    }

    /** The local player's health plus absorption, and remaining burn ticks, on the integrated server (logged). */
    private static Step health(String label) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            float hp = server == null ? -1 : server.submit(() -> me(server).getHealth() + me(server).getAbsorptionAmount()).join();
            int fire = server == null ? -1 : server.submit(() -> me(server).getRemainingFireTicks()).join();
            HologramCapture.log(String.format(Locale.ROOT, "info: %s: health + absorption %.1f, burning %d ticks", label, hp, fire));
            return true;
        };
    }

    /** Fireball ignites the area: clear the stage's fire between sequences. */
    private static Step clearFire(double[] stage) {
        return onServer(server -> {
            ServerLevel level = (ServerLevel) me(server).level();
            BlockPos corner = BlockPos.containing(stage[0], stage[1], stage[2]);
            for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-16, -2, -8), corner.offset(16, 10, 34))) {
                if (level.getBlockState(pos).getBlock() instanceof BaseFireBlock) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        });
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("remember the stage", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A 33 x 45 stone-brick range along +z (fills split under the 32768-block limit), a deepslate target wall at
        // z = +24 (the blast radius is 6, so it stays clear of the caster), plains biome for green surroundings.
        for (int[] z : new int[][]{{-10, 5}, {6, 20}, {21, 34}}) {
            s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill ~-16 ~ ~%d ~16 ~14 ~%d minecraft:air",
                    stage[0], stage[1], stage[2], z[0], z[1])).tick(mc));
            s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill ~-16 ~-1 ~%d ~16 ~-1 ~%d minecraft:stone_bricks",
                    stage[0], stage[1], stage[2], z[0], z[1])).tick(mc));
        }
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill ~-6 ~ ~24 ~6 ~6 ~24 minecraft:polished_deepslate",
                stage[0], stage[1], stage[2])).tick(mc));
        s.add(HologramCapture.waitTicks(10));

        double[] origin = {0, EYE, 0};
        double[] wall = {0, EYE - 0.1, 24};
        s.add(hud(true));
        // Warm-up cast (loads sounds and textures), not captured.
        s.add(cast(stage, origin, wall, true));
        s.add(HologramCapture.waitTicks(60));
        s.add(clearFire(stage));

        // ── 1. Side view, slowed (tick rate 4): ignition, the whole flight, detonation, lingering ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, 9.5, 2.6, 12.0, 0, 1.6, 12.0));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, true));
        frames(s, "side_slow", 64);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));
        s.add(count("after the side view"));

        // ── 2. Cast ignition close (slowed): the caster's hand side, as another player sees it ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, 2.8, 2.2, 1.4, 0, 1.6, 2.0));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, true));
        frames(s, "cast_close", 14);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.waitTicks(40));
        s.add(clearFire(stage));

        // ── 3. Detonation close-up (slowed), 10 blocks from the impact, outside the blast ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, 6.5, 3.2, 16.0, 0, 1.8, 23.5));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, new double[]{0, EYE, 8}, wall, true));
        frames(s, "detonation_slow", 64);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // ── 4. A ground impact in the open (slowed): the full sphere over the floor, the shock ring ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, -9.0, 5.0, 2.0, -1.0, 0.3, 12.0));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, new double[]{0, 0, 12}, true));
        frames(s, "ground_slow", 56);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // ── 5. First person (HUD on) and third person, slowed ──
        s.add(hud(false));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.command("tick rate 5"));
        s.add(cam(stage, 0, EYE, 0, 0.3, EYE - 0.2, 24));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, false));
        frames(s, "first_person", 34);
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));
        s.add(cameraType(CameraType.THIRD_PERSON_BACK));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, false));
        frames(s, "third_person", 34);
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));
        s.add(cameraType(CameraType.THIRD_PERSON_FRONT));
        s.add(cam(stage, 0, EYE, 0, 0.3, EYE - 0.4, 24));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, false));
        frames(s, "third_person_front", 12);
        s.add(cameraType(CameraType.FIRST_PERSON));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.waitTicks(40));
        s.add(clearFire(stage));
        s.add(HologramCapture.command("gamemode spectator @s"));

        // ── 6. Another player's fireball (slowed): a separate caster (a fake player, shown by a Steve stand-in) casts
        //       through the spell; this client only receives the projectile and the blast like any other player ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[0f,0f],Tags:[\"fbl_caster\"],profile:{texture:\"minecraft:entity/player/wide/steve\",model:\"wide\"}}",
                stage[0] + 3, stage[1], stage[2] + 2)).tick(mc));
        s.add(cam(stage, 1.6, 2.4, -1.2, 4.0, 1.5, 10.0));
        s.add(HologramCapture.waitTicks(8));
        s.add(onServer(server -> {
            ServerLevel level = (ServerLevel) me(server).level();
            TotalityFakePlayer other = TotalityFakePlayer.create(level, "FireballStandIn");
            camera(other, at(stage, 3, EYE, 2), at(stage, 2.0, EYE - 0.3, 24));
            new FireballSpell().onActivate(other, null);
        }));
        frames(s, "other_player", 40);
        s.add(HologramCapture.command("kill @e[type=minecraft:mannequin,tag=fbl_caster]"));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // ── 7. Several Fireballs at once (5 casts in one tick, fanned), slowed, from behind and above ──
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
        s.add(count("5 simultaneous casts"));
        frames(s, "multi_slow", 64);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));
        s.add(count("after the multiple casts"));

        // ── 8. Readability at gameplay distances (real speed): observers 8, 16 and 32 blocks from the path ──
        double[][] observers = {{8, 2.2, 12.0}, {0.5, 9.0, -6.0}, {1.0, 20.0, -20.0}};
        int[] dists = {8, 16, 32};
        for (int k = 0; k < 3; k++) {
            double[] o = observers[k];
            s.add(cam(stage, o[0], o[1], o[2], 0, 1.4, 12.0));
            s.add(HologramCapture.waitTicks(8));
            s.add(cast(stage, origin, wall, true));
            frames(s, String.format(Locale.ROOT, "distance_%02d", dists[k]), 36);
            s.add(clearFire(stage));
            s.add(HologramCapture.waitTicks(20));
        }

        // ── 9. Night, slowed side view ──
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cam(stage, 9.5, 2.6, 12.0, 0, 1.6, 12.0));
        s.add(HologramCapture.waitTicks(10));
        s.add(cast(stage, origin, wall, true));
        frames(s, "night_side_slow", 64);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(clearFire(stage));

        // ── 10. Expiry and cleanup: a shot into the open sky expires after its 100-tick life ──
        s.add(cast(stage, origin, new double[]{0, 60, 6}, true));
        s.add(HologramCapture.waitTicks(20));
        s.add(count("a sky shot in flight"));
        s.add(HologramCapture.waitTicks(100));
        s.add(count("after everything (all fireballs must be gone)"));

        // ── 11. The caster is not immune: in survival, a cast at a wall 3 blocks away hurts the caster; at 24 blocks it
        //        does not. The player's health + absorption is logged before and after each (the server decides the
        //        damage); 48 absorption (Absorption XI) means an 8d6 roll (up to 48) cannot kill the player. ──
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("effect give @s minecraft:instant_health 1 10 true"));
        s.add(HologramCapture.command("effect give @s minecraft:absorption 120 11 true"));
        s.add(hud(false));
        s.add(HologramCapture.waitTicks(20));
        s.add(health("self-damage: before the close cast"));
        s.add(cast(stage, new double[]{0, EYE, 21}, wall, false));
        for (int i = 0; i < 16; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "self_damage_close_%02d", i))));
        s.add(health("self-damage: after the close cast (3 blocks from the wall, inside the 6-block sphere)"));
        s.add(clearFire(stage));
        s.add(HologramCapture.command("effect give @s minecraft:instant_health 1 10 true"));
        s.add(HologramCapture.waitTicks(200));
        s.add(HologramCapture.command("effect give @s minecraft:absorption 120 11 true"));
        s.add(HologramCapture.waitTicks(30));
        s.add(health("self-damage: before the far cast"));
        s.add(cast(stage, origin, wall, false));
        s.add(HologramCapture.waitTicks(40));
        s.add(health("self-damage: after the far cast (24 blocks away, outside the sphere)"));
        s.add(clearFire(stage));
        s.add(count("after the self-damage casts"));
        s.add(hud(false));
        s.add(HologramCapture.command("effect clear @s minecraft:absorption"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }
}
