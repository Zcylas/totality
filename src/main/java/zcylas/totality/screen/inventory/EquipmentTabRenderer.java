package zcylas.totality.screen.inventory;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import static zcylas.totality.screen.inventory.InventoryColors.*;
import static zcylas.totality.screen.inventory.InventoryLayout.*;
import static zcylas.totality.screen.inventory.InventoryItemList.inBounds;
import static zcylas.totality.screen.inventory.InventoryItemList.truncate;

/**
 * Renders the Equipment tab (full-body slot overview).
 * Instantiated by TotalityInventoryScreen — holds its own selected/hovered slot state.
 * Future: will manage ring/amulet/belt slot data when those systems are implemented.
 */
public final class EquipmentTabRenderer {

    public static final String[] SLOT_LABELS = {
            "Head", "Chest", "Legs", "Feet", "Main Hand", "Off Hand",
            "Belt", "Ring 1", "Ring 2", "Amulet"
    };

    private int selectedSlot = -1;
    private int hoveredSlot  = -1;

    public int  getSelectedSlot() { return selectedSlot; }
    public int  getHoveredSlot()  { return hoveredSlot; }
    public void clearSelected()   { selectedSlot = -1; }

    /** Click handling — call from mouseClicked. Returns true if a slot was hit. */
    public boolean mouseClicked(int mx, int my, int screenWidth, int screenHeight) {
        int cols = 2, slotW = (screenWidth-PADDING*3)/cols, slotH = 24, slotGap = 6;
        int startY = PADDING + TAB_H + 10;
        for (int i = 0; i < SLOT_LABELS.length; i++) {
            int sx = PADDING + (i%cols)*(slotW+PADDING), sy = startY + (i/cols)*(slotH+slotGap);
            if (inBounds(mx, my, sx, sy, slotW, slotH)) {
                if (selectedSlot == i) {
                    // Double-click / re-click = unequip request; caller handles it
                    return true; // caller checks selectedSlot == i to trigger unequip
                }
                selectedSlot = i;
                return true;
            }
        }
        return false;
    }

    /**
     * Returns true if the click was on an already-selected filled slot
     * (i.e. an unequip was requested).
     */
    public boolean isUnequipClick(int mx, int my, int screenWidth, int screenHeight, Player player) {
        int cols = 2, slotW = (screenWidth-PADDING*3)/cols, slotH = 24, slotGap = 6;
        int startY = PADDING + TAB_H + 10;
        for (int i = 0; i < SLOT_LABELS.length; i++) {
            int sx = PADDING + (i%cols)*(slotW+PADDING), sy = startY + (i/cols)*(slotH+slotGap);
            if (inBounds(mx, my, sx, sy, slotW, slotH)) {
                return i == selectedSlot && !getEquippedForSlot(player, i).isEmpty();
            }
        }
        return false;
    }

    public void draw(GuiGraphicsExtractor g, Font font,
                     int screenWidth, int screenHeight,
                     int mx, int my, int ba,
                     Player player) {
        int cols  = 2;
        int slotW = (screenWidth - PADDING*3) / cols;
        int slotH = 24, slotGap = 6;
        int startY = PADDING + TAB_H + 10;
        hoveredSlot = -1;

        for (int i = 0; i < SLOT_LABELS.length; i++) {
            int col = i % cols, row = i / cols;
            int sx  = PADDING + col*(slotW+PADDING), sy = startY + row*(slotH+slotGap);

            boolean sel   = i == selectedSlot;
            boolean hov   = inBounds(mx, my, sx, sy, slotW, slotH);
            if (hov) hoveredSlot = i;

            ItemStack equipped = getEquippedForSlot(player, i);
            boolean   filled   = !equipped.isEmpty();

            int bg = withAlpha(sel ? ROW_SEL : hov ? ROW_HOV
                    : filled ? EQUIP_FILLED : EQUIP_SLOT, ba);
            g.fill(sx, sy, sx+slotW, sy+slotH, bg);
            if (sel) g.fill(sx-1, sy-1, sx+slotW+1, sy+slotH+1, withAlpha(BORDER_GLOW, ba/2));

            int bc = withAlpha(sel ? VALUE : filled ? BORDER : 0xFF1A3A5A, ba);
            g.fill(sx,         sy,         sx+slotW, sy+1,       bc);
            g.fill(sx,         sy+slotH-1, sx+slotW, sy+slotH,   bc);
            g.fill(sx,         sy,         sx+1,     sy+slotH,   bc);
            g.fill(sx+slotW-1, sy,         sx+slotW, sy+slotH,   bc);

            g.text(font, Component.literal(SLOT_LABELS[i]),
                    sx+PADDING, sy+3, withAlpha(LABEL, ba), false);

            if (filled) {
                g.item(equipped, sx+slotW-14, sy+4);
                g.text(font, Component.literal(
                                truncate(font, equipped.getHoverName().getString(), slotW-PADDING*2-16)),
                        sx+PADDING, sy+13, withAlpha(VALUE, ba), false);
            } else {
                g.text(font, Component.literal(emptyLabel(i)),
                        sx+PADDING, sy+13, withAlpha(0xFF2A4A5A, ba), false);
            }
        }

        int warnY = startY + (SLOT_LABELS.length/cols + 1)*(slotH+slotGap);
        g.text(font, Component.literal("⚠ Belt / Rings / Amulet slots coming soon"),
                PADDING, warnY, withAlpha(LABEL, ba/2), false);

        if (selectedSlot >= 0 && !getEquippedForSlot(player, selectedSlot).isEmpty()) {
            String hint = "[E] Unequip";
            int bottomH = BOTTOM_H;
            g.text(font, Component.literal(hint),
                    screenWidth/2 - font.width(hint)/2,
                    screenHeight - bottomH - PADDING - 8,
                    withAlpha(VALUE, ba), false);
        }
    }

    public static ItemStack getEquippedForSlot(Player player, int i) {
        return switch (i) {
            case 0 -> player.getItemBySlot(EquipmentSlot.HEAD);
            case 1 -> player.getItemBySlot(EquipmentSlot.CHEST);
            case 2 -> player.getItemBySlot(EquipmentSlot.LEGS);
            case 3 -> player.getItemBySlot(EquipmentSlot.FEET);
            case 4 -> player.getItemBySlot(EquipmentSlot.MAINHAND);
            case 5 -> player.getItemBySlot(EquipmentSlot.OFFHAND);
            default -> ItemStack.EMPTY;
        };
    }

    /** The EquipmentSlot corresponding to a slot index (0–5). Null for custom slots 6–9. */
    public static EquipmentSlot equipmentSlotFor(int i) {
        return switch (i) {
            case 0 -> EquipmentSlot.HEAD;
            case 1 -> EquipmentSlot.CHEST;
            case 2 -> EquipmentSlot.LEGS;
            case 3 -> EquipmentSlot.FEET;
            case 4 -> EquipmentSlot.MAINHAND;
            case 5 -> EquipmentSlot.OFFHAND;
            default -> null;
        };
    }

    private static String emptyLabel(int i) {
        return switch (i) {
            case 6    -> "No belt equipped";
            case 7, 8 -> "No ring equipped  (max 2)";
            case 9    -> "No amulet equipped";
            default   -> "Empty";
        };
    }
}