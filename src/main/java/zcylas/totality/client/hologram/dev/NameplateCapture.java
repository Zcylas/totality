package zcylas.totality.client.hologram.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.TargetNameplate;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.entity.npc.BankerNpcEntity;
import zcylas.totality.init.ModEntities;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Nameplate fix footage for the opt-in capture run (inert in normal play): scene 62 ({@code NP_}). A generic Banker (a
 * Totality NPC with a random name, like the "Lark" of the report) looked at from the front and at an angle, and not
 * looked at; a named zombie (no Totality replacement yet) and a named armor stand
 * (never given the custom plate) as the ordinary-nametag control; a second player (a stand-in) standing and sneaking,
 * whose vanilla nametag must be untouched.
 */
final class NameplateCapture {

    private NameplateCapture() {}

    private static final double EYE = 1.62;
    private static final UUID ROWAN_ID = UUID.fromString("7a6f3c10-0000-4000-8000-00000000a0a1");
    private static ServerPlayer rowan;

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        s.add(HologramCapture.command("gamerule spawn_mobs false"));
        s.add(HologramCapture.command("gamerule send_command_feedback false"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(HologramCapture.run("remember the stage", () -> {
            var p = Minecraft.getInstance().player;
            stage[0] = Math.floor(p.getX()) + 0.5;
            stage[1] = Math.floor(p.getY());
            stage[2] = Math.floor(p.getZ()) + 0.5;
        }));
        s.add(here(stage, "fill ~-10 ~ ~-4 ~10 ~10 ~16 minecraft:air"));
        s.add(here(stage, "fill ~-10 ~-1 ~-4 ~10 ~-1 ~16 minecraft:grass_block"));
        s.add(here(stage, "kill @e[type=!minecraft:player,distance=..40]"));
        // a generic Banker spawned the ordinary way (finalizeSpawn runs), so it gets its own random name (as "Lark" did)
        s.add(onServer(server -> {
            BankerNpcEntity banker = ModEntities.BANKER.spawn(server.overworld(), BlockPos.containing(stage[0], stage[1], stage[2] + 6), EntitySpawnReason.SPAWN_ITEM_USE);
            if (banker == null) return;
            banker.setNoAi(true);
            banker.setPersistenceRequired();
            banker.snapTo(stage[0], stage[1], stage[2] + 6, 180, 0);
            banker.setYHeadRot(180);
        }));
        s.add(here(stage, "summon minecraft:zombie ~-7 ~ ~8 {CustomName:\"Bram\",CustomNameVisible:1b,NoAI:1b,PersistenceRequired:1b,Rotation:[180f,0f]}"));
        s.add(here(stage, "summon minecraft:armor_stand ~4 ~ ~6 {CustomName:\"Marker\",CustomNameVisible:1b,Rotation:[180f,0f]}"));
        s.add(onServer(server -> {
            ServerLevel level = server.overworld();
            rowan = TotalityFakePlayer.create(level, new GameProfile(ROWAN_ID, "Rowan"));
            rowan.setGameMode(GameType.SURVIVAL);
            rowan.snapTo(stage[0] - 4, stage[1], stage[2] + 6, 180, 0);
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(rowan)));
            level.addNewPlayer(rowan);
        }));
        s.add(HologramCapture.waitTicks(40));

        // The Banker: looked at from the front, then at an angle (the plate is on it), then not looked at.
        shot(s, stage, "NP_01_banker_front", 0, EYE, 1.5, 0, 1.3, 6);
        s.add(HologramCapture.run("log banker", () -> HologramCapture.log("NP banker's random name: " + describe(banker()))));
        s.add(HologramCapture.check("front: the custom plate is on the Banker, showing its own random name", () ->
                banker() != null && TargetNameplate.shownTarget() == banker() && !banker().getName().getString().isEmpty()));
        s.add(HologramCapture.check("front: the Banker's vanilla nametag is suppressed (one nameplate)", () -> TargetNameplate.replacesVanillaName(banker())));
        shot(s, stage, "NP_02_banker_front_close", 0, 1.7, 3.6, 0, 1.6, 6);
        shot(s, stage, "NP_03_banker_angled", 2.6, 1.9, 3.4, 0, 1.4, 6);
        shot(s, stage, "NP_04_banker_side", 3.2, 1.7, 6, 0, 1.5, 6);
        s.add(HologramCapture.check("angled/side: the custom plate stays on the Banker", () -> TargetNameplate.shownTarget() == banker()));
        shot(s, stage, "NP_05_looking_away_from_banker", 0, EYE, 1.5, 0, 4.2, 6);
        s.add(HologramCapture.check("looking away (plate gone): the Banker's vanilla nametag stays suppressed (not restored)", () ->
                TargetNameplate.shownTarget() == null && TargetNameplate.replacesVanillaName(banker())));
        s.add(HologramCapture.check("a named vanilla mob not looked at (no Totality replacement yet) keeps its vanilla nametag", () ->
                !TargetNameplate.replacesVanillaName(client("Bram"))));
        // Controls: the named armor stand never gets the custom plate; the second player keeps its vanilla nametag.
        shot(s, stage, "NP_06_named_armor_stand", 3.0, EYE, 2.5, 4, 1.2, 6);
        s.add(HologramCapture.check("a named armor stand never gets the custom plate", () -> TargetNameplate.shownTarget() == null
                || !named(TargetNameplate.shownTarget(), "Marker")));
        s.add(HologramCapture.check("the armor stand keeps its vanilla nametag", () -> !TargetNameplate.replacesVanillaName(client("Marker"))));
        shot(s, stage, "NP_07_other_player", -3.0, EYE, 2.5, -4, 1.4, 6);
        s.add(HologramCapture.check("another player never gets the custom plate", () -> TargetNameplate.shownTarget() == null
                || !named(TargetNameplate.shownTarget(), "Rowan")));
        s.add(HologramCapture.check("the other player keeps its vanilla nametag", () -> !TargetNameplate.replacesVanillaName(client("Rowan"))));
        s.add(onServer(server -> rowan.setShiftKeyDown(true)));
        shot(s, stage, "NP_08_other_player_sneaking", -3.0, EYE, 2.5, -4, 1.4, 6);
        s.add(onServer(server -> rowan.setShiftKeyDown(false)));
        shot(s, stage, "NP_09_all", 0, 2.2, 0.0, 0, 1.2, 6);
        s.add(HologramCapture.run("log", () -> HologramCapture.log("NP shown target at the end: " + describe(TargetNameplate.shownTarget()))));

        s.add(onServer(server -> {
            if (rowan == null) return;
            ((ServerLevel) rowan.level()).removePlayerImmediately(rowan, Entity.RemovalReason.DISCARDED);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(ROWAN_ID)));
            rowan = null;
        }));
        s.add(here(stage, "kill @e[type=!minecraft:player,distance=..40]"));
        s.add(HologramCapture.command("gamemode creative @s"));
        return s;
    }

    private static void shot(List<Step> s, double[] stage, String name, double ex, double ey, double ez, double lx, double ly, double lz) {
        s.add(onServer(server -> {
            Vec3 eye = new Vec3(stage[0] + ex, stage[1] + ey, stage[2] + ez), look = new Vec3(stage[0] + lx, stage[1] + ly, stage[2] + lz);
            ServerPlayer p = me(server);
            double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yaw, pitch, true);
        }));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot(name));
        s.add(HologramCapture.run("log " + name, () -> HologramCapture.log(name + ": plate on " + describe(TargetNameplate.shownTarget()))));
    }

    /** The client's copy of the Banker (whatever its random name). */
    private static Entity banker() {
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) if (e instanceof BankerNpcEntity) return e;
        return null;
    }

    /** The client's copy of the named entity (what the renderer decides for). */
    private static Entity client(String name) {
        for (Entity e : Minecraft.getInstance().level.entitiesForRendering()) if (e.getName().getString().equals(name)) return e;
        return null;
    }

    private static boolean named(LivingEntity e, String name) {
        return e != null && e.getName().getString().equals(name);
    }

    private static String describe(Entity e) {
        return e == null ? "nothing" : e.getName().getString();
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
