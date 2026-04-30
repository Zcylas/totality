package zcylas.totality.api.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class ItemTransferTicker {

    /**
     * Call from a block entity's tick to push/pull items based on side config.
     * One item per face per tick, matching vanilla hopper speed.
     */
    public static void tick(ServerLevel level, BlockPos pos,
                            WorldlyContainer container,
                            SimpleSidedItemContainer itemSides) {
        for (Direction dir : Direction.values()) {
            ItemSideMode mode = itemSides.getSideMode(dir);
            if (mode == ItemSideMode.NONE) continue;

            BlockPos neighborPos = pos.relative(dir);
            Container neighbor = getNeighborContainer(level, neighborPos, dir.getOpposite());
            if (neighbor == null) continue;

            if (mode.allowsExtraction()) {
                push(container, dir, neighbor);
            }
            if (mode.allowsInsertion()) {
                pull(container, dir, neighbor);
            }
        }
    }

    /** Push from our output slots into the neighbor. */
    private static void push(WorldlyContainer from, Direction fromFace, Container to) {
        int[] slots = from.getSlotsForFace(fromFace);
        for (int slot : slots) {
            ItemStack stack = from.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!from.canTakeItemThroughFace(slot, stack, fromFace)) continue;

            int inserted = insertInto(to, stack, fromFace.getOpposite());
            if (inserted > 0) {
                stack.shrink(inserted);
                from.setChanged();
                return; // one transfer per tick per face
            }
        }
    }

    /** Pull from the neighbor into our input slots. */
    private static void pull(WorldlyContainer to, Direction toFace, Container from) {
        for (int fromSlot = 0; fromSlot < from.getContainerSize(); fromSlot++) {
            ItemStack stack = from.getItem(fromSlot);
            if (stack.isEmpty()) continue;
            if (from instanceof WorldlyContainer wc &&
                    !wc.canTakeItemThroughFace(fromSlot, stack, toFace.getOpposite())) continue;

            int[] slots = to.getSlotsForFace(toFace);
            for (int toSlot : slots) {
                if (!to.canPlaceItemThroughFace(toSlot, stack, toFace)) continue;

                ItemStack current = to.getItem(toSlot);
                if (!current.isEmpty() && !ItemStack.isSameItemSameComponents(current, stack)) continue;

                int space = to.getMaxStackSize(stack) - current.getCount();
                if (space <= 0) continue;

                int toMove = Math.min(1, Math.min(stack.getCount(), space));
                ItemStack moved = from.removeItem(fromSlot, toMove);
                if (!moved.isEmpty()) {
                    if (current.isEmpty()) {
                        to.setItem(toSlot, moved);
                    } else {
                        current.grow(moved.getCount());
                    }
                    to.setChanged();
                    from.setChanged();
                    return;
                }
            }
        }
    }

    /** Insert as much of stack as possible into the target container. Returns amount inserted. */
    private static int insertInto(Container target, ItemStack stack, Direction fromFace) {
        int inserted = 0;
        for (int i = 0; i < target.getContainerSize(); i++) {
            if (target instanceof WorldlyContainer wc && !wc.canPlaceItemThroughFace(i, stack, fromFace))
                continue;

            ItemStack current = target.getItem(i);
            if (current.isEmpty()) {
                int toMove = Math.min(1, stack.getCount());
                target.setItem(i, stack.copyWithCount(toMove));
                target.setChanged();
                inserted += toMove;
                return inserted;
            } else if (ItemStack.isSameItemSameComponents(current, stack)) {
                int space = target.getMaxStackSize(stack) - current.getCount();
                if (space > 0) {
                    int toMove = Math.min(1, Math.min(stack.getCount(), space));
                    current.grow(toMove);
                    target.setChanged();
                    inserted += toMove;
                    return inserted;
                }
            }
        }
        return inserted;
    }

    /** Get a Container from a neighboring block, respecting WorldlyContainerHolder. */
    private static Container getNeighborContainer(ServerLevel level, BlockPos pos, Direction face) {
        BlockState state = level.getBlockState(pos);
        BlockEntity be = level.getBlockEntity(pos);

        if (be instanceof Container container) return container;

        if (state.getBlock() instanceof WorldlyContainerHolder holder) {
            return holder.getContainer(state, level, pos);
        }

        return null;
    }

    private ItemTransferTicker() {}
}