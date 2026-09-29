package zcylas.totality.client.hologram.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.spell.destruction.FireboltSpell;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.magic.SpellBoltEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Firebolt VFX footage for the opt-in development capture run (scene 49); inert in normal play. Casts go through
 * {@link FireboltSpell#onActivate}, the method the spell system calls, so the real projectile, its visuals and its
 * impact are shown. {@code /tick rate} slows the world for frame-by-frame sequences; rendering stays interpolated.
 * Prefix {@code FB_} on every frame; {@code -Dtotality.firebolt.capture.tag=<tag>} labels a run (e.g. original).
 */
final class FireboltCapture {

    private FireboltCapture() {}

    private static final double EYE = 1.62;
    private static final String TAG = System.getProperty("totality.firebolt.capture.tag", "");

    private static String shot(String name) {
        return "FB_" + (TAG.isEmpty() ? "" : TAG + "_") + name;
    }

    private static Step onServer(Consumer<MinecraftServer> action) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server));
            return true;
        };
    }

    private static ServerPlayer me(MinecraftServer server) {
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

    /** The caster (the player, survival-safe) stands at {@code from} and casts Firebolt toward {@code target}. */
    private static Step cast(double[] stage, double[] from, double[] target, boolean moveCaster) {
        return onServer(server -> {
            ServerPlayer p = me(server);
            if (moveCaster) camera(p, at(stage, from[0], from[1], from[2]), at(stage, target[0], target[1], target[2]));
            new FireboltSpell().onActivate(p, null);
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

    private static Step countBolts(String label) {
        return mc -> {
            int n = mc.level.getEntitiesOfClass(SpellBoltEntity.class, mc.player.getBoundingBox().inflate(80)).size();
            HologramCapture.log("info: " + label + ": " + n + " bolt entities on the client");
            return true;
        };
    }

    /** Firebolt ignites what it hits: clear the stage's fire between sequences (server side, no chat output). */
    private static Step clearFire(double[] stage) {
        return onServer(server -> {
            ServerLevel level = (ServerLevel) me(server).level();
            var pos = new net.minecraft.core.BlockPos.MutableBlockPos();
            for (int x = -16; x <= 16; x++) {
                for (int y = -2; y <= 8; y++) {
                    for (int z = -6; z <= 30; z++) {
                        pos.set(Math.floor(stage[0]) + x, stage[1] + y, Math.floor(stage[2]) + z);
                        if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.BaseFireBlock) {
                            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        }
                    }
                }
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
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.run("remember the stage", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A 41 x 21 flat stone-brick range along +z, a target wall at z = +18 and a dummy target at z = +10.
        s.add(HologramCapture.command("execute at @s run fill ~-10 ~ ~-10 ~10 ~8 ~30 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-10 ~-1 ~-10 ~10 ~-1 ~30 minecraft:stone_bricks"));
        s.add(HologramCapture.command("execute at @s run fill ~-4 ~ ~18 ~4 ~4 ~18 minecraft:polished_deepslate"));
        s.add(HologramCapture.waitTicks(10));

        double[] origin = {0, EYE, 0};
        double[] wall = {0, EYE - 0.1, 18};
        // ── 1. Side view, slowed (tick rate 4): cast, travel, impact on the wall, every client frame-tick ──
        s.add(hud(true));
        s.add(HologramCapture.command("tick rate 4"));
        s.add(cast(stage, origin, wall, true));                 // warm-up cast (loads sounds, textures)
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(onServer(server -> camera(me(server), at(stage, 4.6, 1.9, 8.0), at(stage, 0, 1.45, 8.0))));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.command("gamemode creative @s"));
        // The caster is a second, stand-in position: the player is the camera here, so cast from the player's
        // spot would move the camera. Instead the camera stays; the bolt is cast by teleporting the player to the
        // origin for the cast tick and straight back to the camera the same server tick.
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            camera(p, at(stage, 0, EYE, 0), at(stage, 0, EYE - 0.1, 18));
            new FireboltSpell().onActivate(p, null);
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        for (int i = 0; i < 40; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "side_slow_%02d", i))));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.waitTicks(20));

        // ── 2. Impact close-up (slowed), camera 3.5 blocks from the wall's hit point ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(onServer(server -> camera(me(server), at(stage, 2.6, 2.6, 15.2), at(stage, 0, 1.5, 18.0))));
        s.add(HologramCapture.waitTicks(6));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            camera(p, at(stage, 0, EYE, 6), at(stage, 0, EYE - 0.1, 18));
            new FireboltSpell().onActivate(p, null);
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        for (int i = 0; i < 40; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "impact_slow_%02d", i))));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // ── 3. First person (HUD on) and third person: cast and travel, slowed ──
        s.add(hud(false));
        s.add(HologramCapture.command("tick rate 5"));
        s.add(onServer(server -> camera(me(server), at(stage, 0, EYE, 0), at(stage, 0.3, EYE - 0.2, 18))));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, false));
        for (int i = 0; i < 24; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "first_person_%02d", i))));
        s.add(HologramCapture.waitTicks(20));
        s.add(cameraType(CameraType.THIRD_PERSON_BACK));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, false));
        for (int i = 0; i < 24; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "third_person_%02d", i))));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(10));
        // Aimed down and to the side (as at a mob on the ground): the path is not hidden behind the caster's head.
        s.add(cast(stage, origin, new double[]{3.5, 0.4, 12}, true));
        for (int i = 0; i < 24; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "third_person_aimed_%02d", i))));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(10));
        s.add(cameraType(CameraType.THIRD_PERSON_FRONT));
        s.add(onServer(server -> camera(me(server), at(stage, 0, EYE, 0), at(stage, 0.3, EYE - 0.4, 18))));
        s.add(HologramCapture.waitTicks(6));
        s.add(cast(stage, origin, wall, false));
        for (int i = 0; i < 12; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "third_person_front_%02d", i))));
        s.add(cameraType(CameraType.FIRST_PERSON));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.waitTicks(20));

        // ── 3b. Over the shoulder (slowed): another player's view of a caster (a Steve stand-in at the cast point) ──
        s.add(HologramCapture.command("tick rate 4"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[0f,0f],Tags:[\"fb_caster\"],profile:{texture:\"minecraft:entity/player/wide/steve\",model:\"wide\"}}",
                stage[0], stage[1], stage[2])).tick(mc));
        s.add(onServer(server -> camera(me(server), at(stage, -1.3, 2.3, -2.6), at(stage, 0.4, 1.4, 8.0))));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            camera(p, at(stage, 0, EYE, 0), at(stage, 0.6, EYE - 0.3, 18));
            new FireboltSpell().onActivate(p, null);
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        for (int i = 0; i < 30; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "over_shoulder_%02d", i))));
        s.add(HologramCapture.command("kill @e[type=minecraft:mannequin,tag=fb_caster]"));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // ── 4. Real speed: first person, every tick ──
        s.add(cast(stage, origin, wall, true));
        for (int i = 0; i < 10; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "first_person_realtime_%02d", i))));
        s.add(HologramCapture.waitTicks(20));

        // ── 5. Several simultaneous Firebolts (a fan of 7 casts in one tick), slowed, seen from behind and above ──
        s.add(hud(true));
        s.add(HologramCapture.command("tick rate 4"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(onServer(server -> camera(me(server), at(stage, -1.5, 5.5, -6.0), at(stage, 0, 1.0, 10))));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            for (int k = -3; k <= 3; k++) {
                camera(p, at(stage, k * 0.8, EYE, 0), at(stage, k * 2.2, EYE - 0.4 + (k % 2) * 0.5, 18));
                new FireboltSpell().onActivate(p, null);
            }
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        s.add(countBolts("7 simultaneous casts"));
        for (int i = 0; i < 32; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "multi_slow_%02d", i))));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(clearFire(stage));
        s.add(HologramCapture.waitTicks(20));

        // ── 6. Readability at gameplay distances (real speed): side cameras 8, 16 and 32 blocks from the path ──
        // 8 blocks: beside the path, at eye height. 16 and 32: raised behind the caster (clear of the hills and trees).
        double[][] observers = {{8, 2.0, 9.0}, {0.5, 9.0, -5.0}, {1.0, 20.0, -18.0}};
        int[] dists = {8, 16, 32};
        for (int k = 0; k < 3; k++) {
            double[] o = observers[k];
            int dist = dists[k];
            s.add(onServer(server -> camera(me(server), at(stage, o[0], o[1], o[2]), at(stage, 0, 1.4, 9.0))));
            s.add(HologramCapture.waitTicks(8));
            s.add(onServer(server -> {
                ServerPlayer p = me(server);
                Vec3 camEye = p.getEyePosition();
                float yaw = p.getYRot(), pitch = p.getXRot();
                camera(p, at(stage, 0, EYE, 0), at(stage, 0, EYE - 0.1, 18));
                new FireboltSpell().onActivate(p, null);
                p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
            }));
            for (int i = 0; i < 12; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "distance_%02d_%02d", dist, i))));
            s.add(HologramCapture.waitTicks(20));
        }

        // ── 7. Night, slowed side view ──
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.command("tick rate 4"));
        s.add(onServer(server -> camera(me(server), at(stage, 7.5, 2.2, 9.0), at(stage, 0, 1.4, 9.0))));
        s.add(HologramCapture.waitTicks(10));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 camEye = p.getEyePosition();
            float yaw = p.getYRot(), pitch = p.getXRot();
            camera(p, at(stage, 0, EYE, 0), at(stage, 0, EYE - 0.1, 18));
            new FireboltSpell().onActivate(p, null);
            p.teleportTo((ServerLevel) p.level(), camEye.x, camEye.y - EYE, camEye.z, Set.of(), yaw, pitch, true);
        }));
        for (int i = 0; i < 40; i++) s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "night_side_slow_%02d", i))));
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(clearFire(stage));
        s.add(hud(false));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.waitTicks(20));
        s.add(countBolts("after everything (all bolts must be gone)"));
        return s;
    }
}
