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

/** Interaction states of the app grid for the capture run: hover, lock tooltip, keyboard focus, pages, press. */
final class PhoneCaptureStates {

    private PhoneCaptureStates() {}

    private static PhoneAppGridScreen grid() {
        return Minecraft.getInstance().gui.screen() instanceof PhoneAppGridScreen s ? s : null;
    }

    private static double[] tile(int i) {
        PhoneAppGridScreen s = grid();
        return s == null ? new double[] {0, 0} : s.tileCentre(i);
    }

    private static Step clickAt(String label, java.util.function.Supplier<double[]> at) {
        return HologramCapture.run("click " + label, () -> {
            PhoneAppGridScreen s = grid();
            if (s == null) return;
            double[] p = at.get();
            s.mouseClicked(new MouseButtonEvent(p[0], p[1], new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0)), false);
        });
    }

    /**
     * The Codex tile (page 1, index 3, where the Bestiary placeholder was): unselected, hovered, pressed by mouse,
     * focused by keyboard and pressed with Enter. It gives the normal press feedback and opens nothing.
     */
    private static List<Step> codex() {
        List<Step> s = new ArrayList<>();
        Object[] before = new Object[1];
        s.add(HologramCapture.check("the Codex tile replaced the Bestiary placeholder (page 1, index 3)",
                () -> grid() != null && grid().page() == 0 && "Codex".equals(grid().appLabel(3))));
        s.add(PhoneCapture.mouse("away (Codex unselected)", () -> new double[] {2, 2}));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("99_codex_unselected"));
        s.add(PhoneCapture.mouse("Codex tile", () -> tile(3)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("99_codex_hover"));
        s.add(HologramCapture.run("remember the screen", () -> before[0] = Minecraft.getInstance().gui.screen()));
        s.add(clickAt("Codex", () -> tile(3)));
        s.add(HologramCapture.screenshot("99_codex_mouse_press"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("clicking Codex keeps the same launcher open on the same page (nothing opened)",
                () -> Minecraft.getInstance().gui.screen() == before[0] && grid() != null && grid().page() == 0));
        s.add(PhoneCapture.mouse("away", () -> new double[] {2, 2}));
        s.add(PhoneCapture.key("Up", GLFW.GLFW_KEY_UP));
        s.add(PhoneCapture.key("Up", GLFW.GLFW_KEY_UP));
        s.add(PhoneCapture.key("Left", GLFW.GLFW_KEY_LEFT));
        s.add(PhoneCapture.key("Left", GLFW.GLFW_KEY_LEFT));
        s.add(PhoneCapture.key("Down (to Codex)", GLFW.GLFW_KEY_DOWN));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("99_codex_keyboard_focus"));
        s.add(PhoneCapture.key("Enter", GLFW.GLFW_KEY_ENTER));
        s.add(HologramCapture.screenshot("99_codex_enter_press"));
        s.add(HologramCapture.waitTicks(10));
        s.add(HologramCapture.check("Enter on Codex keeps the same launcher open (no screen, no error, no lock reason)",
                () -> Minecraft.getInstance().gui.screen() == before[0] && grid() != null && grid().page() == 0));
        s.add(HologramCapture.screenshot("99_codex_after_enter"));
        return s;
    }

    static List<Step> states() {
        List<Step> s = new ArrayList<>();
        s.add(PhoneCapture.mouse("Character tile", () -> tile(0)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("94_phone_hover_unlocked"));
        s.add(PhoneCapture.mouse("Spells tile (locked)", () -> tile(4)));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("94_phone_hover_locked_tooltip"));
        s.add(PhoneCapture.mouse("dock: Quests", () -> {
            PhoneAppGridScreen g = grid();
            return g == null ? new double[] {0, 0} : g.dockCentre(2);
        }));
        s.add(HologramCapture.waitTicks(4));
        s.add(HologramCapture.screenshot("94_phone_hover_dock"));
        // Keyboard: right, right, down -> Abilities (locked, tooltip at the tile).
        s.add(PhoneCapture.key("Right", GLFW.GLFW_KEY_RIGHT));
        s.add(PhoneCapture.key("Right", GLFW.GLFW_KEY_RIGHT));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.screenshot("95_phone_keyboard_focus"));
        s.add(PhoneCapture.key("Right past the edge (next page)", GLFW.GLFW_KEY_RIGHT));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("keyboard: Right past the last column turns to page 2",
                () -> grid() != null && grid().page() == 1));
        s.add(HologramCapture.screenshot("95_phone_page_2"));
        // Press feedback on an entry with no action yet (Settings, page 2 index 3).
        s.add(PhoneCapture.mouse("Settings tile", () -> tile(3)));
        s.add(HologramCapture.waitTicks(2));
        s.add(clickAt("Settings", () -> tile(3)));
        s.add(HologramCapture.screenshot("96_phone_press_feedback"));
        s.add(HologramCapture.check("a no-op entry leaves the phone open", () -> grid() != null));
        s.add(clickAt("page row, left half (previous page)", () -> {
            PhoneAppGridScreen g = grid();
            double[] a = g.dockCentre(0);
            return new double[] {a[0], a[1] - 12};
        }));
        s.add(HologramCapture.waitTicks(3));
        s.add(HologramCapture.check("clicking the page row's left half returns to page 1", () -> grid() != null && grid().page() == 0));
        s.addAll(codex());
        s.add(PhoneCapture.mouse("away", () -> new double[] {2, 2}));
        s.add(HologramCapture.waitTicks(3));
        return s;
    }
}
