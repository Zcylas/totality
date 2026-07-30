package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

import java.util.ArrayList;
import java.util.List;

public class TooltipPainter {

    /**
     * Blended toward a neutral dark ground before drawing (visual-correction pass, Finding 6):
     * the panel background used to paint {@code theme.bgTop()}/{@code bgBottom()} at full
     * saturation across the whole panel, which — combined with the old wider panel — read as an
     * oversaturated colored block competing with the content on top of it. Rarity identity now
     * lives primarily in the border, title, and badge colors (all drawn separately, still at full
     * saturation); the background itself is desaturated toward neutral so it recedes behind
     * content instead of dominating it. {@link TooltipTheme}'s own per-rarity palettes are
     * untouched — this blend happens only here, at draw time, not in the theme data.
     */
    public static void drawBackground(GuiGraphicsExtractor graphics, int x, int y, int w, int h,
                                      TooltipTheme theme) {
        int top = blendTowardNeutral(theme.bgTop(), 0.35f);
        int bottom = blendTowardNeutral(theme.bgBottom(), 0.35f);
        graphics.fillGradient(x, y, x + w, y + h, top, bottom);
    }

    private static final int NEUTRAL_GROUND = 0xFF19191C;

    private static int blendTowardNeutral(int color, float amount) {
        int r = (int) (((color >> 16) & 0xFF) * (1 - amount) + ((NEUTRAL_GROUND >> 16) & 0xFF) * amount);
        int g = (int) (((color >> 8) & 0xFF) * (1 - amount) + ((NEUTRAL_GROUND >> 8) & 0xFF) * amount);
        int b = (int) ((color & 0xFF) * (1 - amount) + (NEUTRAL_GROUND & 0xFF) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    public static void drawSeparator(GuiGraphicsExtractor graphics, int x, int y, int width, TooltipTheme theme) {
        int lineY = y + 4;
        int midX = x + width / 2;
        graphics.fill(x + 4, lineY, midX - 5, lineY + 1, theme.separator());
        graphics.fill(midX + 5, lineY, x + width - 4, lineY + 1, theme.separator());
        drawSmallDiamond(graphics, midX, lineY, theme.border());
    }

    public static void drawText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, true);
    }

    public static void drawText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.text(font, text, x, y, color, true);
    }

    public static void drawBadge(GuiGraphicsExtractor graphics, Font font, String label,
                                 int x, int y, TooltipTheme theme) {
        int textW = font.width(label);
        int padH = 3;
        int badgeH = font.lineHeight;
        int badgeW = textW + padH * 2;
        graphics.fill(x, y, x + badgeW, y + badgeH, theme.badgeBg());
        drawText(graphics, font, label, x + padH, y + 1, theme.badgeCutout());
    }

    public static void drawItem(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
        graphics.item(stack, x, y);
    }

    public static List<String> wrapText(String text, Font font, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.width(candidate) > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) lines.add(current.toString());
        return lines;
    }
    public static void drawSmallDiamond(GuiGraphicsExtractor graphics, int cx, int cy, int color) {
        graphics.fill(cx, cy - 1, cx + 1, cy, color);
        graphics.fill(cx - 1, cy, cx + 2, cy + 1, color);
        graphics.fill(cx, cy + 1, cx + 1, cy + 2, color);
    }

    private static boolean isShiftDown() {
        long window = net.minecraft.client.Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    private TooltipPainter() {}
}