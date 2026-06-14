package zcylas.totality.screen.pouch;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import zcylas.totality.api.menu.SortableContainerMenu;
import zcylas.totality.menu.ComponentPouchMenu;
import zcylas.totality.networking.menu.ContainerSortPayload;
import zcylas.totality.util.InventorySortHelper;
import zcylas.totality.util.TotalityKeyHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Component Pouch screen with four sort/transfer buttons above the panel.
 *
 * Sort is computed client-side for proper localized alphabetical order:
 *   Normal click  → sort A–Z by display name
 *   Shift click   → sort by quantity (most first)
 *   Ctrl click    → sort by type (grouped by category)
 */
public class ComponentPouchScreen extends AbstractContainerScreen<ComponentPouchMenu> {

    private static final Identifier TEXTURE =
            Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");

    private static final int BUTTON_W     = 22;
    private static final int BUTTON_H     = 12;
    private static final int BUTTON_GAP   = 2;
    private static final int BUTTON_COUNT = 4;

    private static final String[] LABELS = { "Sort", "Stack", "→ Bag", "← Inv" };
    private static final String[] TOOLTIPS = {
            "Sort pouch  |  Shift: by quantity  |  Ctrl: by type",
            "Quick-stack matching items from inventory  |  Shift: include hotbar",
            "Transfer all spell materials to pouch  |  Shift: include hotbar",
            "Transfer all items to inventory"
    };

    public ComponentPouchScreen(ComponentPouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected void init() { super.init(); }

    // ── Button hit testing ────────────────────────────────────────────────────

    private int buttonX(int index) {
        int totalW = BUTTON_COUNT * BUTTON_W + (BUTTON_COUNT - 1) * BUTTON_GAP;
        return leftPos + (imageWidth - totalW) / 2 + index * (BUTTON_W + BUTTON_GAP);
    }

    private int buttonY() { return topPos - BUTTON_H - 3; }

    private boolean isHovered(int index, int mx, int my) {
        int bx = buttonX(index);
        int by = buttonY();
        return mx >= bx && mx < bx + BUTTON_W && my >= by && my < by + BUTTON_H;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
        g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE,
                leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int by = buttonY();
        for (int i = 0; i < BUTTON_COUNT; i++) {
            int bx = buttonX(i);
            boolean hovered = isHovered(i, mx, my);
            g.fill(RenderPipelines.GUI, bx, by, bx + BUTTON_W, by + BUTTON_H,
                    hovered ? 0xFFB0A060 : 0xFF705830);
            g.fill(RenderPipelines.GUI, bx, by, bx+BUTTON_W, by+1, 0xFF404020);
            g.fill(RenderPipelines.GUI, bx, by+BUTTON_H-1, bx+BUTTON_W, by+BUTTON_H, 0xFF404020);
            g.fill(RenderPipelines.GUI, bx, by, bx+1, by+BUTTON_H, 0xFF404020);
            g.fill(RenderPipelines.GUI, bx+BUTTON_W-1, by, bx+BUTTON_W, by+BUTTON_H, 0xFF404020);
            int tw = font.width(LABELS[i]);
            g.text(font, LABELS[i],
                    bx + (BUTTON_W - tw) / 2,
                    by + (BUTTON_H - font.lineHeight) / 2 + 1,
                    0xFFFFEE88, false);
        }
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor g, int mx, int my) {
        g.text(font, title, titleLabelX, titleLabelY, 0x404040, false);
        g.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
        for (int i = 0; i < BUTTON_COUNT; i++) {
            if (isHovered(i, mx, my)) {
                g.setTooltipForNextFrame(font, Component.literal(TOOLTIPS[i]), mx, my);
            }
        }
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        boolean shift = TotalityKeyHelper.isShiftPressed();
        boolean ctrl  = TotalityKeyHelper.isCtrlPressed();

        if (isHovered(0, mx, my)) {
            sendSort(shift, ctrl);
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            return true;
        }
        for (int i = 1; i < BUTTON_COUNT; i++) {
            if (isHovered(i, mx, my)) {
                int[] actions = { ContainerSortPayload.QUICK_STACK,
                        ContainerSortPayload.TO_CONTAINER,
                        ContainerSortPayload.TO_INVENTORY };
                ContainerSortPayload.send(actions[i - 1], shift);
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ── Client-side sort computation ──────────────────────────────────────────

    private void sendSort(boolean shift, boolean ctrl) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.player.containerMenu instanceof SortableContainerMenu sortable)) return;

        int count = sortable.getContainerSlotCount();
        List<ItemStack> current = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            current.add(mc.player.containerMenu.getSlot(i).getItem());
        }

        InventorySortHelper.SortMode mode = ctrl ? InventorySortHelper.SortMode.BY_TYPE
                : shift ? InventorySortHelper.SortMode.BY_COUNT
                : InventorySortHelper.SortMode.ALPHABETICAL;

        List<ItemStack> sorted = InventorySortHelper.computeSortedStacks(current, mode);
        ContainerSortPayload.sendSort(sorted, shift);
    }
}