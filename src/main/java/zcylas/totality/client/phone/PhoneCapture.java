package zcylas.totality.client.phone;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.screen.phone.BankScreen;
import zcylas.totality.screen.phone.PhoneFrame;
import zcylas.totality.screen.phone.PhoneScreens;
import zcylas.totality.screen.phone.PhoneSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Phone screen scenes for the opt-in development capture run ({@link HologramCapture}); inert in
 * normal play. Real item, real opening path (the held-item entry point {@code PhoneItem.use} calls),
 * real key handling of the screens.
 */
public final class PhoneCapture {

    private PhoneCapture() {}

    public static void registerIfRequested() {
        if (!HologramCapture.requested()) return;
        HologramCapture.addScene(45, scenes());
    }

    static Step key(String label, int key) {
        return HologramCapture.run("key " + label, () -> {
            Screen screen = Minecraft.getInstance().gui.screen();
            if (screen != null) screen.keyPressed(new KeyEvent(key, 0, 0));
        });
    }

    static Step openHeldPhone() {
        return HologramCapture.run("open the held phone (PhoneItem.use path)", () -> {
            ItemStack stack = Minecraft.getInstance().player.getMainHandItem();
            PhoneScreens.openFromHand(stack, PhoneSource.hand(InteractionHand.MAIN_HAND), PhoneFrame.forStack(stack));
        });
    }

    /** Moves the (virtual) mouse to GUI coordinates, the way a real cursor would. Dev only: reflection. */
    static Step mouse(String label, java.util.function.Supplier<double[]> guiPoint) {
        return HologramCapture.run("mouse to " + label, () -> {
            Minecraft mc = Minecraft.getInstance();
            double[] p = guiPoint.get();
            double scale = mc.getWindow().getGuiScale();
            try {
                var fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
                var fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
                fx.setAccessible(true);
                fy.setAccessible(true);
                fx.setDouble(mc.mouseHandler, p[0] * scale);
                fy.setDouble(mc.mouseHandler, p[1] * scale);
            } catch (ReflectiveOperationException e) {
                HologramCapture.log("FAIL: cannot move the mouse: " + e);
            }
        });
    }

    private static List<Step> scenes() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.command("item replace entity @s weapon.mainhand with totality:basic_copper_phone"));
        s.add(HologramCapture.look(-35, 8));
        s.add(mouse("a corner", () -> new double[] {2, 2}));
        s.add(HologramCapture.waitTicks(30));
        s.add(HologramCapture.screenshot("90_phone_held"));
        s.add(HologramCapture.run("third person", () -> Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_FRONT)));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.screenshot("90b_phone_held_third_person"));
        s.add(HologramCapture.run("first person", () -> Minecraft.getInstance().options.setCameraType(CameraType.FIRST_PERSON)));
        s.add(HologramCapture.run("allow screens", () -> HologramCapture.allowScreens = true));
        s.add(HologramCapture.run("inventory", () -> Minecraft.getInstance().gui.setScreen(
                new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.screenshot("91_phone_inventory"));
        s.add(HologramCapture.run("close", () -> Minecraft.getInstance().gui.setScreen(null)));
        s.add(HologramCapture.waitTicks(10));

        // First open: the setup screen, with the opening transition.
        s.add(openHeldPhone());
        for (int t : new int[] {1, 2, 3, 5}) {
            s.add(HologramCapture.screenshot("92_phone_open_t" + t));
        }
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("92_phone_setup"));
        s.add(key("Enter (Begin)", GLFW.GLFW_KEY_ENTER));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.check("setup completed: the app grid is open",
                () -> Minecraft.getInstance().gui.screen() instanceof zcylas.totality.screen.phone.PhoneAppGridScreen));
        s.add(HologramCapture.screenshot("93_phone_grid"));
        s.addAll(PhoneCaptureStates.states());
        s.add(key("Tab (close)", GLFW.GLFW_KEY_TAB));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("Tab closes the phone", () -> Minecraft.getInstance().gui.screen() == null));

        // Bank app screen (phone-framed).
        s.add(HologramCapture.run("bank screen", () -> Minecraft.getInstance().gui.setScreen(new BankScreen(PhoneFrame.COPPER))));
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("97_phone_bank"));
        s.add(key("Esc (back)", GLFW.GLFW_KEY_ESCAPE));
        s.add(HologramCapture.waitTicks(15));
        s.add(HologramCapture.check("Esc from Bank returns to the app grid",
                () -> Minecraft.getInstance().gui.screen() instanceof zcylas.totality.screen.phone.PhoneAppGridScreen));
        s.add(key("Tab (close)", GLFW.GLFW_KEY_TAB));

        // Dark environment.
        s.add(HologramCapture.command("time set 18000"));
        s.add(HologramCapture.waitTicks(20));
        s.add(openHeldPhone());
        s.add(HologramCapture.waitTicks(20));
        s.add(HologramCapture.screenshot("98_phone_grid_night"));
        s.add(key("Tab (close)", GLFW.GLFW_KEY_TAB));
        s.add(HologramCapture.command("time set 6000"));
        s.add(HologramCapture.run("screens back to normal", () -> HologramCapture.allowScreens = false));
        s.add(HologramCapture.waitTicks(10));
        return s;
    }
}
