package zcylas.totality.client.hologram.dev;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.portal.VisualPortalCommands;
import zcylas.totality.entity.portal.VisualPortalEntity;
import zcylas.totality.entity.portal.VisualPortalVfx;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Visual portal test footage for the opt-in development capture run (scene 50); inert in normal play. Portals are
 * opened and collapsed through the same server-side calls as /portaltest. Lifecycle sequences take a frame every game
 * tick (the animation's own resolution). Prefix {@code VP_} on every frame.
 */
final class VisualPortalCapture {

    private VisualPortalCapture() {}

    private static final double EYE = 1.62;

    private static String shot(String name) {
        return "VP_" + name;
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

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** Puts the (spectator) player's eyes at {@code eye}, looking at {@code at}. */
    private static Step camera(double[] stage, double ex, double ey, double ez, double ax, double ay, double az) {
        return onServer(server -> {
            Vec3 eye = at(stage, ex, ey, ez), look = at(stage, ax, ay, az);
            double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            ServerPlayer p = me(server);
            p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yawTo(eye, look), pitch, true);
        });
    }

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    /** Opens a portal standing on the stage floor at (dx, dz), facing the point (fx, fz). */
    private static Step open(double[] stage, double dx, double dz, double fx, double fz, float size) {
        return onServer(server -> {
            Vec3 centre = at(stage, dx, VisualPortalVfx.HALF_HEIGHT * size + 0.05, dz);
            VisualPortalCommands.spawn((ServerLevel) me(server).level(), centre, yawTo(centre, at(stage, fx, centre.y - stage[1], fz)), 0.0F, size);
        });
    }

    /** Opens a portal centred at (dx, dy, dz) with an explicit yaw / pitch (angled, floor or ceiling portals). */
    private static Step openAngled(double[] stage, double dx, double dy, double dz, float yaw, float pitch, float size) {
        return onServer(server -> VisualPortalCommands.spawn((ServerLevel) me(server).level(), at(stage, dx, dy, dz), yaw, pitch, size));
    }

    private static Step collapseAll() {
        return onServer(server -> all(server).forEach(VisualPortalEntity::collapse));
    }

    private static Step clearAll() {
        return onServer(server -> all(server).forEach(VisualPortalEntity::discard));
    }

    private static List<VisualPortalEntity> all(MinecraftServer server) {
        ServerPlayer p = me(server);
        return p.level().getEntitiesOfClass(VisualPortalEntity.class, p.getBoundingBox().inflate(96));
    }

    private static Step count(String label) {
        return mc -> {
            int client = mc.level.getEntitiesOfClass(VisualPortalEntity.class, mc.player.getBoundingBox().inflate(96)).size();
            MinecraftServer server = mc.getSingleplayerServer();
            HologramCapture.log("info: " + label + ": " + client + " portal entities on the client, particles "
                    + mc.particleEngine.countParticles());
            if (server != null) server.execute(() -> HologramCapture.log("info: " + label + ": " + all(server).size() + " on the server"));
            return true;
        };
    }

    private static void frames(List<Step> s, String name, int count, int every) {
        for (int i = 0; i < count; i++) {
            s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "%s_%02d", name, i))));
            if (every > 1) s.add(HologramCapture.waitTicks(every - 1));
        }
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("hide HUD", () -> {
            var hud = net.minecraft.client.Minecraft.getInstance().gui.hud;
            if (!hud.isHidden()) hud.toggle();
        }));
        s.add(HologramCapture.run("remember the stage", () -> {
            var p = net.minecraft.client.Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        // A flat plains meadow (61 x 91; fills split under the 32768-block limit) and a mossy stone "workshop" wall
        // far behind the main portal spot.
        for (int[] z : new int[][]{{-40, -18}, {-17, 5}, {6, 28}, {29, 50}}) {
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-30 ~ ~%d ~30 ~12 ~%d minecraft:air", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-30 ~13 ~%d ~30 ~30 ~%d minecraft:air", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-30 ~-2 ~%d ~30 ~-2 ~%d minecraft:dirt", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-30 ~-1 ~%d ~30 ~-1 ~%d minecraft:grass_block", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fillbiome ~-30 ~-3 ~%d ~30 ~12 ~%d minecraft:plains", z[0], z[1])));
        }
        s.add(HologramCapture.command("execute at @s run fill ~-7 ~ ~14 ~7 ~5 ~14 minecraft:mossy_stone_bricks"));
        s.add(HologramCapture.command("kill @e[type=!minecraft:player,distance=..80]"));   // wandering animals, dropped items
        s.add(HologramCapture.waitTicks(40));

        // ── 1. Opening: every game tick (the animation's own resolution) ──
        s.add(camera(stage, 0, 1.7, 1.0, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(10));
        s.add(open(stage, 8, 6, 0, 0, 1.0F));                    // warm-up portal off to the side (loads textures)
        s.add(HologramCapture.waitTicks(30));
        s.add(clearAll());
        s.add(HologramCapture.waitTicks(20));
        s.add(open(stage, 0, 6, 0, 0, 1.0F));
        frames(s, "open", 28, 1);

        // ── 2. Stable loop, close-up and front (2 s = 1.25 loops) ──
        s.add(camera(stage, 0, 1.3, 3.2, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(10));
        frames(s, "stable_close", 40, 1);
        s.add(camera(stage, 0, 1.6, 0.0, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "stable_front", 12, 2);

        // ── 3. Presentation views: 3/4, side (thin), from behind (mirrored) ──
        s.add(camera(stage, 3.2, 1.6, 2.8, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "view_three_quarter", 4, 3);
        s.add(camera(stage, 4.5, 1.4, 6.0, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "view_side", 4, 3);
        s.add(camera(stage, 1.2, 1.5, 9.8, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "view_back", 4, 3);

        // ── 4. Scale: a Steve stand-in (1.8 blocks) beside the 2.4-block portal ──
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,           // built when run: needs the stage
                "summon minecraft:mannequin %.2f %.2f %.2f {Rotation:[180f,0f],Tags:[\"vp_scale\"],profile:{texture:\"minecraft:entity/player/wide/steve\",model:\"wide\"}}",
                stage[0] - 1.6, stage[1], stage[2] + 6.0)).tick(mc));
        s.add(camera(stage, -0.6, 1.3, 1.2, -0.6, 1.2, 6));
        s.add(HologramCapture.waitTicks(10));
        frames(s, "scale", 4, 3);
        s.add(onServer(server -> me(server).level().getEntities((net.minecraft.world.entity.Entity) null,   // discard: no death
                me(server).getBoundingBox().inflate(96), e -> e.entityTags().contains("vp_scale")).forEach(e -> e.discard())));  // pose or damage number

        // ── 5. Gameplay distances ──
        s.add(camera(stage, 0, 1.7, -6, 0, 1.3, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "distance_12", 4, 4);
        s.add(camera(stage, 0, 2.2, -18, 0, 1.3, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "distance_24", 4, 4);
        s.add(camera(stage, 0, 3.0, -34, 0, 1.3, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "distance_40", 4, 4);

        // ── 6. Collapse, every game tick, then a check that nothing is left ──
        s.add(camera(stage, 0, 1.7, 1.0, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(10));
        s.add(count("before collapse"));
        s.add(collapseAll());
        frames(s, "collapse", 40, 1);
        s.add(HologramCapture.waitTicks(20));
        s.add(count("after collapse"));

        // ── 7. Lifecycle from a gameplay distance (8 blocks): open, hold, collapse ──
        s.add(camera(stage, 1.5, 1.7, -2.0, 0, 1.25, 6));
        s.add(HologramCapture.waitTicks(5));
        s.add(open(stage, 0, 6, 0, 0, 1.0F));
        frames(s, "lifecycle_a", 50, 1);
        s.add(collapseAll());
        frames(s, "lifecycle_b", 36, 1);
        s.add(HologramCapture.waitTicks(20));
        s.add(count("after the lifecycle"));

        // ── 8. Several portals at once: sizes and orientations (upright, big, small, hovering tilted, on the floor) ──
        s.add(open(stage, 0, 10, 0, 0, 1.0F));
        s.add(open(stage, -5, 9, 0, 0, 0.6F));
        s.add(open(stage, 5.5, 11, 0, 0, 1.6F));
        s.add(openAngled(stage, -3.2, 2.4, 6.5, 205.0F, -30.0F, 0.9F));     // hovering, tilted back
        s.add(openAngled(stage, 3.0, 0.08, 5.0, 180.0F, -90.0F, 0.8F));     // lying on the floor, facing up
        s.add(HologramCapture.waitTicks(30));
        s.add(camera(stage, 0, 5.5, -3.0, 0, 1.0, 8));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "multi", 6, 3);
        s.add(count("five portals open"));
        s.add(camera(stage, 0.5, 4.2, 1.5, 0.5, 0.8, 6));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "multi_angled_floor", 4, 3);

        // ── 9. Night ──
        s.add(HologramCapture.command("time set 18000"));
        s.add(camera(stage, 0, 5.5, -3.0, 0, 1.0, 8));
        s.add(HologramCapture.waitTicks(10));
        frames(s, "night_multi", 4, 3);
        s.add(camera(stage, 1.0, 1.4, 7.0, 0, 1.25, 10));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "night_close", 8, 2);
        s.add(camera(stage, 0, 2.2, -12, 0, 1.3, 10));
        s.add(HologramCapture.waitTicks(5));
        frames(s, "night_distance_22", 4, 3);
        s.add(camera(stage, 0, 5.5, -3.0, 0, 1.0, 8));
        s.add(HologramCapture.waitTicks(5));
        s.add(collapseAll());
        frames(s, "night_collapse", 30, 1);
        s.add(HologramCapture.waitTicks(30));
        s.add(count("after all collapsed"));

        // ── 10. Removal without the collapse (/portaltest clear, /kill): gone at once, nothing left behind ──
        s.add(HologramCapture.command("time set 6000"));
        s.add(open(stage, 0, 6, 0, 0, 1.0F));
        s.add(HologramCapture.waitTicks(40));
        s.add(clearAll());
        s.add(HologramCapture.waitTicks(40));
        s.add(count("after clear"));
        s.add(HologramCapture.run("show HUD", () -> {
            var hud = net.minecraft.client.Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
        return s;
    }
}
