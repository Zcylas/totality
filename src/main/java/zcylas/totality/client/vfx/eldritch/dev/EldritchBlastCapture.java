package zcylas.totality.client.vfx.eldritch.dev;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.magic.spell.SpellRegistry;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Eldritch Blast V2 footage for the opt-in development capture run (scene 70, prefix {@code EB2_}); inert in normal
 * play. Every cast goes through the real spell ({@code SpellRegistry.ELDRITCH_BLAST.onActivate}): a visible "Warlock"
 * stand-in player casts at an armor-stand target 14 blocks away while the camera player (a spectator) films it, and
 * the real player casts for the first-person shots. One frame per game tick under {@code /tick rate 4}.
 * {@code -Dtotality.eldritch.label=v1} names the frames of a baseline run made before the V2 change.
 */
final class EldritchBlastCapture {

    static final double EYE = 1.62;
    private static final String LABEL = System.getProperty("totality.eldritch.label", "v2");
    /** {@code -Dtotality.eldritch.audio=true}: only the real-time sound sequence (for recording the game's audio). */
    private static final boolean AUDIO = Boolean.getBoolean("totality.eldritch.audio");
    private static final UUID WARLOCK_ID = UUID.fromString("7d1e2a3b-0c4d-4e5f-8a6b-2c3d4e5f6a70");
    private static TotalityFakePlayer warlock;

    private EldritchBlastCapture() {}

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

    static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static float[] yawPitch(Vec3 eye, Vec3 at) {
        double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
        return new float[]{(float) Math.toDegrees(Math.atan2(-dx, dz)), (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)))};
    }

    static void camera(ServerPlayer p, Vec3 eye, Vec3 at) {
        float[] r = yawPitch(eye, at);
        p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), r[0], r[1], true);
    }

    static Step cam(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> camera(me(server), at(stage, ex, ey, ez), at(stage, lx, ly, lz)));
    }

    static Step fill(double[] stage, String region, String block) {
        return mc -> HologramCapture.command(String.format(Locale.ROOT, "execute positioned %.2f %.2f %.2f run fill %s %s",
                stage[0], stage[1], stage[2], region, block)).tick(mc);
    }

    /** The Warlock stand-in turns to {@code target} (its feet stay at the stage origin) and casts. */
    static Step warlockCasts(double[] stage, double[] target) {
        return onServer(server -> {
            aimWarlock(stage, target);
            SpellRegistry.ELDRITCH_BLAST.onActivate(warlock, null);
        });
    }

    static TotalityFakePlayer warlock() {
        return warlock;
    }

    static void aimWarlock(double[] stage, double[] target) {
        Vec3 eye = at(stage, 0, EYE, 0);
        float[] r = yawPitch(eye, at(stage, target[0], target[1], target[2]));
        warlock.snapTo(stage[0], stage[1], stage[2], r[0], r[1]);
        warlock.setYHeadRot(r[0]);
        warlock.setYBodyRot(r[0]);
    }

    /** The real player stands 2.5 blocks right of the Warlock, looks at {@code target} and casts (first and third person). */
    static Step playerCasts(double[] stage, double[] target) {
        return playerCasts(stage, target, null);
    }

    /**
     * As {@link #playerCasts(double[], double[])}, then looks at {@code lookAfter} (when not null): a third-person camera
     * follows the look line, so a beam flying straight along it stays hidden behind the caster; looking a little aside
     * after the cast (the bolt already has its aim) shows it.
     */
    static Step playerCasts(double[] stage, double[] target, double[] lookAfter) {
        return onServer(server -> {
            ServerPlayer p = me(server);
            Vec3 eye = at(stage, -2.5, EYE, 0);
            camera(p, eye, at(stage, target[0], target[1], target[2]));
            SpellRegistry.ELDRITCH_BLAST.onActivate(p, null);
            if (lookAfter != null) camera(p, eye, at(stage, lookAfter[0], lookAfter[1], lookAfter[2]));
        });
    }

    static void frames(List<Step> s, String name, int n) {
        for (int i = 0; i < n; i++) {
            String shot = String.format(Locale.ROOT, "EB2_%s_%s_%02d", LABEL, name, i);
            Step screenshot = HologramCapture.screenshot(shot);
            s.add(mc -> {
                HologramCapture.log("frame: " + shot + " " + EldritchBlastDev.frameState());
                return screenshot.tick(mc);
            });
        }
    }

    /** One whole spell: camera, cast, {@code n} frames (one per tick), then a pause at normal speed. */
    static void spell(List<Step> s, String name, Step camera, Step cast, int n) {
        s.add(HologramCapture.command("tick rate 4"));
        s.add(camera);
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("clear leftover particles", () -> Minecraft.getInstance().particleEngine.clearParticles()));
        s.add(cast);
        frames(s, name, n);
        s.add(HologramCapture.command("tick rate 20"));
        s.add(HologramCapture.waitTicks(30));
    }

    static void setup(List<Step> s, double[] stage) {
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
            EldritchBlastDev.captureStarted();
        }));
        // Flat stone-brick range along +z (split fills), a deepslate wall at z = 24.
        for (int[] z : new int[][]{{-12, 3}, {4, 18}, {19, 34}}) {
            s.add(fill(stage, String.format(Locale.ROOT, "~-16 ~ ~%d ~18 ~14 ~%d", z[0], z[1]), "minecraft:air"));
            s.add(fill(stage, String.format(Locale.ROOT, "~-16 ~-1 ~%d ~18 ~-1 ~%d", z[0], z[1]), "minecraft:stone_bricks"));
        }
        s.add(fill(stage, "~-6 ~ ~24 ~6 ~6 ~24", "minecraft:polished_deepslate"));
        s.add(mc -> HologramCapture.command(String.format(Locale.ROOT,
                "summon minecraft:armor_stand %.2f %.2f %.2f {NoGravity:1b,Invulnerable:1b,ShowArms:1b,Rotation:[180f,0f],Tags:[\"eb2_target\"]}",
                stage[0], stage[1], stage[2] + 14)).tick(mc));
        s.add(onServer(server -> {
            ServerLevel level = server.overworld();
            warlock = TotalityFakePlayer.create(level, new GameProfile(WARLOCK_ID, "Warlock"));
            warlock.setGameMode(GameType.SURVIVAL);
            warlock.snapTo(stage[0], stage[1], stage[2], 0, 0);
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(warlock)));
            level.addNewPlayer(warlock);
        }));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.run("hide HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (!hud.isHidden()) hud.toggle();
        }));
    }

    static void cleanup(List<Step> s) {
        s.add(HologramCapture.command("kill @e[type=minecraft:armor_stand,tag=eb2_target]"));
        s.add(onServer(server -> {
            if (warlock == null) return;
            ((ServerLevel) warlock.level()).removePlayerImmediately(warlock, Entity.RemovalReason.DISCARDED);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(WARLOCK_ID)));
            warlock = null;
        }));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("tick rate 20"));
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        setup(s, stage);
        double[] chest = {0, 1.25, 14};
        double[] wall = {3.5, 2.2, 24};

        // Warm-up (shaders, sounds), not captured.
        s.add(warlockCasts(stage, chest));
        s.add(HologramCapture.waitTicks(40));
        if (AUDIO) {
            EldritchBlastDev.audioScenes(s, stage, chest);
            cleanup(s);
            return s;
        }

        spell(s, "side", cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0), warlockCasts(stage, chest), 24);
        spell(s, "behind", cam(stage, 1.7, 2.7, -4.0, 0, 1.4, 10.0), warlockCasts(stage, chest), 24);
        spell(s, "target_close", cam(stage, -3.6, 1.9, 10.8, 0, 1.3, 14.0), warlockCasts(stage, chest), 24);
        spell(s, "caster_close", cam(stage, 2.6, 1.9, 2.6, 0, 1.4, 1.2), warlockCasts(stage, chest), 18);
        spell(s, "wall", cam(stage, -7.5, 3.0, 14.0, 2.0, 2.0, 21.0), warlockCasts(stage, wall), 24);
        spell(s, "far", cam(stage, -20.0, 9.0, -8.0, 0, 1.0, 10.0), warlockCasts(stage, chest), 24);
        EldritchBlastDev.v2Scenes(s, stage, chest);

        // First person (HUD visible), then third person from behind the real player.
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.run("show HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
        spell(s, "first_person", HologramCapture.waitTicks(1), playerCasts(stage, chest), 20);
        s.add(HologramCapture.run("hide HUD; third person", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (!hud.isHidden()) hud.toggle();
            Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }));
        spell(s, "third_person", HologramCapture.waitTicks(1), playerCasts(stage, new double[]{0, 1.25, 14}, new double[]{-9.0, 1.0, 14}), 20);
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(HologramCapture.command("gamemode spectator @s"));

        // Night.
        s.add(HologramCapture.command("time set 18000"));
        spell(s, "night_side", cam(stage, -8.5, 2.3, 7.0, 0, 1.4, 7.0), warlockCasts(stage, chest), 24);
        spell(s, "night_target_close", cam(stage, -3.6, 1.9, 10.8, 0, 1.3, 14.0), warlockCasts(stage, chest), 24);
        s.add(HologramCapture.command("time set 6000"));

        cleanup(s);
        return s;
    }
}
