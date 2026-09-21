package zcylas.totality.client.tooltip;

/**
 * Where a {@link TooltipDocument} is being presented. This pass only renders
 * {@link #HOVER_TOOLTIP}; {@link #INVENTORY_DETAIL} exists so a future, larger
 * {@code InventoryItemDetail} panel can consume the same contributors/sections with its own
 * layout, without the semantic layer needing to change.
 */
public enum TooltipTarget {
    HOVER_TOOLTIP,
    INVENTORY_DETAIL
}
