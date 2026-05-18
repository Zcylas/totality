package zcylas.totality.client.gui.tab;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

public abstract class GuiTab {

    private static final Map<BlockPos, String> pinnedTabs = new HashMap<>();

    public static boolean isPinned(BlockPos pos, String tabId) {
        return tabId.equals(pinnedTabs.get(pos));
    }

    public static void pin(BlockPos pos, String tabId) {
        pinnedTabs.put(pos, tabId);
    }

    public static void unpin(BlockPos pos) {
        pinnedTabs.remove(pos);
    }

    public void open() {}
    public void close() {}

    public abstract void draw(GuiGraphicsExtractor drawContext, int guiLeft, int guiTop, int mouseX, int mouseY);

    public boolean click(double mouseX, double mouseY, int button) {
        return false;
    }

    /**
     * Returns true if the mouse is anywhere inside this tab's panel bounds.
     * Used by screens to block hover/tooltip detection from reaching slots underneath.
     * Coordinates are relative to guiLeft/guiTop (same as click()).
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return false;
    }
}