package zcylas.totality.screen.energy;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import zcylas.totality.client.gui.TotalityGuiSprites;
import zcylas.totality.client.gui.tab.EnergyConfigTab;
import zcylas.totality.client.gui.tab.GuiTab;
import zcylas.totality.client.gui.tab.InfoTab;
import zcylas.totality.client.gui.tab.ItemConfigTab;
import zcylas.totality.menu.energy.ElectricFurnaceMenu;

import java.util.List;

public class ElectricFurnaceScreen extends AbstractContainerScreen<ElectricFurnaceMenu> {

    // ── Left tabs (energy config + info) ──────────────────────────────────────
    private static final int LEFT_TAB_X = -24;
    private static final int LEFT_TAB_Y = 6;
    private static final int TAB_W      = 24;
    private static final int TAB_H      = 24;

    // ── Right tab (item config) ───────────────────────────────────────────────
    private static final int RIGHT_TAB_X = 176; // imageWidth
    private static final int RIGHT_TAB_Y = 6;

    // ── Vanilla chest icon for item config tab ────────────────────────────────
    private static final Identifier ICON_ITEMS =
            Identifier.withDefaultNamespace("container/chest");

    // ── Energy bar ────────────────────────────────────────────────────────────
    private static final int ENERGY_BAR_X = 9;
    private static final int ENERGY_BAR_Y = 18;
    private static final int ENERGY_BAR_W = 14;
    private static final int ENERGY_BAR_H = 44;
    private static final int ENERGY_FILL_W = 12;
    private static final int ENERGY_FILL_H = 42;

    // ── Machine slots ─────────────────────────────────────────────────────────
    private static final int INPUT_SLOT_X  = 60;
    private static final int INPUT_SLOT_Y  = 31;
    private static final int BOLT_X        = 80;
    private static final int BOLT_Y        = 35;
    private static final int OUTPUT_SLOT_X = 98;
    private static final int OUTPUT_SLOT_Y = 31;

    // ── Tabs ──────────────────────────────────────────────────────────────────
    private EnergyConfigTab energyConfigTab;
    private ItemConfigTab   itemConfigTab;
    private InfoTab         infoTab;
    private boolean configTabOpen     = false;
    private boolean itemConfigTabOpen = false;
    private boolean infoTabOpen       = false;

    public ElectricFurnaceScreen(ElectricFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected void init() {
        super.init();
        energyConfigTab = new EnergyConfigTab(menu.getBlockPos(), menu::getFacing);
        itemConfigTab   = new ItemConfigTab(menu.getBlockPos(), menu::getFacing);

        if (GuiTab.isPinned(menu.getBlockPos(), "energy")) {
            configTabOpen = true;
            energyConfigTab.open();
        } else if (GuiTab.isPinned(menu.getBlockPos(), "items")) {
            itemConfigTabOpen = true;
            itemConfigTab.open();
        }
        infoTab = new InfoTab(List.of(
                Component.literal("Uses energy to smelt items."),
                Component.literal("Cost: 1,000 UE / smelt"),
                Component.literal("Speed: 200 ticks (vanilla)"),
                Component.literal("Capacity: 10,000 UE")
        ));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.CONFIG_BACKGROUND,
                leftPos, topPos, imageWidth, imageHeight);

        // ── Left tab buttons ──────────────────────────────────────────────────
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT_TAB,
                leftPos + LEFT_TAB_X, topPos + LEFT_TAB_Y, TAB_W, TAB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.ICON_CONFIG,
                leftPos + LEFT_TAB_X + 3, topPos + LEFT_TAB_Y + 3, 16, 16);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT_TAB,
                leftPos + LEFT_TAB_X, topPos + LEFT_TAB_Y + TAB_H, TAB_W, TAB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.ICON_TUTORIAL,
                leftPos + LEFT_TAB_X + 3, topPos + LEFT_TAB_Y + TAB_H + 3, 16, 16);

        // ── Right tab button (item config) ────────────────────────────────────
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT_TAB,
                leftPos + RIGHT_TAB_X, topPos + RIGHT_TAB_Y, TAB_W, TAB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                ICON_ITEMS,
                leftPos + RIGHT_TAB_X + 3, topPos + RIGHT_TAB_Y + 3, 16, 16);

        // ── Energy bar ────────────────────────────────────────────────────────
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.ENERGY_BAR_BACKGROUND,
                leftPos + ENERGY_BAR_X, topPos + ENERGY_BAR_Y,
                ENERGY_BAR_W, ENERGY_BAR_H);
        int energyScale = menu.getScaledEnergy(ENERGY_FILL_H);
        if (energyScale > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                    TotalityGuiSprites.ENERGY_BAR_FILL,
                    ENERGY_FILL_W, ENERGY_FILL_H,
                    0, ENERGY_FILL_H - energyScale,
                    leftPos + ENERGY_BAR_X + 1, topPos + ENERGY_BAR_Y + 1 + (ENERGY_FILL_H - energyScale),
                    ENERGY_FILL_W, energyScale);
        }

        // ── Machine slots ─────────────────────────────────────────────────────
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT,
                leftPos + INPUT_SLOT_X, topPos + INPUT_SLOT_Y, 18, 18);

        boolean isSmelting = menu.getSmeltTime() > 0;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                isSmelting ? TotalityGuiSprites.BOLT_LIT : TotalityGuiSprites.BOLT_UNLIT,
                leftPos + BOLT_X, topPos + BOLT_Y, 16, 16);

        graphics.blitSprite(RenderPipelines.GUI_TEXTURED,
                TotalityGuiSprites.SLOT,
                leftPos + OUTPUT_SLOT_X, topPos + OUTPUT_SLOT_Y, 18, 18);

        // ── Player inventory + hotbar ─────────────────────────────────────────
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

        if (configTabOpen || itemConfigTabOpen || infoTabOpen) return;

        // Energy bar tooltip
        if (mouseX >= leftPos + ENERGY_BAR_X && mouseX <= leftPos + ENERGY_BAR_X + ENERGY_BAR_W
                && mouseY >= topPos + ENERGY_BAR_Y && mouseY <= topPos + ENERGY_BAR_Y + ENERGY_BAR_H) {
            long stored  = menu.getStoredEnergy();
            long max     = menu.getMaxEnergy();
            int  percent = max == 0 ? 0 : (int) (stored * 100 / max);
            graphics.setTooltipForNextFrame(font,
                    Component.literal(stored + " / " + max + " UE (" + percent + "%)"),
                    mouseX, mouseY);
        }

        // Bolt tooltip
        if (mouseX >= leftPos + BOLT_X && mouseX <= leftPos + BOLT_X + 16
                && mouseY >= topPos + BOLT_Y && mouseY <= topPos + BOLT_Y + 16) {
            int smeltTime  = menu.getSmeltTime();
            int smeltTotal = menu.getSmeltTimeTotal();
            int percent    = smeltTotal == 0 ? 0 : smeltTime * 100 / smeltTotal;
            graphics.setTooltipForNextFrame(font,
                    Component.literal(smeltTime > 0 ? "Smelting: " + percent + "% complete" : "Idle"),
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
        if (itemConfigTabOpen && itemConfigTab.wasClosedByX()) {
            itemConfigTabOpen = false;
            itemConfigTab.resetClosedByX();
        }

        if (configTabOpen) {
            graphics.nextStratum();
            energyConfigTab.draw(graphics, leftPos, topPos, mouseX, mouseY);
        }
        if (itemConfigTabOpen) {
            graphics.nextStratum();
            itemConfigTab.draw(graphics, leftPos, topPos, mouseX, mouseY);
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
        int    button = mouse.button();

        // Energy config tab button (left)
        if (mouseX >= leftPos + LEFT_TAB_X && mouseX <= leftPos + LEFT_TAB_X + TAB_W
                && mouseY >= topPos + LEFT_TAB_Y && mouseY <= topPos + LEFT_TAB_Y + TAB_H) {
            if (!configTabOpen) {
                configTabOpen     = true;
                itemConfigTabOpen = false;
                infoTabOpen       = false;
                energyConfigTab.open();
                itemConfigTab.close();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        // Info tab button (left)
        if (mouseX >= leftPos + LEFT_TAB_X && mouseX <= leftPos + LEFT_TAB_X + TAB_W
                && mouseY >= topPos + LEFT_TAB_Y + TAB_H && mouseY <= topPos + LEFT_TAB_Y + TAB_H * 2) {
            infoTabOpen = !infoTabOpen;
            if (infoTabOpen) {
                configTabOpen     = false;
                itemConfigTabOpen = false;
                energyConfigTab.close();
                itemConfigTab.close();
            }
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }

        // Item config tab button (right)
        if (mouseX >= leftPos + RIGHT_TAB_X && mouseX <= leftPos + RIGHT_TAB_X + TAB_W
                && mouseY >= topPos + RIGHT_TAB_Y && mouseY <= topPos + RIGHT_TAB_Y + TAB_H) {
            if (!itemConfigTabOpen) {
                itemConfigTabOpen = true;
                configTabOpen     = false;
                infoTabOpen       = false;
                itemConfigTab.open();
                energyConfigTab.close();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        if (configTabOpen && energyConfigTab.click(mouseX - leftPos, mouseY - topPos, button)) {
            return true;
        }
        if (itemConfigTabOpen && itemConfigTab.click(mouseX - leftPos, mouseY - topPos, button)) {
            return true;
        }

        return super.mouseClicked(mouse, doubleClick);
    }

    @Override
    public void onClose() {
        energyConfigTab.close();
        itemConfigTab.close();
        super.onClose();
    }
}