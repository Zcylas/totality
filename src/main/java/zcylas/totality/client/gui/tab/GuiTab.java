package zcylas.totality.client.gui.tab;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

import java.util.HashSet;
import java.util.Set;

public abstract class GuiTab {

    private static final Set<BlockPos> pinnedPositions = new HashSet<>();

    public static boolean isPinned(BlockPos pos) {
        return pinnedPositions.contains(pos);
    }

    public static void pin(BlockPos pos) {
        pinnedPositions.add(pos);
    }

    public static void unpin(BlockPos pos) {
        pinnedPositions.remove(pos);
    }

    public void open() {}
    public void close() {}

    public abstract void draw(GuiGraphicsExtractor drawContext, int guiLeft, int guiTop, int mouseX, int mouseY);

    public boolean click(double mouseX, double mouseY, int button) {
        return false;
    }
}