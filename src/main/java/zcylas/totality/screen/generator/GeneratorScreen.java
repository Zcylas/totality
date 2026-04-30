package zcylas.totality.screen.generator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.client.gui.tab.EnergyConfigTab;
import zcylas.totality.client.gui.tab.GuiTab;
import zcylas.totality.client.gui.tab.InfoTab;
import zcylas.totality.menu.generator.GeneratorMenu;

import java.util.List;

public class GeneratorScreen extends AbstractContainerScreen<GeneratorMenu> {

    private static final int TAB_X = -24;
    private static final int TAB_Y = 6;
    private static final int TAB_W = 24;
    private static final int TAB_H = 24;

    private EnergyConfigTab energyConfigTab;
    private InfoTab infoTab;
    private boolean configTabOpen = false;
    private boolean infoTabOpen = false;

    public GeneratorScreen(GeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        energyConfigTab = new EnergyConfigTab(menu.getBlockPos(), menu::getFacing);
        if (GuiTab.isPinned(menu.getBlockPos(), "energy")) {
            configTabOpen = true;
            energyConfigTab.open();
        }
        infoTab = new InfoTab(List.of(
                Component.literal("Burns fuel to produce UE energy."),
                Component.literal("Rate: 10 UE/t"),
                Component.literal("Capacity: 40,000 UE")
        ));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.CONFIG_BACKGROUND,
                leftPos, topPos, imageWidth, imageHeight);

        // Config tab button
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT_TAB,
                leftPos + TAB_X, topPos + TAB_Y, TAB_W, TAB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.ICON_CONFIG,
                leftPos + TAB_X + 3, topPos + TAB_Y + 3, 16, 16);

        // Info tab button
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT_TAB,
                leftPos + TAB_X, topPos + TAB_Y + TAB_H, TAB_W, TAB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.ICON_TUTORIAL,
                leftPos + TAB_X + 3, topPos + TAB_Y + TAB_H + 3, 16, 16);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.ENERGY_BAR_BACKGROUND,
                leftPos + 9, topPos + 18, 14, 44);

        int energyScale = menu.getScaledEnergy(42);
        if (energyScale > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    TotalityGuiSprites.ENERGY_BAR_FILL,
                    12, 42,
                    0, 42 - energyScale,
                    leftPos + 10, topPos + 19 + (42 - energyScale),
                    12, energyScale);
        }

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT,
                leftPos + 7, topPos + 64, 18, 18);

        boolean isBurning = menu.getBurnTime() > 0;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                isBurning ? TotalityGuiSprites.BOLT_LIT : TotalityGuiSprites.BOLT_UNLIT,
                leftPos + 80, topPos + 52, 16, 16);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT,
                leftPos + 79, topPos + 31, 18, 18);

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                        TotalityGuiSprites.SLOT,
                        leftPos + 7 + col * 18, topPos + 84 + row * 18, 18, 18);
            }
        }

        for (int col = 0; col < 9; col++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    TotalityGuiSprites.SLOT,
                    leftPos + 7 + col * 18, topPos + 142, 18, 18);
        }
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title,
                imageWidth / 2 - font.width(title) / 2, 6, 0xFF404040, false);
        graphics.text(font, playerInventoryTitle,
                imageWidth / 2 - font.width(playerInventoryTitle) / 2,
                inventoryLabelY, 0xFF404040, false);

        if (configTabOpen || infoTabOpen) return;

        if (mouseX >= leftPos + 9 && mouseX <= leftPos + 23
                && mouseY >= topPos + 18 && mouseY <= topPos + 62) {
            long stored = menu.getStoredEnergy();
            long max = menu.getMaxEnergy();
            int percent = max == 0 ? 0 : (int) (stored * 100 / max);
            graphics.setTooltipForNextFrame(font,
                    Component.literal(stored + " / " + max + " UE (" + percent + "%)"),
                    mouseX, mouseY);
        }

        if (mouseX >= leftPos + 80 && mouseX <= leftPos + 96
                && mouseY >= topPos + 52 && mouseY <= topPos + 68) {
            boolean isBurning = menu.getBurnTime() > 0;
            int burnPercent = menu.getTotalBurnTime() == 0 ? 0 :
                    menu.getBurnTime() * 100 / menu.getTotalBurnTime();
            graphics.setTooltipForNextFrame(font,
                    Component.literal(isBurning ? "Burning: " + burnPercent + "% remaining" : "Idle"),
                    mouseX, mouseY);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTicks);
        if (configTabOpen && energyConfigTab.wasClosedByX()) {
            configTabOpen = false;
            energyConfigTab.resetClosedByX();
        }
        if (configTabOpen) {
            graphics.nextStratum();
            energyConfigTab.draw(graphics, leftPos, topPos, mouseX, mouseY);
        }
        if (infoTabOpen) {
            graphics.nextStratum();
            infoTab.draw(graphics, leftPos, topPos, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouse, boolean doubleClick) {
        double mouseX = mouse.x();
        double mouseY = mouse.y();
        int button = mouse.button();

        // Config tab button click
        if (mouseX >= leftPos + TAB_X && mouseX <= leftPos + TAB_X + TAB_W
                && mouseY >= topPos + TAB_Y && mouseY <= topPos + TAB_Y + TAB_H) {
            if (!configTabOpen) {
                configTabOpen = true;
                infoTabOpen = false;
                energyConfigTab.open();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        if (mouseX >= leftPos + TAB_X && mouseX <= leftPos + TAB_X + TAB_W
                && mouseY >= topPos + TAB_Y + TAB_H && mouseY <= topPos + TAB_Y + TAB_H * 2) {
            infoTabOpen = !infoTabOpen;
            if (infoTabOpen) {
                configTabOpen = false;
                energyConfigTab.close();
            }
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        if (configTabOpen && energyConfigTab.click(mouseX - leftPos, mouseY - topPos, button)) {
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public void onClose() {
        energyConfigTab.close();
        super.onClose();
    }
}