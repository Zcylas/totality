package zcylas.totality.client.hologram.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Raw Meat item scenes for the opt-in development capture run; inert in normal play. Real item through
 * ordinary commands: hotbar (next to vanilla meats and Totality foods), a full stack, the survival
 * inventory with its tooltip, held (first and third person), dropped and in item frames, day and night.
 */
final class RawMeatCapture {

    private RawMeatCapture() {}

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode survival @s"));
        s.add(HologramCapture.command("execute at @s run fill ~-4 ~ ~-2 ~4 ~4 ~6 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-4 ~-1 ~-2 ~4 ~-1 ~6 minecraft:smooth_stone"));
        s.add(HologramCapture.command("execute at @s run fill ~-3 ~ ~5 ~3 ~3 ~5 minecraft:spruce_planks"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:raw_meat"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:raw_meat 64"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with minecraft:beef"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:porkchop"));
        s.add(HologramCapture.command("item replace entity @s hotbar.4 with totality:pizza_margherita_slice"));
        s.add(HologramCapture.command("item replace entity @s hotbar.5 with totality:garlic"));
        s.add(HologramCapture.command("item replace entity @s hotbar.6 with totality:raw_meat 12"));
        s.add(HologramCapture.command("item replace entity @s inventory.0 with totality:raw_meat 64"));
        s.add(HologramCapture.command("item replace entity @s inventory.1 with totality:raw_meat 37"));
        s.add(HologramCapture.command("item replace entity @s inventory.9 with minecraft:cooked_beef 8"));
        s.add(HologramCapture.look(0, 10));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.screenshot("A0_raw_meat_held_first_person"));

        // Survival inventory, hovering the stack (Totality tooltip).
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("inventory", () -> Minecraft.getInstance().gui.setScreen(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))));
        s.add(HologramCapture.waitTicks(10));
        s.add(mouseToInventorySlot(0, 1));                   // first main-inventory slot: 64 Raw Meat
        s.add(HologramCapture.waitTicks(6));
        s.add(HologramCapture.screenshot("A1_raw_meat_inventory_tooltip"));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));

        // Frames and dropped items.
        s.add(HologramCapture.command("execute at @s run summon minecraft:item_frame ~-1 ~1 ~4 {Facing:2b,Fixed:1b,Item:{id:\"totality:raw_meat\",count:1}}"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:item_frame ~1 ~1 ~4 {Facing:2b,Fixed:1b,Item:{id:\"minecraft:beef\",count:1}}"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:item ~-0.6 ~ ~2.2 {Item:{id:\"totality:raw_meat\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:item ~0.6 ~ ~2.2 {Item:{id:\"totality:raw_meat\",count:24},PickupDelay:32767s,Age:-32768s}"));
        s.add(slot(8));
        s.add(HologramCapture.look(0, 18));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.screenshot("A2_raw_meat_frames_and_dropped"));
        s.add(HologramCapture.look(0, 55));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("A3_raw_meat_dropped_close"));

        s.add(HologramCapture.look(0, 10));
        s.add(HologramCapture.run("third person (front)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("A4_raw_meat_held_third_person"));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));

        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.look(0, 18));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.screenshot("A5_raw_meat_night"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("kill @e[type=minecraft:item_frame,distance=..10]"));
        s.add(HologramCapture.command("kill @e[type=minecraft:item,distance=..10]"));
        s.add(HologramCapture.command("gamemode creative @s"));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }

    private static Step slot(int index) {
        return HologramCapture.run("select hotbar slot " + index, () -> Minecraft.getInstance().player.getInventory().setSelectedSlot(index));
    }

    /** Vanilla survival inventory layout (176x166): main inventory row {@code row} (1-3), column {@code col}. */
    private static Step mouseToInventorySlot(int col, int row) {
        return HologramCapture.run("mouse to inventory slot", () -> {
            Minecraft mc = Minecraft.getInstance();
            int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
            int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
            double gx = left + 8 + col * 18 + 8, gy = top + 84 + (row - 1) * 18 + 8;
            double scale = mc.getWindow().getGuiScale();
            try {
                var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, gx * scale);
                fy.setDouble(mc.mouseHandler, gy * scale);
            } catch (ReflectiveOperationException e) {
                HologramCapture.log("FAIL: cannot move the mouse: " + e);
            }
        });
    }
}
