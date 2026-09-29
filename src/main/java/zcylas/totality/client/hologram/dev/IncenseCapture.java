package zcylas.totality.client.hologram.dev;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;

import java.util.ArrayList;
import java.util.List;

/**
 * Incense / Blessed Incense item scenes for the opt-in development capture run; inert in normal play.
 * Real items through ordinary commands: hotbar, inventory, held (first and third person), dropped and in
 * item frames, by day and by night.
 */
final class IncenseCapture {

    private IncenseCapture() {}

    static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("weather clear"));
        s.add(HologramCapture.command("gamemode creative @s"));
        // A clean stone-floored stage with a wall for the frames.
        s.add(HologramCapture.command("execute at @s run fill ~-4 ~ ~-2 ~4 ~4 ~6 minecraft:air"));
        s.add(HologramCapture.command("execute at @s run fill ~-4 ~-1 ~-2 ~4 ~-1 ~6 minecraft:smooth_stone"));
        s.add(HologramCapture.command("execute at @s run fill ~-3 ~ ~5 ~3 ~3 ~5 minecraft:spruce_planks"));
        s.add(HologramCapture.command("clear @s"));
        s.add(HologramCapture.command("item replace entity @s hotbar.0 with totality:incense"));
        s.add(HologramCapture.command("item replace entity @s hotbar.1 with totality:blessed_incense"));
        s.add(HologramCapture.command("item replace entity @s hotbar.2 with minecraft:stick"));
        s.add(HologramCapture.command("item replace entity @s hotbar.3 with minecraft:candle"));
        s.add(HologramCapture.command("item replace entity @s hotbar.4 with totality:incense 16"));
        s.add(HologramCapture.command("item replace entity @s hotbar.5 with totality:blessed_incense 16"));
        s.add(HologramCapture.look(0, 10));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.screenshot("99_incense_held_first_person"));
        s.add(slot(1));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("99_blessed_incense_held_first_person"));

        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("inventory", () -> Minecraft.getInstance().gui.setScreen(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("99_incense_inventory"));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));

        // Item frames on the wall and dropped items on the floor.
        s.add(HologramCapture.command("execute at @s run summon minecraft:item_frame ~-1 ~1 ~4 {Facing:2b,Fixed:1b,Item:{id:\"totality:incense\",count:1}}"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:item_frame ~1 ~1 ~4 {Facing:2b,Fixed:1b,Item:{id:\"totality:blessed_incense\",count:1}}"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:item ~-0.6 ~ ~2.2 {Item:{id:\"totality:incense\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(HologramCapture.command("execute at @s run summon minecraft:item ~0.6 ~ ~2.2 {Item:{id:\"totality:blessed_incense\",count:1},PickupDelay:32767s,Age:-32768s}"));
        s.add(slot(8));
        s.add(HologramCapture.look(0, 18));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.screenshot("99_incense_frames_and_dropped"));
        s.add(HologramCapture.look(0, 55));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("99_incense_dropped_close"));

        // Third person, holding each.
        s.add(HologramCapture.look(0, 10));
        s.add(HologramCapture.run("third person (front)", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("99_incense_held_third_person"));
        s.add(slot(1));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("99_blessed_incense_held_third_person"));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));

        // Night.
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.look(0, 18));
        s.add(slot(0));
        s.add(HologramCapture.waitTicks(40));
        s.add(HologramCapture.screenshot("99_incense_night"));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("kill @e[type=minecraft:item_frame,distance=..10]"));
        s.add(HologramCapture.command("kill @e[type=minecraft:item,distance=..10]"));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }

    private static Step slot(int index) {
        return HologramCapture.run("select hotbar slot " + index, () -> Minecraft.getInstance().player.getInventory().setSelectedSlot(index));
    }
}
