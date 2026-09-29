package zcylas.totality.client.hologram.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Steel Sword asset footage for the opt-in development capture run (scene 59, prefix {@code SS_}); inert in normal
 * play. The real item through ordinary commands: the hotbar and the inventory tooltip beside the Iron Sword and the
 * vanilla iron and diamond swords, held in first and third person (both hands), dropped, in item frames, and a large
 * item-display turntable (the raw model, no display transform) for the geometry and texture from every side.
 */
final class SteelSwordCapture {

    private SteelSwordCapture() {}

    private static final double EYE = 1.62;

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

    private static Step cam(double[] stage, double ex, double ey, double ez, double lx, double ly, double lz) {
        return onServer(server -> {
            Vec3 eye = new Vec3(stage[0] + ex, stage[1] + ey, stage[2] + ez), at = new Vec3(stage[0] + lx, stage[1] + ly, stage[2] + lz);
            double dx = at.x - eye.x, dy = at.y - eye.y, dz = at.z - eye.z;
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

    /** Turns the turntable display to {@code deg} degrees about the vertical axis. */
    private static Step turn(double[] stage, int deg) {
        float half = (float) Math.toRadians(deg) / 2;
        String q = String.format(Locale.ROOT, "[0f,%.5ff,0f,%.5ff]", Math.sin(half), Math.cos(half));
        return here(stage, "data merge entity @e[type=minecraft:item_display,tag=ss_turn,limit=1] {transformation:{left_rotation:" + q + "}}");
    }

    private static Step shot(String name) {
        return HologramCapture.screenshot("SS_" + name);
    }

    private static Step hud(boolean shown) {
        return HologramCapture.run(shown ? "show HUD" : "hide HUD", () -> {
            var hud = Minecraft.getInstance().gui.hud;
            if (hud.isHidden() == shown) hud.toggle();
            hud.getChat().clearMessages(false);
        });
    }

    private static Step slot(int index) {
        return HologramCapture.run("select hotbar slot " + index, () -> Minecraft.getInstance().player.getInventory().setSelectedSlot(index));
    }

    private static Step cameraType(CameraType type) {
        return HologramCapture.run("camera " + type, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    /** Survival inventory, hovering the hotbar slot {@code col}. */
    private static void inventoryShot(List<Step> s, String name, int col) {
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("inventory", () -> Minecraft.getInstance().gui.setScreen(new InventoryScreen(Minecraft.getInstance().player))));
        s.add(HologramCapture.waitTicks(10));
        s.add(mc -> {
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            double scale = mc.getWindow().getGuiScale();
            try {
                var fx = MouseHandler.class.getDeclaredField("xpos");
                var fy = MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, (left + 8 + col * 18 + 8) * scale);
                fy.setDouble(mc.mouseHandler, (top + 142 + 8) * scale);
            } catch (ReflectiveOperationException e) {
                HologramCapture.log("FAIL: cannot move the mouse: " + e);
            }
            return true;
        });
        s.add(HologramCapture.waitTicks(6));
        s.add(shot(name));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
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
        // A small plains yard: spruce-plank floor, a stone-brick wall (z + 5) for the item frames.
        for (int[] z : new int[][]{{-8, 4}, {5, 14}}) {
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-10 ~ ~%d ~10 ~10 ~%d minecraft:air", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fill ~-10 ~-2 ~%d ~10 ~-1 ~%d minecraft:spruce_planks", z[0], z[1])));
            s.add(here(stage, String.format(Locale.ROOT, "fillbiome ~-10 ~-2 ~%d ~10 ~10 ~%d minecraft:plains", z[0], z[1])));
        }
        s.add(here(stage, "fill ~-6 ~ ~5 ~6 ~4 ~5 minecraft:stone_bricks"));
        s.add(here(stage, "kill @e[type=!minecraft:player,distance=..30]"));
        s.add(HologramCapture.waitTicks(30));

        // ── 1. Hotbar and inventory: the Steel Sword beside the Iron Sword and the vanilla swords ──
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:steel_sword"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:iron_sword"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with minecraft:iron_sword"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:diamond_sword"));
        s.add(HologramCapture.command("item replace entity @s hotbar.4 with totality:steel_sword"));
        s.add(HologramCapture.command("item replace entity @s inventory.0 with totality:steel_sword"));
        s.add(hud(true));
        s.add(cam(stage, 0, EYE, 0, 0, 1.3, 5));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(20));
        s.add(shot("01_first_person_held_hotbar"));
        inventoryShot(s, "02_inventory_tooltip_steel_sword", 0);
        inventoryShot(s, "03_inventory_tooltip_iron_sword_for_comparison", 1);
        s.add(slot(1));
        s.add(HologramCapture.waitTicks(15));
        s.add(shot("04_first_person_iron_sword_for_comparison"));
        s.add(slot(0));
        s.add(HologramCapture.command("item replace entity @s weapon.offhand with totality:steel_sword"));
        s.add(HologramCapture.waitTicks(15));
        s.add(shot("05_first_person_both_hands"));
        s.add(HologramCapture.command("item replace entity @s weapon.offhand with minecraft:air"));

        // ── 2. Third person: back, front, and front with the sword in the off hand too ──
        s.add(hud(false));
        s.add(cameraType(CameraType.THIRD_PERSON_BACK));
        s.add(cam(stage, 0, EYE, 0, 3, 1.4, 5));
        s.add(HologramCapture.waitTicks(15));
        s.add(shot("06_third_person_back"));
        s.add(cameraType(CameraType.THIRD_PERSON_FRONT));
        s.add(cam(stage, 0, EYE, 0, 0, 1.4, -5));
        s.add(HologramCapture.waitTicks(15));
        s.add(shot("07_third_person_front"));
        s.add(cam(stage, 0, EYE, 0, -3, 1.4, -5));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("08_third_person_front_turned"));
        s.add(HologramCapture.command("item replace entity @s weapon.offhand with totality:steel_sword"));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("09_third_person_front_both_hands"));
        s.add(HologramCapture.command("item replace entity @s weapon.offhand with minecraft:air"));
        s.add(cameraType(CameraType.FIRST_PERSON));

        // ── 3. Dropped, in item frames (beside the Iron Sword and vanilla iron sword), and a turntable ──
        s.add(HologramCapture.command("gamemode spectator @s"));
        s.add(here(stage, "summon minecraft:item ~-0.8 ~ ~2.2 {Item:{id:\"totality:steel_sword\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(here(stage, "summon minecraft:item ~0.8 ~ ~2.2 {Item:{id:\"totality:iron_sword\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(HologramCapture.waitTicks(30));
        s.add(cam(stage, 0, 1.4, 0.4, 0, 0.15, 2.2));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("10_dropped_steel_left_iron_right"));
        s.add(cam(stage, -2.2, 0.6, 2.2, -0.8, 0.2, 2.2));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("11_dropped_close_side"));
        s.add(here(stage, "kill @e[type=minecraft:item,distance=..10]"));
        s.add(here(stage, "summon minecraft:item_frame ~-2 ~1.5 ~4 {Facing:2b,Fixed:1b,Invisible:0b,Item:{id:\"totality:steel_sword\",count:1}}"));
        s.add(here(stage, "summon minecraft:item_frame ~ ~1.5 ~4 {Facing:2b,Fixed:1b,Item:{id:\"totality:iron_sword\",count:1}}"));
        s.add(here(stage, "summon minecraft:item_frame ~2 ~1.5 ~4 {Facing:2b,Fixed:1b,Item:{id:\"minecraft:iron_sword\",count:1}}"));
        s.add(here(stage, "summon minecraft:item_frame ~-2 ~3 ~4 {Facing:2b,Fixed:1b,ItemRotation:1b,Item:{id:\"totality:steel_sword\",count:1}}"));
        s.add(HologramCapture.waitTicks(20));
        s.add(cam(stage, 0, 2.3, 0.6, 0, 2.1, 4.5));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("12_item_frames_steel_iron_vanilla"));
        s.add(cam(stage, -2, 1.7, 2.6, -2, 1.55, 4.5));
        s.add(HologramCapture.waitTicks(10));
        s.add(shot("13_item_frame_close"));
        s.add(here(stage, "kill @e[type=minecraft:item_frame,distance=..10]"));

        // Turntable: a 3x item display of the raw model (no display transform). One model unit is 3/16 block, so the
        // hilt (model y ~ -4, 12 units under the model centre) sits 2.25 blocks under the display and the point
        // (y 24) 3 blocks over it. Full-length views every 45 degrees from a slight elevation, then an orbit close
        // around the hilt (guard, grip, pommel), then the blade edge-on (its depth: thin edges, bevel ridges, fuller).
        s.add(here(stage, "summon minecraft:item_display ~ ~3.5 ~2.5 {item:{id:\"totality:steel_sword\",count:1},item_display:\"none\",Tags:[\"ss_turn\"],transformation:{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[3f,3f,3f]}}"));
        s.add(HologramCapture.waitTicks(10));
        for (int deg = 0; deg < 360; deg += 45) {
            s.add(turn(stage, deg));
            s.add(cam(stage, 0, 4.4, -4.6, 0, 3.6, 2.5));
            s.add(HologramCapture.waitTicks(6));
            s.add(shot(String.format(Locale.ROOT, "14_turntable_full_%03d", deg)));
        }
        for (int deg = 0; deg < 360; deg += 30) {
            s.add(turn(stage, deg));
            s.add(cam(stage, 0, 1.9, 0.6, 0, 1.25, 2.5));
            s.add(HologramCapture.waitTicks(6));
            s.add(shot(String.format(Locale.ROOT, "15_turntable_hilt_%03d", deg)));
        }
        s.add(turn(stage, 90));
        s.add(cam(stage, 0, 3.6, 0.9, 0, 3.4, 2.5));
        s.add(HologramCapture.waitTicks(6));
        s.add(shot("16_blade_edge_on_close"));
        s.add(turn(stage, 20));
        s.add(cam(stage, 0.5, 4.2, 1.0, 0, 3.8, 2.5));
        s.add(HologramCapture.waitTicks(6));
        s.add(shot("16_blade_fuller_close"));
        s.add(here(stage, "kill @e[type=minecraft:item_display,tag=ss_turn]"));

        // ── 4. Night: held in first person under the night sky ──
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(hud(true));
        s.add(cam(stage, 0, EYE, 0, 0, 1.3, 5));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(20));
        s.add(shot("17_first_person_night"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("gamemode creative @s"));
        return s;
    }
}
