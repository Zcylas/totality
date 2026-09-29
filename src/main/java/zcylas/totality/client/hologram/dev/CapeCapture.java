package zcylas.totality.client.hologram.dev;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Pair;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.equipment.BackEquipmentAppearance;
import zcylas.totality.api.equipment.EquipmentComponents;
import zcylas.totality.api.equipment.PlayerEquipmentComponent;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.init.items.MagicItems;
import zcylas.totality.menu.equipment.AccessoryInventoryMenu;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.IntFunction;

/**
 * Cape of the Mountebank footage for the opt-in development capture run (scene 52; inert in normal play). The worn
 * cape is shown on "Mountebank", a server-side stand-in player added to the capture world, so the real client sees it
 * exactly as it sees another player: tracked, its Back item synced through the render-only mirror, animated by the
 * client from its movement (walk, sprint, sneak, jump, fall, flight, elytra glide, turning). Then the local player
 * wears it (first person, third person, the Equipment screen). Prefix {@code CP_}; one frame per client tick in the
 * movement sequences.
 */
final class CapeCapture {

    private CapeCapture() {}

    private static final double EYE = 1.62;
    /** The stand-in's default skin follows its UUID: this one draws the slim model (slim/noor), the other the wide one. */
    private static final UUID MOUNTEBANK_ID = UUID.fromString("5f1d0f7a-6c1e-4d8e-9a38-3c9a1d6b2e41");
    private static final UUID MOUNTEBANK_WIDE_ID = UUID.fromString("5f1d0f7a-6c1e-4d8e-9a38-3c9a1d6b2e48");
    /** Server thread only. */
    private static ServerPlayer mountebank;
    private static UUID mountebankId = MOUNTEBANK_ID;

    private static String shot(String name) {
        return "CP_" + name;
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

    private static Vec3 at(double[] stage, double dx, double dy, double dz) {
        return new Vec3(stage[0] + dx, stage[1] + dy, stage[2] + dz);
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        return (float) Math.toDegrees(Math.atan2(-(to.x - from.x), to.z - from.z));
    }

    /** The local player's eyes at {@code eye}, looking at {@code look}. */
    private static Step camera(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> {
            Vec3 eye = at(stage, ex, ey, ez), look = at(stage, lx, ly, lz);
            double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
            float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
            ServerPlayer p = me(server);
            p.teleportTo((ServerLevel) p.level(), eye.x, eye.y - EYE, eye.z, Set.of(), yawTo(eye, look), pitch, true);
        });
    }

    /** Places Mountebank (feet at the stage offset), facing {@code yaw}, head turned by {@code headTurn}. */
    private static void place(double[] stage, double x, double y, double z, float yaw, float headTurn) {
        mountebank.setPos(stage[0] + x, stage[1] + y, stage[2] + z);
        mountebank.setYRot(yaw + headTurn);
        mountebank.setYHeadRot(yaw + headTurn);
        mountebank.setYBodyRot(yaw);
    }

    /**
     * Mountebank does not tick, so vanilla never broadcasts its equipment changes: set the slot and send the
     * update to everyone tracking it (what the tick would have sent).
     */
    private static void wear(EquipmentSlot slot, ItemStack stack) {
        mountebank.setItemSlot(slot, stack);
        ((ServerLevel) mountebank.level()).getChunkSource().sendToTrackingPlayers(mountebank,
                new ClientboundSetEquipmentPacket(mountebank.getId(), List.of(Pair.of(slot, stack.copy()))));
    }

    private static Step spawnMountebank(double[] stage) {
        return spawnMountebank(stage, MOUNTEBANK_ID);
    }

    private static Step spawnMountebank(double[] stage, UUID id) {
        return onServer(server -> {
            ServerLevel level = server.overworld();
            mountebankId = id;
            mountebank = TotalityFakePlayer.create(level, new GameProfile(id, "Mountebank"));
            mountebank.setGameMode(GameType.SURVIVAL);                   // (a new player takes the world's default mode)
            place(stage, 0, 0, 6, 180, 0);
            server.getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(mountebank)));
            level.addNewPlayer(mountebank);
            EquipmentComponents.get(mountebank).setItem(PlayerEquipmentComponent.IDX_BACK, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK));
        });
    }

    private static Step removeMountebank() {
        return onServer(server -> {
            if (mountebank == null) return;
            EquipmentComponents.get(mountebank).clearContent();
            ((ServerLevel) mountebank.level()).removePlayerImmediately(mountebank, Entity.RemovalReason.DISCARDED);
            server.getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(mountebankId)));
            mountebank = null;
        });
    }

    private static RemotePlayer clientMountebank() {
        Minecraft mc = Minecraft.getInstance();
        for (var p : mc.level.players()) if (p instanceof RemotePlayer r && p.getUUID().equals(mountebankId)) return r;
        return null;
    }

    /** A pose for tick i: {x, y, z, body yaw, head turn}. */
    private static Step pose(double[] stage, IntFunction<double[]> path, int i) {
        return onServer(server -> {
            double[] p = path.apply(i);
            place(stage, p[0], p[1], p[2], (float) p[3], (float) p[4]);
        });
    }

    /** Moves Mountebank along {@code path} and takes a frame every client tick. */
    private static void move(List<Step> s, double[] stage, String name, int frames, IntFunction<double[]> path) {
        for (int i = 0; i < frames; i++) {
            Step move = pose(stage, path, i);
            Step frame = HologramCapture.screenshot(shot(String.format(Locale.ROOT, "%s_%02d", name, i)));
            s.add(mc -> move.tick(mc) & frame.tick(mc));
        }
    }

    private static Step flags(boolean crouch, boolean sprint, boolean fallFlying) {
        return onServer(server -> {
            mountebank.setShiftKeyDown(crouch);
            mountebank.setSprinting(sprint);
            if (fallFlying) mountebank.startFallFlying();
            else mountebank.stopFallFlying();
            mountebank.setPose(fallFlying ? Pose.FALL_FLYING : crouch ? Pose.CROUCHING : Pose.STANDING);
        });
    }

    private static void frames(List<Step> s, String name, int count, int every) {
        for (int i = 0; i < count; i++) {
            s.add(HologramCapture.screenshot(shot(String.format(Locale.ROOT, "%s_%02d", name, i))));
            if (every > 1) s.add(HologramCapture.waitTicks(every - 1));
        }
    }

    /** The stage: a flat plains meadow around the local player (a spectator with the HUD hidden). */
    private static void setUpStage(List<Step> s, double[] stage) {
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
        // A flat plains meadow (fills split under the 32768-block limit) with a stone-brick wall far behind.
        for (int[] z : new int[][]{{-20, -3}, {-2, 15}, {16, 33}}) {
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-24 ~ ~%d ~24 ~20 ~%d minecraft:air", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-24 ~21 ~%d ~24 ~34 ~%d minecraft:air", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fill ~-24 ~-1 ~%d ~24 ~-1 ~%d minecraft:grass_block", z[0], z[1])));
            s.add(HologramCapture.command(String.format(Locale.ROOT, "execute at @s run fillbiome ~-24 ~-3 ~%d ~24 ~20 ~%d minecraft:plains", z[0], z[1])));
        }
        s.add(HologramCapture.command("execute at @s run fill ~-8 ~ ~16 ~8 ~6 ~16 minecraft:stone_bricks"));
        s.add(HologramCapture.command("kill @e[type=!minecraft:player,distance=..60]"));
        s.add(HologramCapture.waitTicks(40));
    }

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        setUpStage(s, stage);

        // ── Mountebank: a tracked "other player" wearing the cape ──────────
        s.add(spawnMountebank(stage));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.check("client: Mountebank is tracked and its synced Back item is the cape",
                () -> clientMountebank() != null && BackEquipmentAppearance.get(clientMountebank()).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));

        // ── 1. Turntable at the reference sheet's angles (standing, 3.4 blocks) ─
        String[] names = {"front", "front_right", "right", "back_right", "back", "back_left", "left", "front_left"};
        for (int k = 0; k < 8; k++) {
            double a = Math.toRadians(k * 45);
            // Mountebank faces -z (yaw 180), so its front is seen from -z.
            s.add(camera(stage, -Math.sin(a) * 3.4, 1.35, 6 - Math.cos(a) * 3.4, 0, 1.0, 6));
            s.add(HologramCapture.waitTicks(4));
            s.add(HologramCapture.screenshot(shot("turn_" + names[k])));
        }
        // ── 1b. Idle: standing still, the cloak's slow drape (every 4th tick over 2 s) ─
        s.add(camera(stage, -2.4, 1.2, 3.2, 0, 1.0, 6));
        s.add(HologramCapture.waitTicks(20));
        frames(s, "idle", 10, 4);
        // ── 1c. Start and stop: stand, walk 1.5 s, stop; the hem lags and settles (every tick) ─
        s.add(camera(stage, 0, 1.3, 0.5, 0, 1.0, 6));
        s.add(onServer(server -> place(stage, -3.5, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(20));
        move(s, stage, "start_stop", 70, i -> {
            double walked = i < 6 ? 0 : Math.min(30, i - 6) * 0.216;
            return new double[]{-3.5 + walked, 0, 6, -90, 0};
        });
        // ── 1d. Turning in place: a quick quarter turn, then still (every tick) ─
        s.add(camera(stage, -2.4, 1.3, 3.0, 0, 1.0, 6));
        s.add(onServer(server -> place(stage, 0, 0, 6, 180, 0)));
        s.add(HologramCapture.waitTicks(20));
        move(s, stage, "turn_in_place", 40, i -> new double[]{0, 0, 6, 180 - Math.min(90, Math.max(0, i - 4) * 15), 0});
        s.add(onServer(server -> place(stage, 0, 0, 6, 180, 0)));
        s.add(HologramCapture.waitTicks(10));

        // ── 2. Details: clasp and chain, back emblem, hem ─────────────────
        s.add(camera(stage, 0, 1.45, 4.7, 0, 1.3, 6));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot(shot("detail_clasp")));
        s.add(camera(stage, 0, 1.1, 8.2, 0, 1.0, 6));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot(shot("detail_back")));
        s.add(camera(stage, 0.6, 0.5, 7.6, 0, 0.35, 6));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot(shot("detail_hem")));

        // ── 3. Walking and sprinting past a side camera, then away from a rear camera ─
        s.add(camera(stage, 0, 1.3, 0.5, 0, 1.0, 6));
        s.add(onServer(server -> place(stage, -5, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "walk_side", 50, i -> new double[]{-5 + i * 0.216, 0, 6, -90, 0});
        s.add(flags(false, true, false));
        s.add(onServer(server -> place(stage, -6, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "sprint_side", 40, i -> new double[]{-6 + i * 0.28, 0, 6, -90, 0});
        s.add(flags(false, false, false));
        s.add(camera(stage, 0, 1.8, 1.0, 0, 1.0, 6));
        s.add(onServer(server -> place(stage, 0, 0, 3.5, 0, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "walk_back", 40, i -> new double[]{0, 0, 3.5 + i * 0.216, 0, 0});

        // ── 4. Sneaking (in place, then crouch-walking past the side camera) ─
        s.add(camera(stage, 0, 1.2, 2.0, 0, 0.8, 6));
        s.add(onServer(server -> place(stage, 0, 0, 6, -90, 0)));
        s.add(flags(true, false, false));
        s.add(HologramCapture.waitTicks(10));
        frames(s, "sneak_still", 2, 5);
        move(s, stage, "sneak_walk", 30, i -> new double[]{-1.5 + i * 0.1, 0, 6, -90, 0});
        s.add(flags(false, false, false));

        // ── 5. Jumping while walking, falling from 9 blocks ──────────────
        s.add(camera(stage, 0, 1.8, 0.5, 0, 1.3, 6));
        s.add(onServer(server -> place(stage, -4, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "jump", 36, i -> {
            int t = i % 12;
            double y = t < 12 ? Math.max(0, 0.42 * t - 0.04 * t * t) : 0;
            return new double[]{-4 + i * 0.216, y, 6, -90, 0};
        });
        s.add(camera(stage, 6.5, 4.0, 6, 0, 3.5, 6));
        s.add(onServer(server -> place(stage, 0, 9, 6, 180, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "fall", 22, i -> {
            double fallen = 0;
            double v = 0;
            for (int t = 0; t < i; t++) {
                v = (v + 0.08) * 0.98;
                fallen += v;
            }
            return new double[]{0, Math.max(0, 9 - fallen), 6, 180, 0};
        });

        // ── 6. Flight: fast through the air (creative-style), then an elytra glide ─
        s.add(camera(stage, 0, 4.2, -0.5, 0, 3.8, 6));
        s.add(onServer(server -> place(stage, -9, 3.5, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "fly", 36, i -> new double[]{-9 + i * 0.5, 3.5 + Math.sin(i * 0.2) * 0.2, 6, -90, 0});
        s.add(onServer(server -> wear(EquipmentSlot.CHEST, new ItemStack(Items.ELYTRA))));
        s.add(onServer(server -> place(stage, -9, 4.5, 6, -90, 0)));
        s.add(flags(false, false, true));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("client: the watcher sees Mountebank's elytra and glide",
                () -> clientMountebank().getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA) && clientMountebank().isFallFlying()));
        move(s, stage, "elytra", 30, i -> new double[]{-9 + i * 0.6, 4.5 - i * 0.05, 6, -90, 0});
        s.add(flags(false, false, false));
        s.add(onServer(server -> wear(EquipmentSlot.CHEST, ItemStack.EMPTY)));

        // ── 7. Turning: a walked circle, the cape swinging out ────────────
        s.add(camera(stage, 0, 5.5, 1.5, 0, 0.5, 6));
        s.add(HologramCapture.waitTicks(4));
        move(s, stage, "circle", 48, i -> {
            double a = i * 2 * Math.PI / 48;
            return new double[]{Math.cos(a) * 2.2, 0, 6 + Math.sin(a) * 2.2, Math.toDegrees(a), 0};
        });

        // ── 8. Clipping: full iron armor (turntable and a walk), head turned and pitched ─
        s.add(onServer(server -> {
            wear(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            wear(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            wear(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            wear(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
            place(stage, 0, 0, 6, 180, 0);
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("client: the watcher sees Mountebank's iron armor with the cape",
                () -> clientMountebank().getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                        && clientMountebank().getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                        && BackEquipmentAppearance.get(clientMountebank()).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        for (int k = 0; k < 8; k += 2) {
            double a = Math.toRadians(k * 45);
            s.add(camera(stage, -Math.sin(a) * 3.4, 1.35, 6 - Math.cos(a) * 3.4, 0, 1.0, 6));
            s.add(HologramCapture.waitTicks(4));
            s.add(HologramCapture.screenshot(shot("armor_" + names[k])));
        }
        s.add(camera(stage, 0, 1.3, 0.5, 0, 1.0, 6));
        s.add(onServer(server -> place(stage, -4, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(10));
        move(s, stage, "armor_walk", 36, i -> new double[]{-4 + i * 0.216, 0, 6, -90, 0});
        s.add(onServer(server -> {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                wear(slot, ItemStack.EMPTY);
            }
            place(stage, 0, 0, 6, 180, 0);
        }));
        s.add(camera(stage, -2.4, 1.6, 3.6, 0, 1.4, 6));
        for (int turn : new int[]{-70, -35, 35, 70}) {
            s.add(onServer(server -> place(stage, 0, 0, 6, 180, turn)));
            s.add(HologramCapture.waitTicks(6));
            s.add(HologramCapture.screenshot(shot("head_turn_" + (turn < 0 ? "m" : "p") + Math.abs(turn))));
        }
        s.add(onServer(server -> {
            place(stage, 0, 0, 6, 180, 0);
            mountebank.setXRot(60);
        }));
        s.add(camera(stage, 3.0, 1.6, 7.5, 0, 1.4, 6));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(shot("head_down")));
        s.add(onServer(server -> mountebank.setXRot(-60)));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(shot("head_up")));
        s.add(onServer(server -> mountebank.setXRot(0)));

        // ── 9. Tracking sync: unequip, re-equip, and an ordinary death drop ─
        s.add(camera(stage, 0, 1.4, 9.4, 0, 1.0, 6));
        s.add(onServer(server -> EquipmentComponents.get(mountebank).setItem(PlayerEquipmentComponent.IDX_BACK, ItemStack.EMPTY)));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("client: unequipped on the server, the watcher's mirror for Mountebank is empty",
                () -> BackEquipmentAppearance.get(clientMountebank()).isEmpty()));
        s.add(HologramCapture.screenshot(shot("sync_unequipped")));
        s.add(onServer(server -> EquipmentComponents.get(mountebank).setItem(PlayerEquipmentComponent.IDX_BACK,
                new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK))));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("client: re-equipped, the watcher sees the cape again",
                () -> BackEquipmentAppearance.get(clientMountebank()).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        s.add(onServer(server -> EquipmentComponents.get(mountebank).dropOnDeath()));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("client: after Mountebank's death drop the watcher's mirror is empty (no stale cape)",
                () -> BackEquipmentAppearance.get(clientMountebank()).isEmpty()));
        s.add(HologramCapture.check("client: the dropped cape is an item entity in the world",
                () -> Minecraft.getInstance().level.getEntitiesOfClass(ItemEntity.class, clientMountebank().getBoundingBox().inflate(12))
                        .stream().anyMatch(e -> e.getItem().is(MagicItems.CAPE_OF_THE_MOUNTEBANK))));
        s.add(HologramCapture.screenshot(shot("sync_death_drop")));
        s.add(removeMountebank());
        s.add(HologramCapture.command("kill @e[type=minecraft:item,distance=..60]"));
        s.add(HologramCapture.waitTicks(10));

        // ── 10. The local player wears it: first person, third person, Equipment screen ─
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            EquipmentComponents.get(p).setItem(PlayerEquipmentComponent.IDX_BACK, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK));
            EquipmentComponents.get(p).sync();
            p.getInventory().setItem(1, new ItemStack(MagicItems.CAPE_OF_THE_MOUNTEBANK));   // its icon in the hotbar, not held
            p.teleportTo((ServerLevel) p.level(), stage[0], stage[1], stage[2] + 6, Set.of(), 180, 0, true);
        }));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.run("show HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden()) hud.toggle();
        }));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(shot("fp_forward")));
        s.add(HologramCapture.look(180, 80));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(shot("fp_down")));
        s.add(HologramCapture.look(180, 5));
        s.add(HologramCapture.run("third person (back)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK)));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(shot("tp_back")));
        s.add(HologramCapture.run("third person (front)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot(shot("tp_front")));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.until("Equipment screen open", () ->
                        Minecraft.getInstance().player.containerMenu instanceof AccessoryInventoryMenu,
                () -> {
                    if (!(Minecraft.getInstance().player.containerMenu instanceof AccessoryInventoryMenu)) BackSlotCapture.openOnServer();
                }, 60));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot(shot("equipment_screen")));
        // The inventory icon at smaller GUI scales (the default capture runs at GUI scale 4).
        for (int scale : new int[]{3, 2}) {
            s.add(HologramCapture.run("GUI scale " + scale, () -> {
                Minecraft.getInstance().options.guiScale().set(scale);
                Minecraft.getInstance().resizeGui();
            }));
            s.add(HologramCapture.waitTicks(6));
            s.add(HologramCapture.screenshot(shot("equipment_screen_gui" + scale)));
        }
        s.add(HologramCapture.run("GUI scale 4", () -> {
            Minecraft.getInstance().options.guiScale().set(4);
            Minecraft.getInstance().resizeGui();
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.run("close screen", () -> Minecraft.getInstance().player.closeContainer()));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
        s.add(onServer(server -> {
            ServerPlayer p = me(server);
            EquipmentComponents.get(p).setItem(PlayerEquipmentComponent.IDX_BACK, ItemStack.EMPTY);
            EquipmentComponents.get(p).sync();
        }));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }

    // ── Scene 53: the geometry correction (joins, stability, arm clearance) ──────────────────────────────────────

    /** Where Mountebank is on tick i of a sequence: {x, y, z (stage offsets), body yaw, head turn, head pitch}. */
    private interface Path {
        double[] at(int i);
    }

    /**
     * Moves Mountebank along {@code path} with the camera following it, and takes a frame every client tick. The camera
     * sits at {@code cam} = {forward, right, up} blocks in Mountebank's own frame from where the client draws it (a
     * watched player is drawn interpolated behind its server position) and looks at its chest.
     */
    private static void follow(List<Step> s, double[] stage, String name, int frames, Path path, double[] cam) {
        for (int i = 0; i < frames; i++) {
            double[] p = path.at(i);
            Step move = onServer(server -> {
                place(stage, p[0], p[1], p[2], (float) p[3], (float) p[4]);
                mountebank.setXRot(p.length > 5 ? (float) p[5] : 0.0F);
            });
            Step camera = mc -> {
                RemotePlayer drawn = clientMountebank();
                if (drawn == null) return true;
                double yaw = Math.toRadians(p[3]);
                Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
                Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
                Vec3 look = drawn.position().add(0, 1.0, 0);
                Vec3 eye = drawn.position().add(forward.scale(cam[0])).add(right.scale(cam[1])).add(0, cam[2], 0);
                double dx = look.x - eye.x, dy = look.y - eye.y, dz = look.z - eye.z;
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
                mc.player.snapTo(eye.x, eye.y - EYE, eye.z, yawTo(eye, look), pitch);
                mc.player.setDeltaMovement(Vec3.ZERO);
                return true;
            };
            Step frame = HologramCapture.screenshot("CG_" + String.format(Locale.ROOT, "%s_%02d", name, i));
            s.add(mc -> move.tick(mc) & camera.tick(mc) & frame.tick(mc));
        }
    }

    /** Heading +x: idle, walking, sprinting and stopping, one continuous run (frames every tick once moving). */
    private static void idleWalkSprintStop(List<Step> s, double[] stage, String name, double[] cam) {
        double x0 = -12;
        double walked = 24 * 0.216;
        double sprinted = walked + 24 * 0.28;
        s.add(flags(false, false, false));
        s.add(onServer(server -> place(stage, x0, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(20));
        for (int k = 0; k < 8; k++) {
            follow(s, stage, name + "_idle_" + k, 1, i -> new double[]{x0, 0, 6, -90, 0}, cam);
            s.add(HologramCapture.waitTicks(2));
        }
        follow(s, stage, name + "_walk", 24, i -> new double[]{x0 + (i + 1) * 0.216, 0, 6, -90, 0}, cam);
        s.add(flags(false, true, false));
        follow(s, stage, name + "_sprint", 24, i -> new double[]{x0 + walked + (i + 1) * 0.28, 0, 6, -90, 0}, cam);
        s.add(flags(false, false, false));
        follow(s, stage, name + "_stop", 20, i -> new double[]{x0 + sprinted, 0, 6, -90, 0}, cam);
    }

    /**
     * Cape geometry correction footage for the opt-in capture run (scene 53; prefix {@code CG_}). Close-ups with a
     * camera that follows Mountebank: the joins from the back and both sides while it stands, walks, sprints and stops;
     * the arm cycles of the wide and the slim model; chest armor (walking, sprinting, crouch-walking); turning while
     * sprinting; head movement; then the same movement at a normal viewing distance. One frame per client tick.
     */
    static List<Step> geometryScenes() {
        List<Step> s = new ArrayList<>();
        double[] stage = new double[3];
        setUpStage(s, stage);
        double[] back = {-2.0, 0, 1.3};
        double[] left = {0, -1.7, 1.2};
        double[] right = {0, 1.7, 1.2};

        // ── Wide model: joins from the back and both sides ──────────────────
        s.add(spawnMountebank(stage, MOUNTEBANK_WIDE_ID));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.check("client: the wide Mountebank is tracked and wears the cape",
                () -> clientMountebank() != null && BackEquipmentAppearance.get(clientMountebank()).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        idleWalkSprintStop(s, stage, "wide_back", back);
        idleWalkSprintStop(s, stage, "wide_left", left);
        idleWalkSprintStop(s, stage, "wide_right", right);

        // ── Turning while sprinting (a circle of radius 3), from behind on the outside of the turn ─
        s.add(flags(false, true, false));
        s.add(onServer(server -> place(stage, 3, 0, 6, 0, 0)));
        s.add(HologramCapture.waitTicks(10));
        follow(s, stage, "wide_turn_sprint", 48, i -> {
            double a = i * 0.28 / 3.0;
            return new double[]{3 * Math.cos(a), 0, 6 + 3 * Math.sin(a), Math.toDegrees(a), 0};
        }, new double[]{-1.4, -1.0, 1.3});
        s.add(flags(false, false, false));

        // ── Head movement while walking (collar and hood) ────────────────────
        s.add(onServer(server -> place(stage, -8, 0, 6, -90, 0)));
        s.add(HologramCapture.waitTicks(10));
        follow(s, stage, "wide_head_walk", 36, i -> new double[]{-8 + i * 0.216, 0, 6, -90,
                65 * Math.sin(i * 0.3), 40 * Math.sin(i * 0.19)}, new double[]{-1.2, 0.9, 1.5});

        // ── Chest armor (full iron): walk, sprint, crouch-walk ───────────────
        s.add(onServer(server -> {
            wear(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
            wear(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            wear(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
            wear(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
        }));
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.check("client: the watcher sees Mountebank's iron armor with the cape",
                () -> clientMountebank().getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                        && BackEquipmentAppearance.get(clientMountebank()).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        for (String view : new String[]{"left", "back"}) {
            double[] cam = view.equals("left") ? left : back;
            s.add(onServer(server -> place(stage, -12, 0, 6, -90, 0)));
            s.add(HologramCapture.waitTicks(10));
            follow(s, stage, "armor_" + view + "_walk", 24, i -> new double[]{-12 + (i + 1) * 0.216, 0, 6, -90, 0}, cam);
            s.add(flags(false, true, false));
            follow(s, stage, "armor_" + view + "_sprint", 24, i -> new double[]{-12 + 24 * 0.216 + (i + 1) * 0.28, 0, 6, -90, 0}, cam);
            s.add(flags(false, false, false));
        }
        s.add(onServer(server -> place(stage, -3, 0, 6, -90, 0)));
        s.add(flags(true, false, false));
        s.add(HologramCapture.waitTicks(10));
        follow(s, stage, "armor_left_crouch", 24, i -> new double[]{-3 + (i + 1) * 0.065, 0, 6, -90, 0}, left);
        s.add(flags(false, false, false));
        s.add(onServer(server -> {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                wear(slot, ItemStack.EMPTY);
            }
        }));

        // ── Crouch-walking without armor ──────────────────────────────────────
        s.add(onServer(server -> place(stage, -3, 0, 6, -90, 0)));
        s.add(flags(true, false, false));
        s.add(HologramCapture.waitTicks(10));
        follow(s, stage, "wide_left_crouch", 24, i -> new double[]{-3 + (i + 1) * 0.065, 0, 6, -90, 0}, left);
        s.add(flags(false, false, false));

        // ── Normal viewing distance (3.4 blocks): side and back, walk and sprint ─
        for (String view : new String[]{"left", "back"}) {
            double[] cam = view.equals("left") ? new double[]{0, -3.4, 1.35} : new double[]{-3.4, 0, 1.45};
            s.add(onServer(server -> place(stage, -12, 0, 6, -90, 0)));
            s.add(HologramCapture.waitTicks(10));
            follow(s, stage, "normal_" + view + "_walk", 24, i -> new double[]{-12 + (i + 1) * 0.216, 0, 6, -90, 0}, cam);
            s.add(flags(false, true, false));
            follow(s, stage, "normal_" + view + "_sprint", 24, i -> new double[]{-12 + 24 * 0.216 + (i + 1) * 0.28, 0, 6, -90, 0}, cam);
            s.add(flags(false, false, false));
        }

        // ── Slim model: the arm cycles from both sides and behind ───────────
        s.add(removeMountebank());
        s.add(HologramCapture.waitTicks(10));
        s.add(spawnMountebank(stage, MOUNTEBANK_ID));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.check("client: the slim Mountebank is tracked and wears the cape",
                () -> clientMountebank() != null && BackEquipmentAppearance.get(clientMountebank()).is(MagicItems.CAPE_OF_THE_MOUNTEBANK)));
        for (String view : new String[]{"left", "right", "back"}) {
            double[] cam = view.equals("left") ? left : view.equals("right") ? right : back;
            s.add(onServer(server -> place(stage, -12, 0, 6, -90, 0)));
            s.add(HologramCapture.waitTicks(10));
            follow(s, stage, "slim_" + view + "_walk", 24, i -> new double[]{-12 + (i + 1) * 0.216, 0, 6, -90, 0}, cam);
            s.add(flags(false, true, false));
            follow(s, stage, "slim_" + view + "_sprint", 24, i -> new double[]{-12 + 24 * 0.216 + (i + 1) * 0.28, 0, 6, -90, 0}, cam);
            s.add(flags(false, false, false));
        }
        s.add(removeMountebank());
        s.add(HologramCapture.waitTicks(10));
        return s;
    }
}
