package zcylas.totality.client.hologram.dev;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import zcylas.totality.client.entity.forestboar.ForestBoarModel;
import zcylas.totality.client.entity.forestboar.ForestBoarRenderState;
import zcylas.totality.client.entity.forestboar.ForestBoarRenderer;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.animal.ForestBoarEntity;
import zcylas.totality.entity.animal.ForestBoarEntity.Behavior;
import zcylas.totality.entity.animal.ForestBoarRules;
import zcylas.totality.init.ModEntities;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Forest Boar (Astra hybrid) scenes for the opt-in development capture run; inert in normal play.
 * <ul>
 *   <li>Model views from the same camera angles and field of view as the Blockbench renders of Astra's original.</li>
 *   <li>Animation footage from a FIXED side camera over a striped floor (a stripe per block), one frame per tick,
 *       with every hoof's world position logged from the exactly-rendered pose ({@code hoof:} lines), so foot
 *       sliding and a reversed ("moonwalking") gait can be measured, not just looked at. The behaviour is set from
 *       the capture here (the brain paused) so each animation is shown on demand.</li>
 *   <li>Gameplay with the real brain and a real survival player: wandering and grazing, detection, the flee, the
 *       charge and retreat, a landed hit, the escape (and when exactly it is allowed), and death drops.</li>
 * </ul>
 */
final class ForestBoarCapture {

    private ForestBoarCapture() {}

    private static final String SHOW = "@e[type=totality:forest_boar,tag=show,limit=1]";
    private static final double EYE = 1.62;
    /** Blockbench units to blocks at the render scale (1.8191 / 16): the matched views share their numbers. */
    private static final double BB = 1.8191 / 16.0;
    private static final String[] LEGS = {"leg_front_left", "leg_front_right", "leg_rear_left", "leg_rear_right"};

    // ── helpers ──

    private static Step onServer(String label, Consumer<MinecraftServer> action) {
        return mc -> {
            MinecraftServer server = mc.getSingleplayerServer();
            if (server != null) server.execute(() -> action.accept(server));
            return true;
        };
    }

    private static ServerPlayer me(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static ForestBoarEntity tagged(MinecraftServer server, String tag) {
        ServerPlayer p = me(server);
        List<ForestBoarEntity> boars = p.level().getEntitiesOfClass(ForestBoarEntity.class, p.getBoundingBox().inflate(160),
                b -> b.entityTags().contains(tag));
        return boars.isEmpty() ? null : boars.getFirst();
    }

    private static ForestBoarEntity clientNearest(Minecraft mc) {
        return mc.level.getEntitiesOfClass(ForestBoarEntity.class, mc.player.getBoundingBox().inflate(80)).stream()
                .min(java.util.Comparator.comparingDouble(b -> b.distanceToSqr(mc.player))).orElse(null);
    }

    /** Puts the player's eye at {@code eye}, looking at {@code at}. */
    private static void camera(ServerPlayer p, Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
    }

    private static Step hud(boolean hidden) {
        return HologramCapture.run((hidden ? "hide" : "show") + " HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden() != hidden) hud.toggle();
        });
    }

    private static Step fov(int degrees) {
        return HologramCapture.run("fov " + degrees, () -> Minecraft.getInstance().options.fov().set(degrees));
    }

    private static Step log(String line) {
        return mc -> {
            HologramCapture.log(line);
            return true;
        };
    }

    /** Screenshot and hoof positions of the nearest boar, in one tick. */
    private static Step frame(String scene, int i) {
        return mc -> {
            HologramCapture.screenshot(String.format(Locale.ROOT, "%s_%03d", scene, i)).tick(mc);
            ForestBoarEntity b = clientNearest(mc);
            if (b != null) HologramCapture.log("hoof: " + scene + " " + i + " " + hoofs(mc, b));
            return true;
        };
    }

    /** Every hoof's lowest corner (x y z, blocks) from the pose the renderer drew last, plus the gait state. */
    private static String hoofs(Minecraft mc, ForestBoarEntity boar) {
        if (!(mc.getEntityRenderDispatcher().getRenderer(boar) instanceof ForestBoarRenderer r) || r.lastExtracted() == null) return "n/a";
        ForestBoarRenderState st = r.lastExtracted();
        ForestBoarModel model = r.getModel();
        model.setupAnim(st);
        PoseStack ps = new PoseStack();
        ps.translate(st.x, st.y, st.z);
        ps.scale(st.scale, st.scale, st.scale);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - st.bodyRot));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.translate(0.0F, -1.501F, 0.0F);
        // Per leg: the hoof's bottom centre (x, z: where it stands) and its lowest corner (y: whether it touches the ground).
        Map<String, float[]> low = new TreeMap<>();
        model.root().visit(ps, (pose, path, index, cube) -> {
            String leg = null;
            for (String l : LEGS) if (path.endsWith(l)) leg = l;
            if (leg == null) return;
            float[] best = low.get(leg);
            if (best != null && best[3] >= cube.maxY) return;                // the hoof is the leg's lowest cube
            Vector3f c = pose.pose().transformPosition((cube.minX + cube.maxX) / 32.0F, cube.maxY / 16.0F, (cube.minZ + cube.maxZ) / 32.0F, new Vector3f());
            float minY = Float.MAX_VALUE;
            for (float x : new float[]{cube.minX, cube.maxX}) {
                for (float z : new float[]{cube.minZ, cube.maxZ}) {
                    minY = Math.min(minY, pose.pose().transformPosition(x / 16.0F, cube.maxY / 16.0F, z / 16.0F, new Vector3f()).y);
                }
            }
            low.put(leg, new float[]{c.x, minY, c.z, cube.maxY});
        });
        StringBuilder sb = new StringBuilder(String.format(Locale.ROOT, "t=%.2f boar=%.4f,%.4f,%.4f yaw=%.1f phase=%.4f walk=%.3f run=%.3f beh=%s",
                st.ageInTicks, st.x, st.y, st.z, st.bodyRot, st.gaitPhase, st.walkWeight, st.runWeight, st.behavior));
        low.forEach((leg, v) -> sb.append(String.format(Locale.ROOT, " %s=%.4f,%.4f,%.4f", leg, v[0], v[1], v[2])));
        return sb.toString();
    }

    private static Step script(String tag, Behavior b) {
        return onServer("script " + b, server -> {
            ForestBoarEntity boar = tagged(server, tag);
            if (boar != null) boar.scriptForCapture(b);
        });
    }

    private static Step stop(String tag) {
        return onServer("stop", server -> {
            ForestBoarEntity b = tagged(server, tag);
            if (b != null) b.getNavigation().stop();
        });
    }

    private static void discardBoars(MinecraftServer server) {
        ServerPlayer p = me(server);
        p.level().getEntitiesOfClass(ForestBoarEntity.class, p.getBoundingBox().inflate(200)).forEach(Entity::discard);
    }

    // ── scenes ──

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule mob_griefing false"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(onServer("clear boars", ForestBoarCapture::discardBoars));
        s.add(HologramCapture.run("remember the stage position", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A 31 x 31 flat stage; the floor is striped every block along x (grass / coarse dirt) as a ground reference.
        s.add(HologramCapture.command("execute at @s run fill ~-15 ~ ~-15 ~15 ~7 ~15 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-15 ~-1 ~-15 ~15 ~-1 ~15 minecraft:grass_block"));
        for (int x = -15; x <= 15; x += 2) {
            s.add(HologramCapture.command("execute at @s run fill ~" + x + " ~-1 ~-15 ~" + x + " ~-1 ~15 minecraft:coarse_dirt"));
        }
        s.add(HologramCapture.command("execute at @s run fill ~-15 ~-2 ~-15 ~15 ~-2 ~15 minecraft:dirt"));
        s.add(HologramCapture.waitTicks(10));

        // -Dtotality.boar.capture.parts=views,poses,locomotion,gameplay,drops,forest,hitbox (default: all) for quicker reruns.
        Set<String> parts = Set.of(System.getProperty("totality.boar.capture.parts", "views,poses,locomotion,gameplay,drops,forest,hitbox").split(","));
        if (parts.contains("views") || parts.contains("poses")) modelViews(s, stage, parts.contains("views"));
        if (parts.contains("poses")) poses(s, stage);
        if (parts.contains("locomotion")) locomotion(s, stage);
        if (parts.contains("gameplay")) gameplay(s, stage);
        if (parts.contains("drops")) drops(s);
        if (parts.contains("forest")) forest(s);
        if (parts.contains("hitbox")) hitbox(s, stage);
        s.add(fov(70));
        s.add(hud(false));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.command("gamerule spawn_mobs true"));
        s.add(HologramCapture.command("gamerule mob_griefing true"));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static Step summon(double[] stage, double dx, double dz, float yaw, String tag, boolean ai) {
        return onServer("summon " + tag, server -> {
            ServerLevel level = (ServerLevel) me(server).level();
            ForestBoarEntity b = ModEntities.FOREST_BOAR.create(level, EntitySpawnReason.COMMAND);
            if (b == null) return;
            b.snapTo(stage[0] + dx, stage[1], stage[2] + dz, yaw, 0.0F);
            b.setYHeadRot(yaw);
            b.setYBodyRot(yaw);
            b.addTag(tag);
            b.setNoAi(!ai);
            level.addFreshEntity(b);
        });
    }

    // ── A: appearance ──

    /** Camera and target in Blockbench units (the model faces -z there): the same numbers drive the Blockbench renders. */
    static final double[][] VIEWS = {
            {0, 7, -58, 0, 5.5, 0}, {-58, 7, 0, 0, 5.5, 0}, {58, 7, 0, 0, 5.5, 0}, {0, 8, 58, 0, 5.5, 0},
            {0, 62, 0.8, 0, 5, 0.5}, {-36, 28, -40, 0, 5, 0}, {34, 5, -42, 0, 5, 0}, {38, 22, 40, 0, 5, 0}};
    static final String[] VIEW_NAMES = {"A0_front", "A1_left_side", "A2_right_side", "A3_rear", "A4_top", "A5_three_quarter",
            "A6_three_quarter_low", "A7_rear_three_quarter"};

    private static void modelViews(List<Step> s, double[] stage, boolean shoot) {
        s.add(summon(stage, 0, 0, 0.0F, "show", false));             // yaw 0: faces +z (south)
        s.add(HologramCapture.waitTicks(20));
        s.add(mc -> {
            ForestBoarEntity b = clientNearest(mc);
            HologramCapture.log(b == null ? "FAIL: no Forest Boar spawned" : String.format(Locale.ROOT,
                    "PASS: Forest Boar spawned (client) — box %.2f x %.2f, eye height %.2f, type %s", b.getBbWidth(), b.getBbHeight(),
                    b.getEyeHeight(), BuiltInRegistries.ENTITY_TYPE.getKey(b.getType())));
            return true;
        });
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(hud(true));
        s.add(fov(30));
        if (!shoot) return;                                        // the pose scenes only need the boar
        // The rest pose (no animation layer active: a standing boar in a moving-only state), as Blockbench shows it.
        s.add(script("show", Behavior.FLEE));
        for (int i = 0; i < VIEWS.length; i++) {
            double[] v = VIEWS[i];
            // Blockbench (x, y, z) -> world offset (-x, y, -z) at the render scale, for a boar facing +z.
            s.add(onServer("camera " + VIEW_NAMES[i], server -> camera(me(server), at(stage, -v[0] * BB, v[1] * BB, -v[2] * BB),
                    at(stage, -v[3] * BB, v[4] * BB, -v[5] * BB))));
            s.add(HologramCapture.waitTicks(12));
            s.add(HologramCapture.screenshot(VIEW_NAMES[i]));
        }
        // Beside Steve (a mannequin with the default skin), then night and torchlight.
        s.add(fov(50));
        s.add(HologramCapture.command("execute as " + SHOW + " at @s run summon minecraft:mannequin ~1.5 ~ ~ {Rotation:[0f,0f],Tags:[\"scale\"],profile:{texture:\"minecraft:entity/player/wide/steve\",model:\"wide\"}}"));
        s.add(onServer("camera scale", server -> camera(me(server), at(stage, 0.75, 1.0, 5.0), at(stage, 0.75, 0.8, 0))));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.screenshot("A8_beside_steve"));
        s.add(onServer("remove the mannequin", server -> me(server).level().getEntitiesOfClass(
                net.minecraft.world.entity.decoration.Mannequin.class, me(server).getBoundingBox().inflate(64)).forEach(Entity::discard)));
        s.add(HologramCapture.command("time set 18000"));
        s.add(onServer("camera night", server -> camera(me(server), at(stage, -2.2, 1.3, 2.6), at(stage, 0, 0.6, 0))));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.screenshot("A9_night"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("execute as " + SHOW + " at @s run fill ~-4 ~-1 ~-4 ~4 ~4 ~4 minecraft:stone hollow"));
        s.add(HologramCapture.command("execute as " + SHOW + " at @s run setblock ~2 ~ ~2 minecraft:torch"));
        s.add(onServer("camera torch", server -> camera(me(server), at(stage, -1.9, 1.1, 2.4), at(stage, 0, 0.6, 0))));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("A10_torchlight"));
        s.add(HologramCapture.command("execute as " + SHOW + " at @s run fill ~-4 ~ ~-4 ~4 ~4 ~4 minecraft:air"));
        s.add(HologramCapture.command("execute as " + SHOW + " at @s run fill ~-4 ~-1 ~-4 ~4 ~-1 ~4 minecraft:grass_block"));
        for (int x = -3; x <= 3; x += 2) {       // restore the stripes under the room
            s.add(HologramCapture.command("execute as " + SHOW + " at @s run fill ~" + x + " ~-1 ~-4 ~" + x + " ~-1 ~4 minecraft:coarse_dirt"));
        }
    }

    // ── B: stationary animations, fixed side camera ──

    private static void poses(List<Step> s, double[] stage) {
        s.add(fov(40));
        // The boar faces +z; the camera ahead of it and to its right (-x), a little above.
        s.add(onServer("camera pose 3/4", server -> camera(me(server), at(stage, -3.3, 1.35, 4.2), at(stage, 0, 0.55, 0.3))));
        s.add(HologramCapture.waitTicks(5));
        sequence(s, "B0_idle", Behavior.WANDER, 13, 5);        // 3 s idle loop, every 0.25 s
        sequence(s, "B1_graze", Behavior.GRAZE, 21, 5);        // lowering, then 4 s of grazing
        sequence(s, "B2_sniff", Behavior.SNIFF, 13, 5);
        sequence(s, "B3_alert", Behavior.ALERT, 17, 2);        // the startle, every 0.1 s
        sequence(s, "B4_charge_windup", Behavior.CHARGE_WINDUP, 12, 2);
        // Hurt: a real landed hit (a creative player's hit; the paused brain ignores it), frames every tick.
        s.add(script("show", Behavior.WANDER));
        s.add(HologramCapture.waitTicks(20));
        s.add(onServer("hit", server -> {
            ForestBoarEntity b = tagged(server, "show");
            ServerPlayer p = me(server);
            if (b == null) return;
            int swings = 0;
            while (b.getLastHurtByMob() != p && swings++ < 20) {
                b.hurtServer((ServerLevel) b.level(), b.damageSources().playerAttack(p), 1.0F);
                b.invulnerableTime = 0;
            }
        }));
        for (int i = 0; i < 10; i++) s.add(frame("B5_hurt", i));
        s.add(onServer("heal", server -> {
            ForestBoarEntity b = tagged(server, "show");
            if (b != null) b.setHealth(b.getMaxHealth());
        }));
        // Side views of the grazing and wind-up silhouettes.
        s.add(onServer("camera pose side", server -> camera(me(server), at(stage, -5.2, 0.8, 0.3), at(stage, 0, 0.55, 0.3))));
        s.add(script("show", Behavior.GRAZE));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.screenshot("B6_graze_side"));
        s.add(script("show", Behavior.CHARGE_WINDUP));
        s.add(HologramCapture.waitTicks(16));
        s.add(HologramCapture.screenshot("B7_windup_side"));
        s.add(script("show", Behavior.ALERT));
        s.add(HologramCapture.waitTicks(5));
        s.add(HologramCapture.screenshot("B8_alert_side"));
        s.add(onServer("remove", server -> {
            ForestBoarEntity b = tagged(server, "show");
            if (b != null) b.discard();
        }));
    }

    private static void sequence(List<Step> s, String name, Behavior b, int frames, int every) {
        s.add(script("show", Behavior.FLEE));                    // rest pose between sequences
        s.add(HologramCapture.waitTicks(12));
        s.add(script("show", b));
        for (int i = 0; i < frames; i++) {
            s.add(frame(name, i));
            if (every > 1) s.add(HologramCapture.waitTicks(every - 1));
        }
    }

    // ── C: locomotion, fixed side camera over the striped floor ──

    private static void locomotion(List<Step> s, double[] stage) {
        s.add(fov(45));
        // Moving along +x (east), so its right side faces the camera, which stays put 6.5 blocks south (it sees
        // x = -4.3 .. 5.3). Every frame is one tick; the boar starts inside the view so its gait is tracked throughout.
        Step cam = onServer("fixed side camera", server -> camera(me(server), at(stage, 0.5, 0.75, 6.5), at(stage, 0.5, 0.55, 0)));
        crossing(s, stage, cam, "C0_walk", -3.8, Behavior.WANDER, ForestBoarRules.WANDER_SPEED, 80, null, 0, 0);
        // walk -> idle: it stops (every other tick).
        s.add(stop("show"));
        for (int i = 0; i < 16; i++) {
            s.add(frame("C1_walk_to_idle", i));
            s.add(HologramCapture.waitTicks(1));
        }
        crossing(s, stage, cam, "C2_walk_to_run", -4.2, Behavior.WANDER, ForestBoarRules.WANDER_SPEED, 44, Behavior.FLEE, ForestBoarRules.FLEE_SPEED, 24);
        crossing(s, stage, cam, "C3_run", -4.3, Behavior.FLEE, ForestBoarRules.FLEE_SPEED, 32, null, 0, 0);
        // The charge: the wind-up in place, then the rush along the row.
        crossing(s, stage, cam, "C4_charge", -3.0, Behavior.CHARGE_WINDUP, 0.0, 44, Behavior.CHARGE, ForestBoarRules.CHARGE_SPEED, ForestBoarRules.WINDUP_TICKS);
        // alert -> escape (scripted timing, side view): the startle, then away along the row.
        crossing(s, stage, cam, "C5_alert_to_escape", -3.0, Behavior.ALERT, 0.0, 44, Behavior.FLEE, ForestBoarRules.FLEE_SPEED, ForestBoarRules.ALERT_TICKS);
        s.add(onServer("done", ForestBoarCapture::discardBoars));
    }

    /**
     * A boar crossing the fixed view along +x from {@code x0}, one frame per tick: first in {@code b} at {@code speed}
     * (0: standing), then from frame {@code switchAt} in {@code then} at {@code thenSpeed}. The goal is resolved when
     * the step runs (the stage position is only known then).
     */
    private static void crossing(List<Step> s, double[] stage, Step cam, String name, double x0, Behavior b, double speed,
                                 int frames, Behavior then, double thenSpeed, int switchAt) {
        s.add(onServer("reset " + name, ForestBoarCapture::discardBoars));
        s.add(summon(stage, x0, 0.0, -90.0F, "show", true));       // yaw -90: faces +x
        s.add(cam);
        s.add(script("show", b));
        s.add(HologramCapture.waitTicks(15));                      // seen and tracked before it moves
        for (int i = 0; i < frames; i++) {
            final boolean switching = then != null && i == switchAt;
            final double v = then != null && i >= switchAt ? thenSpeed : speed;
            final Behavior now = then != null && i >= switchAt ? then : b;
            final int index = i;
            s.add(mc -> {
                MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) server.execute(() -> {
                    ForestBoarEntity boar = tagged(server, "show");
                    if (boar == null) return;
                    if (switching) boar.scriptForCapture(now);
                    if (v > 0) {
                        double gx = stage[0] + 16.0, gz = stage[2];
                        var target = boar.getNavigation().getTargetPos();
                        if (target == null || target.getX() != (int) Math.floor(gx) || boar.getNavigation().isDone()) {
                            boar.getNavigation().moveTo(gx, stage[1], gz, v);
                        } else {
                            boar.getNavigation().setSpeedModifier(v);
                        }
                    } else {
                        boar.getNavigation().stop();
                    }
                });
                return frame(name, index).tick(mc);
            });
        }
    }

    // ── D: gameplay with the real brain and a survival player ──

    private static void gameplay(List<Step> s, double[] stage) {
        s.add(fov(70));
        s.add(hud(false));                                         // health, hunger and combat text visible
        // D0: undisturbed — three boars on the stage, a spectator (ignored by boars) watching from above for 30 s.
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(summon(stage, -4, -3, 30.0F, "calm", true));
        s.add(summon(stage, 3, 2, 160.0F, "calm", true));
        s.add(summon(stage, 0, 6, 250.0F, "calm", true));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(onServer("overview camera", server -> camera(me(server), at(stage, 0.5, 9.5, 14.0), at(stage, 0, 0, 0))));
        Map<Behavior, Integer> seen = new EnumMap<>(Behavior.class);
        double[] moved = {0};
        List<Vec3> start = new ArrayList<>();
        s.add(onServer("record start", server -> {
            ServerPlayer p = me(server);
            p.level().getEntitiesOfClass(ForestBoarEntity.class, p.getBoundingBox().inflate(40), b -> b.entityTags().contains("calm"))
                    .forEach(b -> start.add(b.position()));
        }));
        for (int i = 0; i < 30; i++) {
            s.add(onServer("sample", server -> {
                ServerPlayer p = me(server);
                var boars = p.level().getEntitiesOfClass(ForestBoarEntity.class, p.getBoundingBox().inflate(60), b -> b.entityTags().contains("calm"));
                StringBuilder line = new StringBuilder("info: calm boars:");
                for (ForestBoarEntity b : boars) {
                    synchronized (seen) { seen.merge(b.getBehavior(), 1, Integer::sum); }
                    line.append(' ').append(b.getBehavior());
                }
                HologramCapture.log(line.toString());
            }));
            if (i % 3 == 0) s.add(HologramCapture.screenshot(String.format(Locale.ROOT, "D0_undisturbed_%02d", i / 3)));
            s.add(HologramCapture.waitTicks(20));
        }
        s.add(onServer("undisturbed verdict", server -> {
            ServerPlayer p = me(server);
            var boars = p.level().getEntitiesOfClass(ForestBoarEntity.class, p.getBoundingBox().inflate(60), b -> b.entityTags().contains("calm"));
            for (int k = 0; k < boars.size() && k < start.size(); k++) moved[0] = Math.max(moved[0], boars.get(k).position().distanceTo(start.get(k)));
            synchronized (seen) {
                boolean peaceful = seen.keySet().stream().allMatch(Behavior::peaceful);
                HologramCapture.log((peaceful && seen.containsKey(Behavior.WANDER) && (seen.containsKey(Behavior.GRAZE) || seen.containsKey(Behavior.SNIFF))
                        && moved[0] > 1.0 ? "PASS" : "FAIL") + ": undisturbed for 30 s (a spectator watching): only peaceful states " + seen
                        + String.format(Locale.ROOT, ", wandered up to %.1f blocks", moved[0]));
            }
            discardBoars(server);
        }));

        // D1: detection and an immediate flee. D2: detection and a charge, then retreat. Survival player, HUD on.
        s.add(HologramCapture.command("gamemode survival @s"));
        reaction(s, stage, "D1_detect_flee", false);
        reaction(s, stage, "D2_detect_charge", true);
        hitReaction(s, stage);
        escape(s, stage);
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.command("effect give @s minecraft:instant_health 1 4 true"));
    }

    private static void reaction(List<Step> s, double[] stage, String name, boolean charge) {
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(summon(stage, 0.0, -5.0, 0.0F, "react", true));    // faces +z (toward the player)
        s.add(onServer("player far", server -> camera(me(server), at(stage, 0.0, EYE, 11.5), at(stage, 0.0, 0.6, -5.0))));
        s.add(HologramCapture.waitTicks(30));
        float[] health = new float[2];
        List<String> timeline = new ArrayList<>();
        s.add(onServer("force reaction", server -> {
            ForestBoarEntity b = tagged(server, "react");
            if (b != null) b.forceNextReaction(charge);
            health[0] = me(server).getHealth();
            health[1] = health[0];
            HologramCapture.log("info: " + name + ": boar " + (b == null ? "missing" : b.getBehavior()) + " with the player "
                    + (b == null ? "?" : String.format(Locale.ROOT, "%.1f", b.distanceTo(me(server)))) + " blocks away (beyond sensing, not in view)");
        }));
        s.add(HologramCapture.waitTicks(20));
        // Step within sensing range (5.5 blocks from wherever it has wandered to), facing it.
        s.add(onServer("player near", server -> {
            ForestBoarEntity b = tagged(server, "react");
            Vec3 at = b == null ? at(stage, 0.0, 0.0, -5.0) : b.position();
            camera(me(server), at.add(0.0, EYE, 5.5), at.add(0.0, 0.6, 0.0));
        }));
        for (int i = 0; i < 60; i++) {
            s.add(onServer("track", server -> {
                ForestBoarEntity b = tagged(server, "react");
                health[1] = Math.min(health[1], me(server).getHealth());
                String st = b == null ? "gone" : b.getBehavior() + String.format(Locale.ROOT, "@%.1f", b.distanceTo(me(server)));
                synchronized (timeline) {
                    if (timeline.isEmpty() || !timeline.getLast().split("@")[0].equals(st.split("@")[0])) timeline.add(st);
                }
            }));
            if (i % 2 == 0) s.add(HologramCapture.screenshot(String.format(Locale.ROOT, "%s_%02d", name, i / 2)));
        }
        s.add(onServer("verdict", server -> {
            ForestBoarEntity b = tagged(server, "react");
            List<String> t;
            synchronized (timeline) { t = new ArrayList<>(timeline); }
            while (!t.isEmpty() && Behavior.valueOf(t.getFirst().split("@")[0]).peaceful()) t.removeFirst();   // before it noticed
            boolean order = charge
                    ? t.size() >= 4 && t.get(0).startsWith("ALERT") && t.get(1).startsWith("CHARGE_WINDUP") && t.get(2).startsWith("CHARGE") && t.get(3).startsWith("FLEE")
                    : t.size() >= 2 && t.get(0).startsWith("ALERT") && t.get(1).startsWith("FLEE");
            HologramCapture.log((order ? "PASS" : "FAIL") + ": " + name + ": " + t
                    + (charge ? " — charge result '" + (b == null ? "?" : b.lastChargeResult()) + "'" + String.format(Locale.ROOT,
                    ", player health %.1f, lowest during the charge %.1f", health[0], health[1]) : ""));
            if (b != null) {
                HologramCapture.log(String.format(Locale.ROOT, "info: %s: after 3 s the boar is %s, %.1f blocks away", name, b.getBehavior(), b.distanceTo(me(server))));
            }
        }));
        if (charge) {
            // No endless retaliation: the player keeps stepping up to it for 10 s; it must never charge again.
            List<Behavior> after = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                s.add(onServer("follow", server -> {
                    ForestBoarEntity b = tagged(server, "react");
                    if (b == null) return;
                    Vec3 toward = b.position().subtract(me(server).position()).normalize();
                    Vec3 eye = b.position().subtract(toward.scale(3.0)).add(0, EYE, 0);
                    camera(me(server), eye, b.position().add(0, 0.6, 0));
                }));
                s.add(HologramCapture.waitTicks(10));
                s.add(onServer("record", server -> {
                    ForestBoarEntity b = tagged(server, "react");
                    synchronized (after) { after.add(b == null ? null : b.getBehavior()); }
                }));
            }
            s.add(onServer("no second charge", server -> {
                synchronized (after) {
                    boolean ok = after.stream().noneMatch(x -> x == Behavior.CHARGE || x == Behavior.CHARGE_WINDUP || x == Behavior.ALERT);
                    HologramCapture.log((ok ? "PASS" : "FAIL") + ": followed for 10 s after its charge, it only ever fled: " + after);
                }
            }));
        }
    }

    private static void hitReaction(List<Step> s, double[] stage) {
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(summon(stage, 0.0, 0.0, 0.0F, "hit", true));
        s.add(HologramCapture.command("gamemode creative @s"));     // not a threat while it settles
        s.add(onServer("player at the boar", server -> camera(me(server), at(stage, 0.0, EYE, 2.2), at(stage, 0, 0.6, 0))));
        s.add(HologramCapture.waitTicks(30));
        List<String> timeline = new ArrayList<>();
        int[] swings = {0};
        s.add(onServer("landed hit", server -> {
            ForestBoarEntity b = tagged(server, "hit");
            ServerPlayer p = me(server);
            if (b == null) return;
            // Survival and the hit in the same server tick, so it is the hit (not detection) that startles it.
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
            b.forceNextReaction(true);
            HologramCapture.log("info: before the hit the boar is " + b.getBehavior() + " (a creative player beside it is ignored)");
            // Totality resolves a player's hit as an attack roll; swing until one lands, as a player would.
            while (b.getLastHurtByMob() != p && swings[0]++ < 20) {
                b.hurtServer((ServerLevel) b.level(), b.damageSources().playerAttack(p), 1.0F);
                b.invulnerableTime = 0;
            }
            HologramCapture.log("info: " + swings[0] + " swing(s) until a hit landed; now " + b.getBehavior());
        }));
        for (int i = 0; i < 60; i++) {
            s.add(onServer("track", server -> {
                ForestBoarEntity b = tagged(server, "hit");
                String st = b == null ? "gone" : b.getBehavior().name();
                synchronized (timeline) { if (timeline.isEmpty() || !timeline.getLast().equals(st)) timeline.add(st); }
            }));
            if (i % 2 == 0) s.add(HologramCapture.screenshot(String.format(Locale.ROOT, "D3_hit_reaction_%02d", i / 2)));
        }
        // A second hit while it flees: it keeps fleeing (no melee fight).
        s.add(onServer("second hit", server -> {
            ForestBoarEntity b = tagged(server, "hit");
            if (b == null) return;
            ServerPlayer p = me(server);
            p.teleportTo((ServerLevel) p.level(), b.getX() - 1.5, b.getY(), b.getZ(), Set.of(), p.getYRot(), p.getXRot(), true);
            float before = b.getHealth();
            for (int k = 0; k < 20 && b.getHealth() >= before; k++) {
                b.hurtServer((ServerLevel) b.level(), b.damageSources().playerAttack(p), 1.0F);
                b.invulnerableTime = 0;
            }
        }));
        s.add(HologramCapture.waitTicks(40));
        s.add(onServer("verdict", server -> {
            ForestBoarEntity b = tagged(server, "hit");
            List<String> t;
            synchronized (timeline) { t = new ArrayList<>(timeline); }
            while (!t.isEmpty() && Behavior.valueOf(t.getFirst()).peaceful()) t.removeFirst();
            boolean ok = t.size() >= 2 && t.getFirst().equals("ALERT") && t.getLast().equals("FLEE") && (b == null || b.getBehavior() == Behavior.FLEE)
                    && t.stream().filter("CHARGE"::equals).count() <= 1;
            HologramCapture.log((ok ? "PASS" : "FAIL") + ": a landed hit startles it; one defensive charge at most, then it runs (no melee fight): " + t
                    + ", after a second hit while fleeing it is " + (b == null ? "gone" : b.getBehavior()));
        }));
    }

    private static void escape(List<Step> s, double[] stage) {
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(summon(stage, 0.0, -4.0, 0.0F, "escape", true));
        s.add(onServer("player", server -> camera(me(server), at(stage, 0.0, EYE, 3.0), at(stage, 0, 0.6, -4.0))));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.command("gamemode survival @s"));
        ForestBoarEntity[] ref = new ForestBoarEntity[1];
        Vec3[] last = new Vec3[1];
        List<String> checks = new ArrayList<>();
        boolean[] sealed = {false};
        int[] removedAt = {-1};
        int[] tick = {0};
        s.add(onServer("force flee", server -> {
            ref[0] = tagged(server, "escape");
            if (ref[0] != null) ref[0].forceNextReaction(false);
        }));
        // Watch it run (the player turns to follow it), and record the escape conditions every 10 ticks.
        for (int i = 0; i < 90; i++) {
            final int frameNo = i;
            s.add(onServer("escape watch", server -> {
                ForestBoarEntity b = ref[0];
                ServerPlayer p = me(server);
                tick[0] += 10;
                if (b == null) return;
                if (b.isRemoved()) {
                    if (removedAt[0] < 0) removedAt[0] = tick[0];
                    return;
                }
                last[0] = b.position();
                double d = b.distanceTo(p);
                boolean los = p.hasLineOfSight(b);
                String line = String.format(Locale.ROOT, "t=%ds %s dist=%.1f sight=%s", tick[0] / 20, b.getBehavior(), d, los);
                synchronized (checks) { checks.add(line); }
                if (!sealed[0]) {
                    Vec3 eye = p.getEyePosition();
                    camera(p, eye, b.position().add(0, 0.6, 0));
                    // Out in the open it stays in sight: once it is past the escape distance, the player shuts
                    // themself in a stone box, which breaks every line of sight.
                    if (d >= ForestBoarRules.ESCAPE_MIN_PLAYER_DISTANCE + 6) {
                        sealed[0] = true;
                        HologramCapture.log("info: escape: " + line + " — still present while in sight; sealing the player in");
                        server.getCommands().performPrefixedCommand(p.createCommandSourceStack(), "execute at @s run fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:stone hollow");
                    }
                }
            }));
            if (i < 40 && i % 2 == 0) s.add(HologramCapture.screenshot(String.format(Locale.ROOT, "D4_escape_%02d", frameNo / 2)));
            s.add(HologramCapture.waitTicks(i < 40 ? 9 : 10));
        }
        s.add(onServer("escape verdict", server -> {
            ForestBoarEntity b = ref[0];
            List<String> c;
            synchronized (checks) { c = new ArrayList<>(checks); }
            boolean removedQuietly = b != null && b.isRemoved() && b.getRemovalReason() == Entity.RemovalReason.DISCARDED
                    && b.hasEscaped() && !b.isDeadOrDying();
            // Loot check: no items where it vanished.
            int items = last[0] == null ? -1 : me(server).level().getEntitiesOfClass(ItemEntity.class, new AABB(BlockPos.containing(last[0])).inflate(4)).size();
            HologramCapture.log((removedQuietly && items == 0 ? "PASS" : "FAIL") + ": escape — removed silently "
                    + (b == null ? "(no boar)" : "(removed=" + b.isRemoved() + ", reason " + b.getRemovalReason() + ", escaped=" + b.hasEscaped()
                    + ", dead=" + b.isDeadOrDying() + ")") + ", items where it vanished: " + items + ", about " + (removedAt[0] / 20) + " s in");
            // The brain's own record of the decision (the watcher above samples on other ticks).
            String record = b == null ? null : b.escapeRecord();
            java.util.regex.Matcher rm = record == null ? null : java.util.regex.Pattern.compile("fled (\\d+) ticks; nearest player ([0-9.]+|Infinity) blocks").matcher(record);
            boolean ok = rm != null && rm.find() && Integer.parseInt(rm.group(1)) >= ForestBoarRules.ESCAPE_MIN_FLEE_TICKS
                    && Double.parseDouble(rm.group(2)) >= ForestBoarRules.ESCAPE_MIN_PLAYER_DISTANCE;
            long seenChecks = c.stream().filter(line -> line.contains("sight=true")).count();
            HologramCapture.log((ok ? "PASS" : "FAIL") + ": escape decision: " + record + "; it was still there at all " + seenChecks
                    + " watcher checks while the player could see it (last watcher check: " + (c.isEmpty() ? "-" : c.getLast()) + ")");
            c.forEach(line -> HologramCapture.log("info: escape check " + line));
        }));
        s.add(HologramCapture.command("execute at @s run fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-2 ~-1 ~-2 ~2 ~-1 ~2 minecraft:grass_block"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f 0 20", stage[0], stage[1], stage[2] + 3)).tick(mc));
    }

    // ── E: death drops ──

    private static void drops(List<Step> s) {
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.command("kill @e[type=minecraft:item]"));
        int[] kills = {0};
        List<String> counts = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            s.add(HologramCapture.command("execute at @s run summon totality:forest_boar ~ ~ ~3 {NoAI:1b,Tags:[\"drop\"]}"));
            s.add(HologramCapture.waitTicks(2));
            s.add(HologramCapture.command("kill @e[type=totality:forest_boar,tag=drop]"));
            s.add(HologramCapture.waitTicks(25));
            s.add(onServer("count drops", server -> {
                ServerPlayer p = me(server);
                List<ItemEntity> items = p.level().getEntitiesOfClass(ItemEntity.class, new AABB(p.blockPosition().south(3)).inflate(3, 1.5, 3),
                        it -> it.getAge() <= 30);
                int meat = 0, other = 0;
                for (ItemEntity it : items) {
                    if (BuiltInRegistries.ITEM.getKey(it.getItem().getItem()).toString().equals("totality:raw_meat")) meat += it.getItem().getCount();
                    else other += it.getItem().getCount();
                }
                synchronized (counts) { counts.add(meat + (other > 0 ? "+" + other + " other" : "")); }
                kills[0]++;
                if (kills[0] < 20) items.forEach(ItemEntity::discard);
            }));
        }
        s.add(HologramCapture.waitTicks(5));
        s.add(mc -> {
            synchronized (counts) {
                boolean ok = counts.size() == 20 && counts.stream().allMatch(c -> c.equals("1") || c.equals("2"));
                HologramCapture.log((ok ? "PASS" : "FAIL") + ": 20 deaths, Raw Meat per death (nothing else): " + counts);
            }
            return true;
        });
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(hud(true));
        s.add(HologramCapture.command("execute at @s run tp @s ~ ~1.1 ~1.6 facing ~ ~ ~3"));
        s.add(HologramCapture.waitTicks(25));
        s.add(HologramCapture.screenshot("E0_raw_meat_drop"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.command("kill @e[type=minecraft:item]"));
    }

    // ── G: targeting regions (F3+B), real crosshair picks, real attacks, navigation ──

    private static boolean hitboxesShown() {
        return Minecraft.getInstance().debugEntries.isCurrentlyEnabled(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
    }

    private static Step showHitboxes(boolean on) {
        return HologramCapture.run("hitboxes " + on, () -> {
            if (hitboxesShown() != on) Minecraft.getInstance().debugEntries.toggleStatus(net.minecraft.client.gui.components.debug.DebugScreenEntries.ENTITY_HITBOXES);
        });
    }

    /** A point in the boar's frame (forward, lateral, up) to world, for a boar at the stage centre with the given yaw. */
    private static Vec3 local(double[] stage, float yaw, double fwd, double lat, double up) {
        double s = Math.sin(Math.toRadians(yaw)), c = Math.cos(Math.toRadians(yaw));
        return new Vec3(stage[0] - s * fwd + c * lat, stage[1] + up, stage[2] + c * fwd + s * lat);
    }

    /**
     * One targeting probe: the player's eye 2.4 blocks from {@code target} along the boar-frame direction
     * {@code (dFwd, dLat, dUp)}, looking straight at it; then what the REAL client pick selected, compared with what
     * the plain square box would have given; and, for expected hits, a REAL attack through the server's validation.
     */
    private static void probe(List<Step> s, double[] stage, float yaw, String name, double[] target, double[] dir, boolean expectHit) {
        Vec3[] eye = new Vec3[1];
        Vec3[] at = new Vec3[1];
        s.add(onServer("aim " + name, server -> {
            at[0] = local(stage, yaw, target[0], target[1], target[2]);
            Vec3 d = local(stage, yaw, dir[0], dir[1], dir[2]).subtract(local(stage, yaw, 0, 0, 0)).normalize();
            eye[0] = at[0].add(d.scale(2.4));
            ServerPlayer p = me(server);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            camera(p, eye[0], at[0]);
        }));
        s.add(HologramCapture.waitTicks(4));
        String shot = "G_pick_" + name;
        s.add(HologramCapture.screenshot(shot));
        s.add(mc -> {
            var picked = mc.crosshairPickEntity;
            ForestBoarEntity boar = clientNearest(mc);
            boolean hit = picked instanceof ForestBoarEntity;
            Vec3 from = mc.player.getEyePosition(), to = from.add(mc.player.getLookAngle().scale(mc.player.entityInteractionRange()));
            boolean squareBox = boar != null && boar.getBoundingBox().clip(from, to).isPresent();
            HologramCapture.log(String.format(Locale.ROOT, "%s: crosshair on %s — picked %s (expected %s); the plain %.1fx%.1f box would %s",
                    hit == expectHit ? "PASS" : "FAIL", name, hit ? "the boar" : "nothing", expectHit ? "the boar" : "nothing",
                    boar == null ? 0 : boar.getBbWidth(), boar == null ? 0 : boar.getBbHeight(), squareBox ? "hit" : "miss"));
            if (hit && expectHit) {
                // A real attack: the client's attack packet, validated by the server like any other.
                mc.gameMode.attack(mc.player, picked);
                mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
            }
            return true;
        });
        if (expectHit) {
            // Totality resolves a player's hit as an attack roll: swing again (as a player would) until one lands.
            for (int retry = 0; retry < 3; retry++) {
                s.add(HologramCapture.waitTicks(12));
                s.add(mc -> {
                    MinecraftServer server = mc.getSingleplayerServer();
                    ForestBoarEntity b = server == null ? null : tagged(server, "hitbox");
                    if (b != null && b.getHealth() >= b.getMaxHealth() && mc.crosshairPickEntity instanceof ForestBoarEntity picked) {
                        mc.gameMode.attack(mc.player, picked);
                        mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                        HologramCapture.log("info: swing again at " + name);
                    }
                    return true;
                });
            }
            s.add(HologramCapture.waitTicks(6));
            s.add(onServer("attack result " + name, server -> {
                ForestBoarEntity b = tagged(server, "hitbox");
                boolean landed = b != null && (b.getLastHurtByMob() == me(server) || b.getHealth() < b.getMaxHealth());
                HologramCapture.log((landed ? "PASS" : "INFO") + ": attack on " + name + (landed ? " reached the server and landed"
                        : " did not land (Totality's attack roll can miss)") + (b == null ? "" : String.format(Locale.ROOT, " — health %.1f/%.1f", b.getHealth(), b.getMaxHealth())));
                if (b != null) {
                    b.setHealth(b.getMaxHealth());
                    b.setLastHurtByMob(null);
                    b.invulnerableTime = 0;
                }
            }));
        }
    }

    private static void hitbox(List<Step> s, double[] stage) {
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(summon(stage, 0, 0, 0.0F, "hitbox", false));        // faces +z
        s.add(HologramCapture.waitTicks(10));
        s.add(script("hitbox", Behavior.FLEE));                   // rest pose (no layer), as in the model views
        s.add(showHitboxes(true));
        s.add(hud(true));
        s.add(fov(50));
        s.add(HologramCapture.command("gamemode spectator @s"));
        String[] names = {"H0_front", "H1_side", "H2_rear", "H3_three_quarter", "H4_top"};
        double[][] cams = {{2.9, 0.0, 0.7}, {0.0, 3.0, 0.8}, {-3.0, 0.0, 0.8}, {2.2, -2.2, 1.9}, {0.05, 0.0, 3.6}};
        for (int i = 0; i < names.length; i++) {
            double[] c = cams[i];
            s.add(onServer("camera " + names[i], server -> camera(me(server), local(stage, 0, c[0], c[1], c[2]), local(stage, 0, 0.05, 0, 0.6))));
            s.add(HologramCapture.waitTicks(8));
            s.add(HologramCapture.screenshot("G_" + names[i]));
        }
        // Turned 45 degrees: the square box's corners stick out into empty air; the cyan regions turn with the boar.
        s.add(onServer("turn 45", server -> {
            ForestBoarEntity b = tagged(server, "hitbox");
            if (b != null) { b.setYRot(45.0F); b.setYBodyRot(45.0F); b.setYHeadRot(45.0F); }
        }));
        s.add(onServer("camera top 45", server -> camera(me(server), local(stage, 0, 0.05, 0, 3.6), local(stage, 0, 0, 0, 0.6))));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.screenshot("G_H5_top_turned_45"));
        s.add(onServer("camera 3/4 45", server -> camera(me(server), local(stage, 0, 2.2, -2.2, 1.9), local(stage, 0, 0, 0, 0.6))));
        s.add(HologramCapture.waitTicks(8));
        s.add(HologramCapture.screenshot("G_H6_three_quarter_turned_45"));
        s.add(script("hitbox", Behavior.GRAZE));
        s.add(onServer("face +z again", server -> {
            ForestBoarEntity b = tagged(server, "hitbox");
            if (b != null) { b.setYRot(0.0F); b.setYBodyRot(0.0F); b.setYHeadRot(0.0F); }
        }));
        s.add(onServer("camera graze", server -> camera(me(server), local(stage, 0, 1.2, 2.6, 0.9), local(stage, 0, 0.4, 0, 0.4))));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("G_H7_grazing"));
        s.add(script("hitbox", Behavior.FLEE));
        s.add(HologramCapture.waitTicks(10));

        // Real picks and attacks (creative, flying, HUD on: crosshair and Mob HUD visible).
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(hud(false));
        s.add(fov(70));
        // name, target (fwd, lat, up), camera direction (fwd, lat, up), expected
        probe(s, stage, 0, "snout_from_side", new double[]{0.85, 0.0, 0.55}, new double[]{0.0, 1.0, 0.25}, true);
        probe(s, stage, 0, "snout_from_front", new double[]{0.9, 0.0, 0.55}, new double[]{1.0, -0.3, 0.3}, true);
        probe(s, stage, 0, "head_top_ear", new double[]{0.55, 0.3, 1.2}, new double[]{0.6, 0.4, 1.0}, true);
        probe(s, stage, 0, "flank", new double[]{0.0, 0.0, 0.75}, new double[]{0.0, 1.0, 0.2}, true);
        probe(s, stage, 0, "rump_from_side", new double[]{-0.65, 0.0, 0.8}, new double[]{0.0, 1.0, 0.2}, true);
        probe(s, stage, 0, "rump_from_behind", new double[]{-0.76, 0.0, 0.8}, new double[]{-1.0, 0.3, 0.3}, true);
        probe(s, stage, 0, "empty_beside_snout", new double[]{0.85, 0.72, 0.55}, new double[]{1.0, 0.0, 0.1}, false);
        probe(s, stage, 0, "empty_beside_rump", new double[]{-0.7, -0.72, 0.8}, new double[]{-1.0, 0.0, 0.1}, false);
        probe(s, stage, 0, "empty_above_back", new double[]{0.0, 0.0, 1.55}, new double[]{0.0, 1.0, 0.0}, false);
        // Turned 45 degrees: straight down onto the square box's corner, which is empty air beside the body.
        s.add(onServer("turn 45 for the corner probe", server -> {
            ForestBoarEntity b = tagged(server, "hitbox");
            if (b != null) { b.setYRot(45.0F); b.setYBodyRot(45.0F); b.setYHeadRot(45.0F); }
        }));
        s.add(HologramCapture.waitTicks(4));
        probe(s, stage, 45, "empty_square_box_corner_turned_45", new double[]{0.0, 0.6, 0.9}, new double[]{0.0, 0.05, 1.0}, false);
        probe(s, stage, 45, "snout_turned_45", new double[]{0.85, 0.0, 0.55}, new double[]{0.0, 1.0, 0.25}, true);
        s.add(onServer("face +z, graze", server -> {
            ForestBoarEntity b = tagged(server, "hitbox");
            if (b != null) { b.setYRot(0.0F); b.setYBodyRot(0.0F); b.setYHeadRot(0.0F); b.scriptForCapture(Behavior.GRAZE); }
        }));
        s.add(HologramCapture.waitTicks(20));
        probe(s, stage, 0, "grazing_snout_near_ground", new double[]{0.95, 0.0, 0.15}, new double[]{0.2, 1.0, 0.3}, true);

        // Walking, the regions follow (F3+B on, fixed side camera, every other tick).
        s.add(showHitboxes(true));
        s.add(hud(true));
        s.add(fov(45));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        Step cam = onServer("side camera", server -> camera(me(server), at(stage, 0.5, 0.75, 6.5), at(stage, 0.5, 0.55, 0)));
        crossing(s, stage, cam, "G_walk_hitboxes", -3.8, Behavior.WANDER, ForestBoarRules.WANDER_SPEED, 40, null, 0, 0);
        s.add(showHitboxes(false));
        s.add(hud(false));

        // Navigation: a wall with a one-block gap; the boar walks through it (its physical box is still 0.9 wide).
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:stone", (int) Math.floor(stage[0]) - 5,
                (int) stage[1], (int) Math.floor(stage[2]) + 3, (int) Math.floor(stage[0]) + 5, (int) stage[1] + 1, (int) Math.floor(stage[2]) + 3)).tick(mc));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", (int) Math.floor(stage[0]),
                (int) stage[1], (int) Math.floor(stage[2]) + 3, (int) Math.floor(stage[0]), (int) stage[1] + 1, (int) Math.floor(stage[2]) + 3)).tick(mc));
        s.add(summon(stage, 0, 0, 0.0F, "gap", true));
        s.add(script("gap", Behavior.WANDER));
        s.add(onServer("gap camera", server -> camera(me(server), at(stage, 3.5, 2.6, 3.0), at(stage, 0.0, 0.5, 3.0))));
        s.add(onServer("walk through the gap", server -> {
            ForestBoarEntity b = tagged(server, "gap");
            if (b != null) {
                boolean ok = b.getNavigation().moveTo(stage[0], stage[1], stage[2] + 7.0, ForestBoarRules.WANDER_SPEED);
                var path = b.getNavigation().getPath();
                HologramCapture.log("info: path through the one-block gap: " + ok + (path == null ? "" : ", reaches target " + path.canReach()));
            }
        }));
        for (int i = 0; i < 8; i++) {
            s.add(HologramCapture.waitTicks(20));
            if (i == 3) s.add(HologramCapture.screenshot("G_gap_passage"));
        }
        s.add(onServer("gap verdict", server -> {
            ForestBoarEntity b = tagged(server, "gap");
            boolean through = b != null && b.getZ() > stage[2] + 4.5;
            HologramCapture.log((through ? "PASS" : "FAIL") + ": the boar walked through a one-block-wide gap"
                    + (b == null ? "" : String.format(Locale.ROOT, " (now %.1f blocks past the wall line)", b.getZ() - (stage[2] + 3.0))));
        }));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", (int) Math.floor(stage[0]) - 5,
                (int) stage[1], (int) Math.floor(stage[2]) + 3, (int) Math.floor(stage[0]) + 5, (int) stage[1] + 1, (int) Math.floor(stage[2]) + 3)).tick(mc));
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
        s.add(HologramCapture.command("gamemode creative @s"));
    }

    // ── F: a real forest ──

    private static void forest(List<Step> s) {
        s.add(onServer("go to the nearest forest", server -> {
            ServerPlayer p = me(server);
            ServerLevel level = (ServerLevel) p.level();
            var found = level.findClosestBiome3d(h -> h.is(Biomes.FOREST), p.blockPosition(), 6400, 32, 64);
            if (found == null) {
                HologramCapture.log("FAIL: no forest found near the capture world");
                return;
            }
            BlockPos at = found.getFirst();
            BlockPos clearing = findClearing(level, at);
            BlockPos c = clearing != null ? clearing : at;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, c.getX(), c.getZ());
            p.teleportTo(level, c.getX() + 0.5, y + 0.1, c.getZ() + 0.5, Set.of(), 0, 25, true);
            HologramCapture.log("PASS: forest found at " + at.getX() + ", " + at.getZ() + (clearing == null ? "" : "; clearing at " + c.getX() + ", " + c.getZ()));
        }));
        s.add(HologramCapture.waitTicks(80));
        s.add(onServer("a small group on the forest floor", server -> {
            ServerPlayer p = me(server);
            ServerLevel level = (ServerLevel) p.level();
            int[][] offsets = {{0, 0}, {-2, -1}, {-1, -3}};
            for (int k = 0; k < offsets.length; k++) {
                int x = p.getBlockX() + offsets[k][0], z = p.getBlockZ() + offsets[k][1];
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                ForestBoarEntity b = ModEntities.FOREST_BOAR.create(level, EntitySpawnReason.COMMAND);
                if (b == null) continue;
                b.snapTo(x + 0.5, y, z + 0.5, k * 120f, 0f);
                level.addFreshEntity(b);
            }
        }));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(hud(true));
        s.add(onServer("forest camera", server -> {
            ServerPlayer p = me(server);
            ServerLevel level = (ServerLevel) p.level();
            double gy = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.getBlockX(), p.getBlockZ());
            double camX = p.getX() + 3.5, camZ = p.getZ() + 3.5;
            double camY = Math.max(gy, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(camX), (int) Math.floor(camZ))) + 0.9 + EYE;
            camera(p, new Vec3(camX, camY, camZ), new Vec3(p.getX(), gy + 0.6, p.getZ()));
        }));
        for (int i = 0; i < 8; i++) {
            s.add(HologramCapture.waitTicks(30));
            s.add(HologramCapture.screenshot(String.format(Locale.ROOT, "F0_forest_%02d", i)));
        }
        s.add(onServer("clear", ForestBoarCapture::discardBoars));
    }

    private static BlockPos findClearing(ServerLevel level, BlockPos around) {
        for (int r = 0; r <= 96; r += 2) {
            for (int dx = -r; dx <= r; dx += 2) {
                for (int dz = -r; dz <= r; dz += 2) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    BlockPos centre = around.offset(dx, 0, dz);
                    if (isClearing(level, centre)) return centre;
                }
            }
        }
        return null;
    }

    private static boolean isClearing(ServerLevel level, BlockPos centre) {
        for (int x = -4; x <= 4; x += 4) {
            for (int z = -4; z <= 4; z += 4) level.getChunk(centre.offset(x, 0, z));
        }
        if (!level.getBiome(centre).is(net.minecraft.tags.BiomeTags.IS_FOREST)) return false;
        int base = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX(), centre.getZ());
        int camGround = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, centre.getX() + 3, centre.getZ() + 3);
        if (Math.abs(camGround - base) > 2) return false;
        if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, centre.getX() + 3, centre.getZ() + 3) != camGround) return false;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                int cx = centre.getX() + x, cz = centre.getZ() + z;
                int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, cx, cz);
                if (Math.abs(ground - base) > 1) return false;
                if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, cx, cz) != ground) return false;
                if (!level.getBlockState(new BlockPos(cx, ground - 1, cz)).is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)) return false;
            }
        }
        return true;
    }
}
