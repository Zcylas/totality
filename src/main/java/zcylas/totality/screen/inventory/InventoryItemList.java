package zcylas.totality.screen.inventory;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

import static zcylas.totality.screen.inventory.InventoryColors.*;
import static zcylas.totality.screen.inventory.InventoryLayout.*;

/**
 * Static renderer for the left-side item list panel, including clickable column headers.
 *
 * To add a new sortable column:
 *   1. Add to InventorySortManager.SortColumn.
 *   2. Add rendering here in drawHeaders() and hit-test in hitTestHeader().
 *   3. Add comparator in InventorySortManager.buildComparator().
 */
public final class InventoryItemList {

    /**
     * Draw the full item list panel.
     *
     * @return the hovered row index (-1 if none)
     */
    public static int draw(
            GuiGraphicsExtractor g, Font font,
            InventoryLayout layout, InventorySortManager sort,
            List<ItemEntry> items, int scrollOffset,
            int selectedItem, int mx, int my, int ba,
            Player player) {

        int lx = layout.listX, ly = layout.listY,
                lw = layout.listW, lh = layout.listH;

        // Panel background + border
        g.fill(lx, ly, lx+lw, ly+lh, withAlpha(0x55001020, ba));
        g.fill(lx,      ly,      lx+lw, ly+1,    withAlpha(BORDER, ba));
        g.fill(lx,      ly+lh-1, lx+lw, ly+lh,   withAlpha(BORDER, ba));
        g.fill(lx,      ly,      lx+1,  ly+lh,    withAlpha(BORDER, ba));
        g.fill(lx+lw-1, ly,      lx+lw, ly+lh,   withAlpha(BORDER, ba));

        // Column headers
        drawHeaders(g, font, layout, sort, mx, my, ba);
        g.fill(lx, ly+ROW_H, lx+lw, ly+ROW_H+1, withAlpha(SEPARATOR, ba));

        // Rows
        int startY     = ly + ROW_H + 1;
        int clipBottom = ly + lh - 1;
        int hoveredRow = -1;

        for (int i = 0; i < layout.visibleRows; i++) {
            int idx = scrollOffset + i;
            if (idx >= items.size()) break;
            ItemEntry entry = items.get(idx);
            int rowY    = startY + i * ROW_H;
            int drawBot = Math.min(rowY + ROW_H, clipBottom);
            if (rowY >= clipBottom) break;

            boolean sel = idx == selectedItem;
            boolean hov = inBounds(mx, my, lx+1, rowY, lw-2, ROW_H);
            if (hov) hoveredRow = idx;

            int rowBg = sel ? withAlpha(ROW_SEL,  ba)
                    : hov ? withAlpha(ROW_HOV,  ba)
                    : i % 2 == 0 ? withAlpha(ROW_EVEN, ba) : 0;
            if (rowBg != 0) g.fill(lx+1, rowY, lx+lw-1, drawBot, rowBg);

            int iconY = rowY + (ROW_H - 16) / 2;
            if (iconY + 16 <= clipBottom) g.item(entry.stack, lx+2, iconY);

            int textY = rowY + (ROW_H - 8) / 2;
            if (textY < clipBottom) {
                String badge  = player != null ? InventoryEquipHelper.getEquipBadge(player, entry.stack) : null;
                int    badgeW = badge != null ? font.width(badge) + 4 : 0;
                int    nameW  = lw - ICON_SIZE - 8 - 60 - badgeW;
                String name   = entry.stack.getHoverName().getString();
                if (font.width(name) > nameW) name = truncate(font, name, nameW);

                g.text(font, Component.literal(name), lx+ICON_SIZE+6, textY,
                        withAlpha(sel ? VALUE : 0xFFFFFFFF, ba), false);

                if (badge != null) {
                    int badgeX = lx + ICON_SIZE + 6 + font.width(name) + 3;
                    g.text(font, Component.literal(badge), badgeX, textY,
                            withAlpha(EQUIP_BADGE, ba), false);
                }

                if (entry.stack.getCount() > 1) {
                    String qty = "x" + entry.stack.getCount();
                    g.text(font, Component.literal(qty), lx+lw-55, textY,
                            withAlpha(LABEL, ba), false);
                }
            }
        }

        // Scrollbar
        if (items.size() > layout.visibleRows) {
            int maxScroll = items.size() - layout.visibleRows;
            int trackH    = lh - ROW_H - 4;
            int thumbH    = Math.max(10, trackH * layout.visibleRows / items.size());
            int thumbY    = ly + ROW_H + 2
                    + (int)((float) scrollOffset / maxScroll * (trackH - thumbH));
            g.fill(lx+lw-3, ly+ROW_H+2, lx+lw-1, ly+lh-2, withAlpha(0xFF001830, ba));
            g.fill(lx+lw-3, thumbY, lx+lw-1, thumbY+thumbH, withAlpha(BORDER, ba));
        }

        return hoveredRow;
    }

    // ── Column headers ────────────────────────────────────────────────────────

    private static void drawHeaders(GuiGraphicsExtractor g, Font font,
                                    InventoryLayout layout, InventorySortManager sort,
                                    int mx, int my, int ba) {
        int lx = layout.listX, ly = layout.listY, lw = layout.listW;

        String nameHdr = "NAME" + sort.arrowFor(InventorySortManager.SortColumn.NAME);
        String qtyHdr  = "QTY"  + sort.arrowFor(InventorySortManager.SortColumn.QTY);

        boolean hovName = hitTestHeader(font, nameHdr, nameHeaderX(lx), ly, mx, my);
        boolean hovQty  = hitTestHeader(font, qtyHdr,  qtyHeaderX(lx, lw), ly, mx, my);

        boolean nameActive = sort.getColumn() == InventorySortManager.SortColumn.NAME;
        boolean qtyActive  = sort.getColumn() == InventorySortManager.SortColumn.QTY;

        g.text(font, Component.literal(nameHdr), nameHeaderX(lx)+2, ly+4,
                withAlpha(hovName || nameActive ? VALUE : LABEL, ba), false);
        g.text(font, Component.literal(qtyHdr),  qtyHeaderX(lx, lw)+2, ly+4,
                withAlpha(hovQty  || qtyActive  ? VALUE : LABEL, ba), false);
    }

    /**
     * Returns the SortColumn whose header was clicked, or null if neither was hit.
     * Call this from mouseClicked to decide which column to cycle.
     */
    public static InventorySortManager.SortColumn hitTestHeaders(
            Font font, InventoryLayout layout, int mx, int my) {
        int lx = layout.listX, ly = layout.listY, lw = layout.listW;

        // We need approximate widths — compute without arrows since they shift on click.
        // Using fixed column widths is more stable; add a few px of padding for comfort.
        if (hitTestHeader(font, "NAME ↑", nameHeaderX(lx), ly, mx, my))
            return InventorySortManager.SortColumn.NAME;
        if (hitTestHeader(font, "QTY ↑", qtyHeaderX(lx, lw), ly, mx, my))
            return InventorySortManager.SortColumn.QTY;
        return null;
    }

    private static boolean hitTestHeader(Font font, String label, int x, int ly, int mx, int my) {
        return inBounds(mx, my, x, ly+1, font.width(label)+4, ROW_H-2);
    }

    // x positions for each column header
    private static int nameHeaderX(int lx) { return lx + ICON_SIZE + 4; }
    private static int qtyHeaderX(int lx, int lw) { return lx + lw - 60; }

    // ── Utilities ─────────────────────────────────────────────────────────────

    static String truncate(Font font, String s, int maxPx) {
        if (font.width(s) <= maxPx) return s;
        while (s.length() > 3 && font.width(s + "...") > maxPx)
            s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    static boolean inBounds(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x+w && my >= y && my < y+h;
    }

    private InventoryItemList() {}
}