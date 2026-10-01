package zcylas.totality.client.phone;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import org.lwjgl.glfw.GLFW;
import zcylas.totality.client.hologram.dev.HologramCapture;
import zcylas.totality.client.hologram.dev.HologramCapture.Step;
import zcylas.totality.screen.phone.PhoneAppGridScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Interaction states of the home screen for the capture run: hover, lock tooltip, dock, keyboard focus, press. */
final class PhoneCaptureStates {

    private static double[] lastDragEnd = {0, 0};

    private PhoneCaptureStates() {}

    static PhoneAppGridScreen grid() {
        return Minecraft.getInstance().gui.screen() instanceof PhoneAppGridScreen s ? s : null;
    }

    static double[] tile(int i) {
        PhoneAppGridScreen s = grid();
        return s == null ? new double[] {0, 0} : s.tileCentre(i);
    }

    static double[] dock(int i) {
        PhoneAppGridScreen s = grid();
        return s == null ? new double[] {0, 0} : s.dockCentre(i);
    }

    private static MouseButtonEvent left(double[] p) {
        return new MouseButtonEvent(p[0], p[1], new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
    }

    /** A real click on the screen: press and release at the same point (actions happen on release). */
    static Step clickAt(String label, Supplier<double[]> at) {
        return HologramCapture.run("click " + label, () -> {
            PhoneAppGridScreen s = grid();
            if (s == null) return;
            double[] p = at.get();
            s.mouseClicked(left(p), false);
            s.mouseReleased(left(p));
        });
    }

    /** Presses at {@code from}, then drags to {@code from + (dx, dy)} in {@code steps} events (no release). */
    static Step dragHold(String label, Supplier<double[]> from, double dx, double dy, int steps) {
        return HologramCapture.run("drag " + label, () -> {
            PhoneAppGridScreen s = grid();
            if (s == null) return;
            double[] p = from.get();
            s.mouseClicked(left(p), false);
            for (int i = 1; i <= steps; i++) {
                double[] q = {p[0] + dx * i / steps, p[1] + dy * i / steps};
                s.mouseDragged(left(q), dx / steps, dy / steps);
            }
            lastDragEnd = new double[] {p[0] + dx, p[1] + dy};
        });
    }

    static Step release(String label) {
        return HologramCapture.run("release " + label, () -> {
            PhoneAppGridScreen s = grid();
            if (s != null) s.mouseReleased(left(lastDragEnd));
        });
    }

    /**
     * The Codex app (main page, index 0): unselected, hovered, pressed by mouse, focused by keyboard and pressed with
     * Enter. It gives the normal press feedback and opens nothing.
     */
    private static List<Step> codex() {
        List<Step> s = new ArrayList<>();
        Object[] before = new Object[1];
        s.add(HologramCapture.check("Codex leads the main page (row 1: Codex, Map, Inventory)",
                () -> grid() != null && grid().page() == grid().mainPage() && "Codex".equals(grid().appLabel(0))
                        && "Map".equals(grid().appLabel(1)) && "Inventory".equals(grid().appLabel(2))));
        s.add(PhoneCapture.mouse("away (Codex unselected)", () -> new double[] {2, 2}));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("99_codex_unselected"));
        s.add(PhoneCapture.mouse("Codex", () -> tile(0)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("99_codex_hover"));
        s.add(HologramCapture.run("remember the screen", () -> before[0] = Minecraft.getInstance().gui.screen()));
        s.add(clickAt("Codex", () -> tile(0)));
        s.add(HologramCapture.screenshot("99_codex_mouse_press"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("clicking Codex keeps the same home screen open on the same page (nothing opened)",
                () -> Minecraft.getInstance().gui.screen() == before[0] && grid() != null && grid().page() == grid().mainPage()));
        s.add(PhoneCapture.mouse("away", () -> new double[] {2, 2}));
        for (int i = 0; i < 3; i++) s.add(PhoneCapture.key("Up", GLFW.GLFW_KEY_UP));
        s.add(PhoneCapture.key("Left", GLFW.GLFW_KEY_LEFT));
        s.add(PhoneCapture.key("Left", GLFW.GLFW_KEY_LEFT));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("99_codex_keyboard_focus"));
        s.add(PhoneCapture.key("Enter", GLFW.GLFW_KEY_ENTER));
        s.add(HologramCapture.screenshot("99_codex_enter_press"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("Enter on Codex keeps the same home screen open (no screen, no error, no lock reason)",
                () -> Minecraft.getInstance().gui.screen() == before[0] && grid() != null && grid().page() == grid().mainPage()));
        return s;
    }

    static List<Step> states() {
        List<Step> s = new ArrayList<>();
        s.add(HologramCapture.check("normal play: one home page, no page indicator",
                () -> grid() != null && grid().pageCount() == 1));
        s.add(HologramCapture.check("favourites dock: Character, Skills, Quests, Camera",
                () -> grid() != null && "Character".equals(grid().dockLabel(0)) && "Skills".equals(grid().dockLabel(1))
                        && "Quests".equals(grid().dockLabel(2)) && "Camera".equals(grid().dockLabel(3)) && grid().dockLabel(4) == null));
        s.add(PhoneCapture.mouse("Inventory", () -> tile(2)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("94_phone_hover_unlocked"));
        s.add(PhoneCapture.mouse("Spells (locked)", () -> tile(3)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("94_phone_hover_locked_tooltip"));
        s.add(PhoneCapture.mouse("dock: Quests", () -> dock(2)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("94_phone_hover_dock"));
        // Keyboard: right, right, down -> Classes (locked, tooltip at the icon).
        s.add(PhoneCapture.key("Right", GLFW.GLFW_KEY_RIGHT));
        s.add(PhoneCapture.key("Right", GLFW.GLFW_KEY_RIGHT));
        s.add(PhoneCapture.key("Down", GLFW.GLFW_KEY_DOWN));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("95_phone_keyboard_focus"));
        s.add(PhoneCapture.key("Right past the edge (single page: stays)", GLFW.GLFW_KEY_RIGHT));
        s.add(HologramCapture.check("keyboard: Right past the last column stays on the only page", () -> grid() != null && grid().page() == 0));
        // Press feedback on an entry with no action yet (Settings, index 11).
        s.add(PhoneCapture.mouse("Settings", () -> tile(11)));
        s.add(HologramCapture.waitTicks(2));
        s.add(clickAt("Settings", () -> tile(11)));
        s.add(HologramCapture.screenshot("96_phone_press_feedback"));
        s.add(HologramCapture.check("a no-op entry leaves the phone open", () -> grid() != null));
        s.addAll(codex());
        s.add(PhoneCapture.mouse("away", () -> new double[] {2, 2}));
        s.add(HologramCapture.waitTicks(3));
        return s;
    }
}
