package zcylas.totality.client.hologram.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.gate.GatePalette;
import zcylas.totality.entity.gate.SoloGateCommands;
import zcylas.totality.entity.gate.SoloGateEntity;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Creative Test K footage for the opt-in capture run (inert in normal play): scene 63, the Solo Leveling Normal Gate
 * ({@code SG_}). A Blue Normal Gate and a Red Gate open side by side (one renderer, one texture set, two palettes),
 * are photographed stable from the front, side and three-quarter, close up, beside a player for scale, pulsing, in
 * daylight and at night, among four more gates, and closing; then nothing may be left on client or server.
 */
final class GateCapture {

    private GateCapture() {}

    private static final double EYE = 1.62;
    private static final UUID SCALE_ID = UUID.fromString("7a6f3c10-0000-4000-8000-00000000a0b2");
    private static ServerPlayer standIn;

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
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
        for (int[] z : new int[][]{{-8, 6}, {7, 20}}) {
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-14 ~ ~%d ~14 ~12 ~%d minecraft:air", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-14 ~-2 ~%d ~14 ~-1 ~%d minecraft:grass_block", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fillbiome ~-14 ~-2 ~%d ~14 ~12 ~%d minecraft:plains", z[0], z[1])));
        }
        s.add(here(stage, "kill @e[type=!minecraft:player,distance=..50]"));
        s.add(camera(stage, 0, 2.0, -1.5, 0, 1.9, 9));
        s.add(HologramCapture.waitTicks(30));

        // Opening: both at once, a frame every tick.
        s.add(onServer(server -> {
            SoloGateCommands.spawn(server.overworld(), at(stage, -2.8, SoloGateEntity.CENTRE_HEIGHT, 9), 180, GatePalette.BLUE);
            SoloGateCommands.spawn(server.overworld(), at(stage, 2.8, SoloGateEntity.CENTRE_HEIGHT, 9), 180, GatePalette.RED);
        }));
        frames(s, "SG_01_opening", 44, 1);
        s.add(HologramCapture.check("two gates exist on the client: one BLUE, one RED (independent palette instances)", () -> {
            List<SoloGateEntity> g = clientGates();
            return g.size() == 2 && g.stream().anyMatch(e -> e.palette() == GatePalette.BLUE) && g.stream().anyMatch(e -> e.palette() == GatePalette.RED);
        }));
        s.add(HologramCapture.check("both reached the stable phase", () -> clientGates().stream().allMatch(e -> e.phase() == SoloGateEntity.Phase.STABLE)));
        s.add(HologramCapture.check("palettes are independent and immutable records (the blue one is still the reference blue)", () ->
                GatePalette.BLUE.body() == 0x3A7DFF && GatePalette.RED.body() == 0xE22A22 && GatePalette.BLUE != GatePalette.RED));

        // Stable: views.
        view(s, stage, "SG_02_stable_front", 0, 2.0, -1.5, 0, 1.9, 9);
        frames(s, "SG_03_stable_loop", 24, 2);
        view(s, stage, "SG_04_three_quarter", -8.5, 2.2, 2.0, 0, 1.9, 9);
        view(s, stage, "SG_05_side", -10.5, 1.8, 9, 0, 1.9, 9);
        view(s, stage, "SG_06_side_close", -6.5, 1.7, 9.3, -2.8, 1.6, 9);
        view(s, stage, "SG_07_close_blue", -2.8, 1.7, 5.4, -2.8, 1.95, 9);
        view(s, stage, "SG_08_close_red", 2.8, 1.7, 5.4, 2.8, 1.95, 9);
        view(s, stage, "SG_09_from_behind", 0, 2.0, 16, 0, 1.9, 9);
        // Scale: a player stand-in between the gates.
        s.add(onServer(server -> {
            ServerLevel level = server.overworld();
            standIn = TotalityFakePlayer.create(level, new GameProfile(SCALE_ID, "Scale"));
            standIn.setGameMode(GameType.SURVIVAL);
            standIn.snapTo(stage[0] - 0.9, stage[1], stage[2] + 8.3, 180, 0);
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(standIn)));
            level.addNewPlayer(standIn);
        }));
        view(s, stage, "SG_10_player_scale", -1.0, 1.9, 2.5, -1.4, 1.5, 9);
        s.add(HologramCapture.run("fps", () -> HologramCapture.log("SG fps with 2 gates: " + Minecraft.getInstance().getFps())));

        // The entry/distortion reaction (visual only).
        s.add(onServer(server -> gates(server).forEach(SoloGateEntity::pulse)));
        frames(s, "SG_11_pulse", 26, 2);
        s.add(HologramCapture.check("the pulse moved nothing: the stand-in and the gates stay where they were", () ->
                Math.abs(serverNow(() -> standIn.getX()) - (stage[0] - 0.9)) < 1e-6 && clientGates().size() == 2));

        // Night.
        s.add(HologramCapture.command("time set 18000"));
        view(s, stage, "SG_12_night_front", 0, 2.0, -1.5, 0, 1.9, 9);
        view(s, stage, "SG_13_night_three_quarter", 7.5, 2.4, 2.5, 0, 1.9, 9);
        view(s, stage, "SG_14_night_close_blue", -2.8, 1.7, 5.4, -2.8, 1.95, 9);
        // Several at once.
        s.add(onServer(server -> {
            for (int i = 0; i < 4; i++) {
                SoloGateCommands.spawn(server.overworld(), at(stage, -8.4 + i * 5.6, SoloGateEntity.CENTRE_HEIGHT, 16), 180,
                        i % 2 == 0 ? GatePalette.RED : GatePalette.BLUE);
            }
        }));
        s.add(HologramCapture.waitTicks(50));
        view(s, stage, "SG_15_six_gates_night", 0, 3.4, -4.5, 0, 1.6, 12);
        s.add(HologramCapture.run("fps", () -> HologramCapture.log("SG fps with 6 gates: " + Minecraft.getInstance().getFps())));
        s.add(HologramCapture.command("time set 6000"));
        view(s, stage, "SG_16_six_gates_day", 0, 3.4, -4.5, 0, 1.6, 12);

        // Closing: everything at once, a frame every tick.
        s.add(onServer(server -> removeStandIn(server)));
        s.add(camera(stage, 0, 2.0, -1.5, 0, 1.9, 9));
        s.add(HologramCapture.waitTicks(10));
        s.add(onServer(server -> gates(server).forEach(SoloGateEntity::close)));
        frames(s, "SG_17_closing", 40, 1);
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("SG_18_after_cleanup"));
        s.add(HologramCapture.check("cleanup: no gate left on the client or the server", () ->
                clientGates().isEmpty() && serverNow(() -> gates(Minecraft.getInstance().getSingleplayerServer()).size()) == 0));
        s.add(HologramCapture.run("show HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
        s.add(HologramCapture.command("gamemode creative @s"));
        return s;
    }

    private static List<SoloGateEntity> clientGates() {
        List<SoloGateEntity> out = new ArrayList<>();
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) if (e instanceof SoloGateEntity g) out.add(g);
        return out;
    }

    private static List<SoloGateEntity> gates(MinecraftServer server) {
        return server.overworld().getEntitiesOfClass(SoloGateEntity.class, AABB.ofSize(me(server).position(), 120, 120, 120));
    }

    private static void removeStandIn(MinecraftServer server) {
        if (standIn == null) return;
        ((ServerLevel) standIn.level()).removePlayerImmediately(standIn, Entity.RemovalReason.DISCARDED);
        server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(SCALE_ID)));
        standIn = null;
    }

    private static <T> T serverNow(Supplier<T> query) {
        return Minecraft.getInstance().getSingleplayerServer().submit(query::get).join();
    }

    private static void frames(List<Step> s, String prefix, int count, int every) {
        int[] tick = {0};
        s.add(mc -> {
            if (tick[0] % every == 0) HologramCapture.screenshot(String.format(Locale.ROOT, "%s_%03d", prefix, tick[0])).tick(mc);
            return ++tick[0] >= count;
        });
    }

    private static void view(List<Step> s, double[] stage, String name, double ex, double ey, double ez, double lx, double ly, double lz) {
        s.add(camera(stage, ex, ey, ez, lx, ly, lz));
        s.add(HologramCapture.waitTicks(12));
        s.add(HologramCapture.screenshot(name));
    }

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static Step camera(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> {
            Vec3 eye = at(stage, ex, ey, ez), look = at(stage, lx, ly, lz);
            double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            ServerPlayer p = me(server);
            p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
        });
    }

    private static Step here(double[] stage, String command) {
        return mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.3f %.3f %.3f run %s",
                stage[0], stage[1], stage[2], command)).tick(mc);
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
}
