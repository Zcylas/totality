package zcylas.totality.util;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Utility for reading modifier key states directly via GLFW.
 * Works in GUI screens where Minecraft's own keybind API isn't available.
 *
 * Ported/adapted from Traveler's Backpack KeyHelper.
 */
public final class TotalityKeyHelper {

    private TotalityKeyHelper() {}

    public static boolean isShiftPressed() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT)  == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    public static boolean isCtrlPressed() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL)  == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
    }

    public static boolean isAltPressed() {
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT)  == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;
    }

    public static boolean isKeyDown(int glfwKey) {
        return GLFW.glfwGetKey(Minecraft.getInstance().getWindow().handle(), glfwKey)
                == GLFW.GLFW_PRESS;
    }
}