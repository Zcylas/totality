package zcylas.totality.client.gui.tab;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import zcylas.totality.client.gui.TotalityGuiSprites;

import java.util.List;

public class InfoTab extends GuiTab {

    private static final int PANEL_X = 8;
    private static final int PANEL_Y = 15;
    private static final int PANEL_W = 100;
    private static final int PANEL_PADDING = 6;

    private final List<Component> lines;
    private final net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

    public InfoTab(List<Component> lines) {
        this.lines = lines;
    }

    @Override
    public void draw(GuiGraphicsExtractor drawContext, int guiLeft, int guiTop, int mouseX, int mouseY) {
        // Calculate panel width based on longest line
        int maxWidth = 0;
        for (Component line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }
        int panelW = maxWidth + PANEL_PADDING * 2;
        int panelH = PANEL_PADDING * 2 + lines.size() * (font.lineHeight + 2);

        drawContext.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.CONFIG_BACKGROUND,
                guiLeft + PANEL_X - PANEL_PADDING,
                guiTop + PANEL_Y - PANEL_PADDING,
                panelW + PANEL_PADDING * 2,
                panelH);

        int textY = guiTop + PANEL_Y;
        for (Component line : lines) {
            drawContext.text(font, line,
                    guiLeft + PANEL_X, textY, 0xFF404040, false);
            textY += font.lineHeight + 2;
        }
    }
}