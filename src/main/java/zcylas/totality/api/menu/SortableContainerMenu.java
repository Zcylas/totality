package zcylas.totality.api.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;

/**
 * Implemented by any {@link net.minecraft.world.inventory.AbstractContainerMenu}
 * whose container slots should support sort / quick-stack / transfer actions.
 *
 * The generic {@link zcylas.totality.networking.menu.ContainerSortHandler}
 * checks for this interface on {@code player.containerMenu} — so any future
 * chest, bag, or storage screen just needs to implement this to get all four
 * buttons for free.
 *
 * Example:
 * <pre>
 *   public class MyChestMenu extends AbstractContainerMenu
 *           implements SortableContainerMenu {
 *
 *       &#64;Override public Container getSortableContainer() { return chestContainer; }
 *       &#64;Override public int getContainerSlotCount()     { return CHEST_SLOTS; }
 *       &#64;Override public void afterAction(ServerPlayer p) { saveTo(p.getInventory()); }
 *   }
 * </pre>
 */
public interface SortableContainerMenu {

    /** The container whose slots should be sorted / transferred. */
    Container getSortableContainer();

    /**
     * Number of slots that belong to the container (i.e. not the player inventory).
     * Used to distinguish container slots from player inventory slots in the menu's
     * full slot list.
     */
    int getContainerSlotCount();

    /**
     * Called after any sort or transfer action completes.
     * Use this to persist changes back to the item stack, block entity, etc.
     */
    void afterAction(ServerPlayer player);
}