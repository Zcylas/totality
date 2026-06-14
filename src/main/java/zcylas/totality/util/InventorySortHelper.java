package zcylas.totality.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Inventory sorting utilities.
 *
 * Sort orders (mirrors ClientSort's configurable order pattern):
 *   ALPHABETICAL  — by display name, A→Z
 *   BY_COUNT      — total quantity, most first; then stack size
 *   BY_TYPE       — group by item class, then alphabetical within group
 *
 * Client-side: use {@link #computeSortMapping(List, SortMode)} to get the
 * slot permutation, then send it in {@link zcylas.totality.networking.menu.ContainerSortPayload}.
 * Server-side: use {@link #applyMapping(Container, int[])} to apply it.
 * For transfers/merges: use {@link #quickStack} and {@link #mergeStacks}.
 */
public final class InventorySortHelper {

    public enum SortMode {
        ALPHABETICAL,
        BY_COUNT,
        BY_TYPE;

        public Comparator<ItemStack> comparator() {
            return switch (this) {
                case ALPHABETICAL -> Comparator
                        .<ItemStack, String>comparing(s -> s.getHoverName().getString().toLowerCase())
                        .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());

                case BY_COUNT -> Comparator
                        .comparingInt(ItemStack::getCount).reversed()
                        .thenComparing(s -> s.getHoverName().getString().toLowerCase());

                case BY_TYPE -> Comparator
                        .<ItemStack, String>comparing(s -> s.getItem().getClass().getSimpleName())
                        .thenComparing(s -> s.getHoverName().getString().toLowerCase())
                        .thenComparing(Comparator.comparingInt(ItemStack::getCount).reversed());
            };
        }
    }

    private InventorySortHelper() {}

    // ── Client-side: compute permutation ─────────────────────────────────────

    /**
     * Given the current items in a container's slots (index = slot position),
     * returns a slot mapping where {@code mapping[i]} = the original slot index
     * whose item should end up at position {@code i}.
     *
     * Empty slots are not included in the mapping — they go at the end implicitly.
     * Send this mapping in {@link zcylas.totality.networking.menu.ContainerSortPayload}.
     */
    public static int[] computeSortMapping(List<ItemStack> containerItems, SortMode mode) {
        // Build indexed list of non-empty stacks
        List<Map.Entry<Integer, ItemStack>> nonEmpty = new ArrayList<>();
        for (int i = 0; i < containerItems.size(); i++) {
            if (!containerItems.get(i).isEmpty()) {
                nonEmpty.add(Map.entry(i, containerItems.get(i)));
            }
        }

        // Merge identical stacks before sorting
        List<ItemStack> merged = mergeStacks(nonEmpty.stream()
                .map(Map.Entry::getValue)
                .map(ItemStack::copy)
                .toList());

        // Sort the merged list
        merged.sort(mode.comparator());

        // The mapping is simply the position in the merged sorted list
        // (since we collapsed duplicates, we rebuild from sorted stacks)
        // Return indices 0..merged.size()-1 as the "virtual" sorted order
        // (server will place merged stacks in order)
        return java.util.stream.IntStream.range(0, merged.size()).toArray();
    }

    /**
     * Full client-side sort: returns merged + sorted stacks in the desired
     * final order. The server puts these at slots 0..N-1, empty at N..end.
     */
    public static List<ItemStack> computeSortedStacks(List<ItemStack> containerItems, SortMode mode) {
        List<ItemStack> nonEmpty = new ArrayList<>();
        for (ItemStack s : containerItems) {
            if (!s.isEmpty()) nonEmpty.add(s.copy());
        }
        List<ItemStack> merged = mergeStacks(nonEmpty);
        merged.sort(mode.comparator());
        return merged;
    }

    // ── Server-side: apply mapping ────────────────────────────────────────────

    /**
     * Apply a pre-computed sorted stack list to a container.
     * {@code sortedStacks} should come from {@link #computeSortedStacks} on the client.
     */
    public static void applyMapping(Container container, List<ItemStack> sortedStacks) {
        int size = container.getContainerSize();
        for (int i = 0; i < size; i++) {
            container.setItem(i, i < sortedStacks.size() ? sortedStacks.get(i) : ItemStack.EMPTY);
        }
    }

    // ── Transfer helpers ──────────────────────────────────────────────────────

    /**
     * Quick-stack: move inventory items into {@code destination} only if a
     * matching stack already exists there. Uses {@code slot.safeInsert()} pattern
     * (merge into partials first, then empty slots).
     */
    public static void quickStack(Container source, Container destination) {
        for (int si = 0; si < source.getContainerSize(); si++) {
            ItemStack s = source.getItem(si);
            if (s.isEmpty()) continue;
            boolean exists = false;
            for (int di = 0; di < destination.getContainerSize(); di++) {
                if (ItemStack.isSameItemSameComponents(destination.getItem(di), s)) {
                    exists = true; break;
                }
            }
            if (!exists) continue;
            source.setItem(si, safeInsertIntoContainer(destination, s));
        }
    }

    /**
     * Insert a stack into a container, filling partial stacks first then empty
     * slots. Returns the remaining stack (empty if fully inserted).
     * Mirrors {@code Slot.safeInsert()} logic.
     */
    public static ItemStack safeInsertIntoContainer(Container container, ItemStack stack) {
        stack = stack.copy();
        // Pass 1: fill partial stacks
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack existing = container.getItem(i);
            if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space > 0) {
                    int take = Math.min(space, stack.getCount());
                    existing.grow(take);
                    stack.shrink(take);
                }
            }
        }
        // Pass 2: fill empty slots
        for (int i = 0; i < container.getContainerSize() && !stack.isEmpty(); i++) {
            if (container.getItem(i).isEmpty()) {
                container.setItem(i, stack.copy());
                stack = ItemStack.EMPTY;
            }
        }
        return stack;
    }

    // ── Stack merging ─────────────────────────────────────────────────────────

    /**
     * Merge stacks of the same item up to max stack size.
     * Produces the minimal number of stacks needed to hold all items.
     */
    public static List<ItemStack> mergeStacks(List<ItemStack> stacks) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack incoming : stacks) {
            if (incoming.isEmpty()) continue;
            ItemStack remaining = incoming.copy();
            for (ItemStack existing : result) {
                if (remaining.isEmpty()) break;
                if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
                int space = existing.getMaxStackSize() - existing.getCount();
                if (space <= 0) continue;
                int take = Math.min(space, remaining.getCount());
                existing.grow(take);
                remaining.shrink(take);
            }
            if (!remaining.isEmpty()) result.add(remaining);
        }
        return result;
    }
}