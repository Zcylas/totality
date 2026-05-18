package zcylas.totality.screen.inventory;

import java.util.Comparator;
import java.util.List;

/**
 * Manages sort state for the inventory item list.
 *
 * To add a new sort column (e.g. WEIGHT, DAMAGE, VALUE):
 *   1. Add a value to SortColumn.
 *   2. Add a case in buildComparator() returning the Comparator<ItemEntry>.
 *   3. Add the column header rendering and hit-test in InventoryItemList.
 * Everything else (ascending/descending cycling, arrow display) is automatic.
 */
public final class InventorySortManager {

    public enum SortColumn {
        DEFAULT,  // insertion order — no active sort
        NAME,
        QTY;
        // Future: TYPE, WEIGHT, DAMAGE, VALUE …
    }

    private SortColumn column    = SortColumn.NAME;
    private boolean    ascending = true;

    public SortColumn getColumn()    { return column; }
    public boolean    isAscending()  { return ascending; }

    /**
     * Called when a column header is clicked.
     * Same column: flip direction. Already descending → reset to DEFAULT.
     * Different column: switch to it ascending.
     */
    public void cycleColumn(SortColumn clicked) {
        if (column == clicked) {
            if (ascending) {
                ascending = false;
            } else {
                column    = SortColumn.DEFAULT;
                ascending = true;
            }
        } else {
            column    = clicked;
            ascending = true;
        }
    }

    /** Returns true if a full rebuild (not just re-sort) is needed after cycling to DEFAULT. */
    public boolean isDefault() {
        return column == SortColumn.DEFAULT;
    }

    /** Apply the current sort to the list in place. No-op if DEFAULT. */
    public void apply(List<ItemEntry> items) {
        if (column == SortColumn.DEFAULT) return;
        Comparator<ItemEntry> cmp = buildComparator(column);
        if (cmp == null) return;
        if (!ascending) cmp = cmp.reversed();
        items.sort(cmp);
    }

    /** The arrow string to display next to an active column header. Empty if not active. */
    public String arrowFor(SortColumn col) {
        if (column != col) return "";
        return ascending ? " ↑" : " ↓";
    }

    private static Comparator<ItemEntry> buildComparator(SortColumn col) {
        return switch (col) {
            case NAME -> Comparator.comparing(e -> e.stack.getHoverName().getString().toLowerCase());
            case QTY  -> Comparator.comparingInt(e -> e.stack.getCount());
            default   -> null;
        };
    }
}