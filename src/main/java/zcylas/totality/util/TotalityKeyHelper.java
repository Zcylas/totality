package zcylas.totality.util;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Utility for reading modifier key states directly from the window.
 * Works in GUI screens where Minecraft's own keybind API isn't available.
 *
 * <p>Reads through vanilla's {@link InputConstants#isKeyDown} — exactly {@code glfwGetKey(window, key) == PRESS}
 * in normal play — so simulated input (Fabric client game tests) is observed the same way as a real keyboard.
 *
 * Ported/adapted from Traveler's Backpack KeyHelper.
 */
public final class TotalityKeyHelper {

    private TotalityKeyHelper() {}

    public static boolean isShiftPressed() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public static boolean isCtrlPressed() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_CONTROL) || isKeyDown(GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public static boolean isAltPressed() {
        return isKeyDown(GLFW.GLFW_KEY_LEFT_ALT) || isKeyDown(GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public static boolean isKeyDown(int glfwKey) {
        return InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), glfwKey);
    }
}