package zcylas.totality.client.tooltip.renderer;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemType;
import zcylas.totality.api.core.rpgutils.rarity.ItemTypeComponent;
import zcylas.totality.api.core.rpgutils.WeightComponent;
import zcylas.totality.api.industrial.energy.UEFormat;
import zcylas.totality.api.industrial.energy.UEItem;
import zcylas.totality.client.tooltip.TotalityIcons;
import zcylas.totality.client.tooltip.theme.TooltipTheme;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;

public class TooltipStatBlock {

    private static final int CYAN   = 0xFF42C8F5;
    private static final int ORANGE = 0xFFFF6600;
    private static final int GRAY   = 0xFF888888;
    private static final int GOLD   = 0xFFFFD700;

    public static int draw(GuiGraphicsExtractor graphics, Font font, ItemStack stack,
                           int x, int y, int panelW, int padding, TooltipTheme theme) {

        boolean hasEnergy  = stack.getItem() instanceof UEItem;
        boolean hasFuel    = isFuel(stack);
        boolean hasWeight  = stack.has(ItemComponents.getWeight());

        if (!hasEnergy && !hasFuel && !hasWeight) return 0;

        int boxX = x;
        int boxW = panelW - padding * 2;
        int cursorY = y;

        // Collect rows first so we know the height
        int rowH = font.lineHeight + 2;
        int rows = 0;
        if (hasEnergy) rows += 2; // energy + I/O
        if (hasFuel)   rows += 1;
        if (hasWeight) rows += 1;

        boolean energyAndOther = hasEnergy && (hasFuel || hasWeight);

        int barH     = hasEnergy ? 6 : 0;
        int dividerH = energyAndOther ? 3 : 0;
        int boxH     = 6 + barH + rows * rowH + dividerH + 4;

        // Draw box outline
        int outlineColor = lighten(theme.separator(), 0.15f);
        graphics.fill(boxX,          cursorY,          boxX + boxW,     cursorY + 1,      outlineColor);
        graphics.fill(boxX,          cursorY + boxH,   boxX + boxW,     cursorY + boxH + 1, outlineColor);
        graphics.fill(boxX,          cursorY,           boxX + 1,        cursorY + boxH + 1, outlineColor);
        graphics.fill(boxX + boxW - 1, cursorY,         boxX + boxW,     cursorY + boxH + 1, outlineColor);

        cursorY += 4;

        int innerX = boxX + 4;
        int innerW = boxW - 8;

        // Energy group
        if (hasEnergy) {
            UEItem ue = (UEItem) stack.getItem();
            long stored = ue.getStoredEnergy(stack);
            long cap    = ue.getEnergyCapacity(stack);
            long maxIn  = ue.getEnergyMaxInput(stack);
            long maxOut = ue.getEnergyMaxOutput(stack);
            int pct     = cap > 0 ? (int)((stored * 100L) / cap) : 0;
            int barColor = ue.getEnergyBarColor(stack) | 0xFF000000;

            boolean shift = isShiftDown();

            // Bar
            int filled = cap > 0 ? (int)((stored * innerW) / cap) : 0;
            graphics.fill(innerX,           cursorY, innerX + innerW,  cursorY + 4, 0xFF1a1a2e);
            graphics.fill(innerX,           cursorY, innerX + filled,  cursorY + 4, barColor);
            cursorY += 6;

            // Energy row
            String energyVal = shift
                    ? stored + " / " + cap + " UE (" + pct + "%)"
                    : UEFormat.energy(stored) + " / " + UEFormat.energy(cap) + " UE (" + pct + "%)";
            drawStatRow(graphics, font, innerX, innerW, cursorY,
                    TotalityIcons.ENERGY, CYAN, "Energy", energyVal, CYAN);
            cursorY += rowH;

            // I/O row
            String ioVal = shift
                    ? maxIn + " / " + maxOut + " UE/t"
                    : UEFormat.energy(maxIn) + " / " + UEFormat.energy(maxOut) + " UE/t";
            drawStatRow(graphics, font, innerX, innerW, cursorY,
                    TotalityIcons.ENERGY, CYAN, "I/O Rate", ioVal, GRAY);
            cursorY += rowH;

            if (hasFuel || hasWeight) {
                cursorY += 1;
                graphics.fill(innerX, cursorY + 1, innerX + innerW, cursorY + 2, lighten(theme.separator(), 0.1f));
                cursorY += 4;
            }
        }

        // Fuel row
        if (hasFuel) {
            var level = Minecraft.getInstance().level;
            if (level != null) {
                int ticks = level.fuelValues().burnDuration(stack);
                if (ticks > 0) {
                    int seconds = ticks / 20;
                    drawStatRow(graphics, font, innerX, innerW, cursorY,
                            TotalityIcons.FLAME, ORANGE, "Burn Time", seconds + "s", ORANGE);
                    cursorY += rowH;
                }
            }
        }

        // Weight row
        if (hasWeight) {
            WeightComponent wc = stack.get(ItemComponents.getWeight());
            if (wc != null) {
                String weightVal = String.format("%.1f", wc.weight());
                drawWeightRow(graphics, font, innerX, innerW, cursorY, weightVal);
                cursorY += rowH;
            }
        }

        cursorY += 4; // bottom padding

        return cursorY - y + 1; // total height consumed
    }

    private static void drawStatRow(GuiGraphicsExtractor graphics, Font font,
                                    int x, int w, int y,
                                    String iconGlyph, int iconColor,
                                    String label, String value, int valueColor) {
        // Icon
        Component icon = TotalityIcons.icon(iconGlyph, iconColor);
        graphics.text(font, icon, x, y, iconColor, false);
        int iconW = font.width(iconGlyph) + 3;

        // Label
        graphics.text(font, label, x + iconW, y, GRAY, false);

        // Value right-aligned
        graphics.text(font, value, x + w - font.width(value), y, valueColor, true);
    }

    private static void drawWeightRow(GuiGraphicsExtractor graphics, Font font,
                                      int x, int w, int y, String value) {
        drawStatRow(graphics, font, x, w, y,
                TotalityIcons.WEIGHT, GRAY, "Weight", value, GRAY);
    }

    private static int lighten(int color, float factor) {
        int r = Math.min(255, (int)(((color >> 16) & 0xFF) + (255 - ((color >> 16) & 0xFF)) * factor));
        int g = Math.min(255, (int)(((color >> 8) & 0xFF) + (255 - ((color >> 8) & 0xFF)) * factor));
        int b = Math.min(255, (int)((color & 0xFF) + (255 - (color & 0xFF)) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static boolean isFuel(ItemStack stack) {
        ItemTypeComponent type = stack.get(ItemComponents.getItemType());
        return type != null && type.type() == ItemType.FUEL;
    }

    private static boolean isShiftDown() {
        long window = Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    public static boolean hasStats(ItemStack stack) {
        return stack.getItem() instanceof UEItem
                || isFuel(stack)
                || stack.has(ItemComponents.getWeight());
    }

    public static int estimateHeight(Font font, ItemStack stack) {
        boolean hasEnergy = stack.getItem() instanceof UEItem;
        boolean hasFuel   = isFuel(stack);
        boolean hasWeight = stack.has(ItemComponents.getWeight());
        int rowH = font.lineHeight + 2;
        int rows = 0;
        if (hasEnergy) rows += 2;
        if (hasFuel)   rows += 1;
        if (hasWeight) rows += 1;
        int barH    = hasEnergy ? 6 : 0;
        int dividerH = hasEnergy && (hasFuel || hasWeight) ? 5 : 0;
        return 6 + barH + rows * rowH + dividerH + 4 + 1;
    }

    private TooltipStatBlock() {}
}