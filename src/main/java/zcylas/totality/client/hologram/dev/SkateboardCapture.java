package zcylas.totality.client.hologram.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.vehicle.SkateboardEntity;
import zcylas.totality.init.ModEntities;
import zcylas.totality.init.items.VehicleItems;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Creative Test D footage for the opt-in capture run (scene 54; prefix {@code SK_}; inert in normal play).
 * <ol>
 *   <li>The board on its own: a turntable, the wheels at ground level, the underside (on an invisible barrier).</li>
 *   <li>Its scale beside a standing player.</li>
 *   <li>The local player, in survival, through the real controls: places it from the item, aims and uses it to get on,
 *       then held movement keys (push, carve right and left, coast, over a strip of slabs, brake, push into a wall),
 *       sneak to step off, and sneak-use to take it back. One frame per client tick in the third-person camera; every
 *       tick the client's board position and heading are logged beside the integrated server's ({@code SK_SYNC}).</li>
 *   <li>Other riders as a watching client sees them: a wide-model and a slim-model stand-in riding a server-driven
 *       board (straight, a turn, a stop), from the side, behind and in front.</li>
 * </ol>
 */
final class SkateboardCapture {

    private SkateboardCapture() {}

    private static final double EYE = 1.62;
    private static final UUID RIDER_WIDE = UUID.fromString("5f1d0f7a-6c1e-4d8e-9a38-3c9a1d6b2e48");
    private static final UUID RIDER_SLIM = UUID.fromString("5f1d0f7a-6c1e-4d8e-9a38-3c9a1d6b2e41");
    /** Server thread only. */
    private static ServerPlayer standIn;
    private static SkateboardEntity standInBoard;
    private static int localTick;

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

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    private static float pitchTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x, dy = to.y - from.y, dz = to.z - from.z;
        return (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
    }

    /** Spectator camera: the local player's eyes at {@code eye}, looking at {@code look} (stage offsets). */
    private static Step camera(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> {
            Vec3 eye = at(stage, ex, ey, ez), look = at(stage, lx, ly, lz);
            ServerPlayer p = me(server);
            p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yawTo(eye, look), pitchTo(eye, look), true);
        });
    }

    private static Step shot(String name) {
        return HologramCapture.screenshot("SK_" + name);
    }

    private static SkateboardEntity spawnBoard(ServerLevel level, Vec3 pos, float yaw) {
        SkateboardEntity board = ModEntities.SKATEBOARD.create(level, EntitySpawnReason.COMMAND);
        board.setInitialPos(pos.x, pos.y, pos.z, yaw);
        level.addFreshEntity(board);
        return board;
    }

    // ── The local player's controls ──────────────────────────────────────────────────────────────────────────────

    private static void keys(Minecraft mc, boolean forward, boolean back, boolean left, boolean right, boolean sneak) {
        Options o = mc.options;
        o.keyUp.setDown(forward);
        o.keyDown.setDown(back);
        o.keyLeft.setDown(left);
        o.keyRight.setDown(right);
        o.keyShift.setDown(sneak);
    }

    /** Holds these keys for {@code frames} ticks, one frame and one {@code SK_SYNC} line per tick. */
    private static void ride(List<Step> s, String name, int frames, boolean forward, boolean back, boolean left, boolean right) {
        for (int i = 0; i < frames; i++) {
            Step frame = shot(String.format(Locale.ROOT, "%s_%02d", name, i));
            s.add(mc -> {
                keys(mc, forward, back, left, right, false);
                logSync(mc);
                return frame.tick(mc);
            });
        }
    }

    /** The client's board beside the integrated server's (same tick; the server lags the client by up to a tick). */
    private static void logSync(Minecraft mc) {
        localTick++;
        if (!(mc.player.getVehicle() instanceof SkateboardEntity client)) return;
        MinecraftServer server = mc.getSingleplayerServer();
        Entity serverBoard = server == null ? null : server.overworld().getEntity(client.getUUID());
        if (serverBoard == null) return;
        HologramCapture.log(String.format(Locale.ROOT, "SK_SYNC %d client %.4f %.4f %.4f %.2f server %.4f %.4f %.4f %.2f onGround %s input %.3f %.3f velocity %.4f",
                localTick, client.getX(), client.getY(), client.getZ(), client.getYRot(),
                serverBoard.getX(), serverBoard.getY(), serverBoard.getZ(), serverBoard.getYRot(), client.onGround(),
                mc.player.zza, mc.player.xxa, client.getDeltaMovement().horizontalDistance()));
    }

    private static Step use() {
        return mc -> {
            KeyMapping.click(mc.options.keyUse.getDefaultKey());
            HologramCapture.log("press: use");
            return true;
        };
    }

    /** The local player looks at a point (client side, as a player turning the mouse). */
    private static Step lookAt(double[] stage, double x, double y, double z) {
        return mc -> {
            Vec3 eye = mc.player.getEyePosition();
            Vec3 target = at(stage, x, y, z);
            float yaw = yawTo(eye, target), pitch = pitchTo(eye, target);
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            mc.player.yRotO = yaw;
            mc.player.xRotO = pitch;
            return true;
        };
    }

    private static SkateboardEntity clientBoardNear(Minecraft mc, Vec3 at) {
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof SkateboardEntity b && b.position().distanceTo(at) < 3.0) return b;
        }
        return null;
    }

    private static int skateboardsHeld(Minecraft mc) {
        int n = 0;
        for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) {
            if (mc.player.getInventory().getItem(i).is(VehicleItems.SKATEBOARD)) n += mc.player.getInventory().getItem(i).getCount();
        }
        return n;
    }

    // ── Stand-in riders ──────────────────────────────────────────────────────────────────────────────────────────

    private static Step spawnStandIn(double[] stage, UUID id, String name) {
        return onServer(server -> {
            ServerLevel level = server.overworld();
            standIn = TotalityFakePlayer.create(level, new GameProfile(id, name));
            standIn.setGameMode(GameType.SURVIVAL);
            Vec3 start = at(stage, 14, 0, -2);
            standIn.setPos(start.x, start.y, start.z);
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(standIn)));
            level.addNewPlayer(standIn);
            standInBoard = spawnBoard(level, start, 0.0F);
            standIn.startRiding(standInBoard);
        });
    }

    private static Step removeStandIn() {
        return onServer(server -> {
            if (standIn == null) return;
            standIn.stopRiding();
            standInBoard.discard();
            UUID id = standIn.getUUID();
            ((ServerLevel) standIn.level()).removePlayerImmediately(standIn, Entity.RemovalReason.DISCARDED);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(id)));
            standIn = null;
        });
    }

    /** The stand-in's path, tick by tick: {x, z, yaw} (stage offsets): straight, a 90 deg right turn, straight, a stop. */
    private static double[][] standInPath() {
        double[][] path = new double[95][];
        double x = 14, z = -2, yaw = 0, speed = 0.22;
        for (int i = 0; i < path.length; i++) {
            if (i >= 30 && i < 60) yaw += 3.0;
            if (i >= 80) speed *= 0.85;
            double rad = Math.toRadians(yaw);
            x += -Math.sin(rad) * speed;
            z += Math.cos(rad) * speed;
            path[i] = new double[]{x, z, yaw};
        }
        return path;
    }

    /** Moves the stand-in's board along the path (server) with the camera following it (client), a frame a tick. */
    private static void followStandIn(List<Step> s, double[] stage, String name, double[] cam) {
        double[][] path = standInPath();
        s.add(onServer(server -> {
            Vec3 start = at(stage, 14, 0, -2);
            standInBoard.setPos(start.x, start.y, start.z);
            standInBoard.setYRot(0.0F);
        }));
        s.add(HologramCapture.waitTicks(15));
        for (int i = 0; i < path.length; i++) {
            double[] p = path[i];
            Step move = onServer(server -> {
                Vec3 pos = at(stage, p[0], 0, p[1]);
                standInBoard.setPos(pos.x, pos.y, pos.z);
                standInBoard.setYRot((float) p[2]);
                standIn.setYRot((float) p[2]);
                standIn.setYHeadRot((float) p[2]);
            });
            Step camera = mc -> {
                SkateboardEntity drawn = null;
                for (Entity e : mc.level.entitiesForRendering()) {
                    if (e instanceof SkateboardEntity b && b.isVehicle() && b.getFirstPassenger() instanceof RemotePlayer) drawn = b;
                }
                if (drawn == null) return true;
                double yaw = Math.toRadians(drawn.getYRot());
                Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
                Vec3 look = drawn.position().add(0, 0.9, 0);
                Vec3 eye = drawn.position().add(forward.scale(cam[0])).add(right.scale(cam[1])).add(0, cam[2], 0);
                mc.player.snapTo(eye.x, eye.y - EYE, eye.z, yawTo(eye, look), pitchTo(eye, look));
                mc.player.setDeltaMovement(Vec3.ZERO);
                return true;
            };
            Step frame = shot(String.format(Locale.ROOT, "%s_%02d", name, i));
            s.add(mc -> move.tick(mc) & camera.tick(mc) & frame.tick(mc));
        }
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        // -Dtotality.hologram.capture.skateboard=ride runs only the local ride (for iterating on the handling).
        boolean all = !"ride".equals(System.getProperty("totality.hologram.capture.skateboard"));
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
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
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A flat meadow (fills split under the 32768-block limit): the ride goes +z from a one-block platform (it
        // drops off its end), over a strip of slabs, towards a brick wall; the showcase stands off to the -x side.
        for (int[] z : new int[][]{{-12, 3}, {4, 19}, {20, 40}}) {
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-22 ~ ~%d ~22 ~12 ~%d minecraft:air", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-22 ~13 ~%d ~22 ~24 ~%d minecraft:air", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-22 ~-1 ~%d ~22 ~-1 ~%d minecraft:grass_block", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fillbiome ~-22 ~-3 ~%d ~22 ~12 ~%d minecraft:plains", z[0], z[1])));
        }
        s.add(HologramCapture.command("execute at @s run fill ~-3 ~ ~-1 ~3 ~ ~5 minecraft:stone"));
        s.add(HologramCapture.command("execute at @s run fill ~-12 ~ ~14 ~12 ~ ~15 minecraft:smooth_stone_slab[type=bottom]"));
        s.add(HologramCapture.command("execute at @s run fill ~-22 ~ ~36 ~22 ~2 ~36 minecraft:stone_bricks"));
        s.add(HologramCapture.command("execute at @s run setblock ~-14 ~ ~10 minecraft:barrier"));
        s.add(HologramCapture.command("kill @e[type=!minecraft:player,distance=..60]"));
        s.add(HologramCapture.waitTicks(40));

        if (all) showcase(s, stage);
        localRide(s, stage);
        if (all) standIns(s, stage);
        return s;
    }

    private static void showcase(List<Step> s, double[] stage) {
        // ── 1. The board on its own ───────────────────────────────────────────
        s.add(onServer(server -> {
            spawnBoard(server.overworld(), at(stage, -14, 0, 4), 0.0F);
            spawnBoard(server.overworld(), at(stage, -14, 1, 10), 0.0F);
        }));
        s.add(HologramCapture.waitTicks(10));
        String[] names = {"front", "front_right", "right", "back_right", "back", "back_left", "left", "front_left"};
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            // the board heads +z, so its nose is seen from +z
            s.add(camera(stage, -14 + Math.sin(a) * 2.2, 0.95, 4 + Math.cos(a) * 2.2, -14, 0.2, 4));
            s.add(HologramCapture.waitTicks(3));
            s.add(shot("turn_" + names[k]));
        }
        s.add(camera(stage, -14, 2.6, 4.01, -14, 0, 4));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("top"));
        s.add(camera(stage, -12.6, 0.12, 4, -14, 0.1, 4));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("wheels_ground_level"));
        s.add(camera(stage, -12.9, 0.35, 11.3, -14, 1.2, 10));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("underside"));
        s.add(camera(stage, -14, 0.4, 10.0, -14, 1.2, 10));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("underside_below"));

        // ── 2. Scale beside a standing player ─────────────────────────────────
        s.add(onServer(server -> {
            ServerLevel level = server.overworld();
            standIn = TotalityFakePlayer.create(level, new GameProfile(RIDER_WIDE, "Skater"));
            standIn.setGameMode(GameType.SURVIVAL);
            Vec3 pos = at(stage, -12.8, 0, 4);
            standIn.setPos(pos.x, pos.y, pos.z);
            standIn.setYRot(180.0F);
            standIn.setYHeadRot(180.0F);
            standIn.setYBodyRot(180.0F);
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(standIn)));
            level.addNewPlayer(standIn);
        }));
        s.add(HologramCapture.waitTicks(20));
        s.add(camera(stage, -13.4, 1.0, 0.0, -13.4, 0.8, 4));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("scale_front"));
        s.add(camera(stage, -9.0, 1.1, 4.0, -13.4, 0.8, 4));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("scale_side"));
        s.add(onServer(server -> {
            ((ServerLevel) standIn.level()).removePlayerImmediately(standIn, Entity.RemovalReason.DISCARDED);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(RIDER_WIDE)));
            standIn = null;
        }));

    }

    private static void localRide(List<Step> s, double[] stage) {
        // ── 3. The local player through the real controls ─────────────────────
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 start = at(stage, 0, 1, 1);
            p.teleportTo((ServerLevel) p.level(), start.x, start.y, start.z, Set.of(), 0.0F, 60.0F, true);
            p.setGameMode(GameType.SURVIVAL);
            p.getInventory().clearContent();
            p.getInventory().setItem(0, new ItemStack(VehicleItems.SKATEBOARD));
        }));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.run("select the skateboard", () -> Minecraft.getInstance().player.getInventory().setSelectedSlot(0)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.check("client: the player holds one skateboard", () -> skateboardsHeld(Minecraft.getInstance()) == 1));
        s.add(lookAt(stage, 0, 1, 3));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("placed from the item: a board on the platform, the item used up", () -> {
            Minecraft mc = Minecraft.getInstance();
            return clientBoardNear(mc, at(stage, 0, 1, 3)) != null && skateboardsHeld(mc) == 0;
        }));
        s.add(HologramCapture.run("third person (back)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK)));
        s.add(shot("local_placed"));
        s.add(lookAt(stage, 0, 1.2, 3));
        s.add(HologramCapture.waitTicks(3));
        s.add(use());
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("use to get on: the local player rides the board", () ->
                Minecraft.getInstance().player.getVehicle() instanceof SkateboardEntity));
        s.add(HologramCapture.run("look ahead", () -> {
            var p = Minecraft.getInstance().player;
            p.setXRot(18.0F);
            p.xRotO = 18.0F;
        }));
        s.add(HologramCapture.waitTicks(5));
        s.add(shot("local_mounted"));
        ride(s, "ride_push", 45, true, false, false, false);
        ride(s, "ride_carve_right", 12, true, false, false, true);
        ride(s, "ride_carve_left", 12, true, false, true, false);
        ride(s, "ride_coast", 12, false, false, false, false);
        s.add(HologramCapture.run("third person (front)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        ride(s, "ride_coast_front", 8, false, false, false, false);
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        ride(s, "ride_coast_first_person", 6, false, false, false, false);
        s.add(HologramCapture.run("third person (back)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK)));
        ride(s, "ride_brake", 25, false, true, false, false);
        ride(s, "ride_push_to_wall", 70, true, false, false, false);
        s.add(mc -> {
            keys(mc, false, false, false, false, true);
            return true;
        });
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("sneak to step off: the player is off the board", () -> !Minecraft.getInstance().player.isPassenger()));
        s.add(shot("local_dismounted"));
        s.add(HologramCapture.run("third person (front)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("local_dismounted_front"));
        s.add(HologramCapture.run("third person (back)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK)));
        s.add(mc -> {
            SkateboardEntity board = null;
            for (Entity e : mc.level.entitiesForRendering()) if (e instanceof SkateboardEntity b && b.distanceTo(mc.player) < 4.0) board = b;
            if (board != null) {
                Vec3 eye = mc.player.getEyePosition();
                Vec3 target = board.position().add(0, 0.2, 0);
                mc.player.setYRot(yawTo(eye, target));
                mc.player.setXRot(pitchTo(eye, target));
            }
            keys(mc, false, false, false, false, true);
            return true;
        });
        s.add(HologramCapture.waitTicks(3));
        s.add(shot("local_before_pickup"));
        s.add(use());
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            keys(mc, false, false, false, false, false);
            return true;
        });
        s.add(HologramCapture.check("sneak-use takes it back: exactly one skateboard, no board left nearby", () -> {
            Minecraft mc = Minecraft.getInstance();
            return skateboardsHeld(mc) == 1 && clientBoardNear(mc, mc.player.position()) == null;
        }));
        s.add(shot("local_picked_up"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));

    }

    private static void standIns(List<Step> s, double[] stage) {
        // ── 4. Other riders, as a watching client sees them ───────────────────
        for (UUID id : new UUID[]{RIDER_WIDE, RIDER_SLIM}) {
            String model = id.equals(RIDER_WIDE) ? "wide" : "slim";
            s.add(spawnStandIn(stage, id, "Skater"));
            s.add(HologramCapture.waitTicks(30));
            s.add(HologramCapture.check("client: the " + model + " stand-in is seen riding a skateboard", () -> {
                for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) {
                    if (e instanceof RemotePlayer p && p.getVehicle() instanceof SkateboardEntity) return true;
                }
                return false;
            }));
            followStandIn(s, stage, model + "_side", new double[]{0.0, 2.6, 1.1});
            if (model.equals("wide")) {
                followStandIn(s, stage, model + "_back", new double[]{-3.0, 0.4, 1.4});
                followStandIn(s, stage, model + "_front", new double[]{3.0, 0.6, 1.2});
            }
            s.add(removeStandIn());
            s.add(HologramCapture.waitTicks(10));
        }
    }
}
