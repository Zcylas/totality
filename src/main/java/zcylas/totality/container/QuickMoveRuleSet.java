package zcylas.totality.container;

import com.google.common.base.Predicates;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A declarative builder for {@link AbstractContainerMenu#quickMoveStack(Player, int)}.
 *
 * <p>In MC 26.1.2, {@code AbstractContainerMenu.moveItemStackTo} is package-private,
 * so the original puzzles-lib approach of passing {@code this::moveItemStackTo} as a
 * functional interface doesn't compile from outside the {@code net.minecraft} package.
 * This version inlines the method's logic directly so no reference to it is needed.</p>
 *
 * <p>Ported from puzzles-lib (Fuzs), adapted for MC 26.1.2 access constraints.</p>
 *
 * <p>Usage — inside any menu class:
 * <pre>{@code
 * private QuickMoveRuleSet rules;
 *
 * public MyMenu(int id, Inventory playerInv) {
 *     // ... add slots ...
 *     rules = QuickMoveRuleSet.of(this)
 *             .addContainerSlotRule(SLOT_INPUT)
 *             .addContainerSlotRule(SLOT_FUEL)
 *             .addContainerSlotRule(SLOT_OUTPUT)
 *             .addInventoryRules();
 * }
 *
 * \@Override
 * public ItemStack quickMoveStack(Player player, int index) {
 *     return rules.quickMoveStack(player, index);
 * }
 * }</pre>
 */
public final class QuickMoveRuleSet {

    private static final Predicate<Slot> IS_INVENTORY =
            slot -> slot.container instanceof Inventory;
    private static final Predicate<Slot> IS_INVENTORY_ITEMS =
            slot -> IS_INVENTORY.test(slot) && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
    private static final Predicate<Slot> IS_INVENTORY_ARMOR =
            slot -> IS_INVENTORY.test(slot)
                    && slot.getContainerSlot() >= Inventory.INVENTORY_SIZE
                    && slot.getContainerSlot() < Inventory.SLOT_OFFHAND;
    private static final Predicate<Slot> IS_HOTBAR =
            slot -> IS_INVENTORY_ITEMS.test(slot) && Inventory.isHotbarSlot(slot.getContainerSlot());
    private static final Predicate<Slot> IS_NOT_HOTBAR =
            slot -> IS_INVENTORY_ITEMS.test(slot) && !IS_HOTBAR.test(slot);

    private final List<Rule> rules = new ArrayList<>();
    private final List<Slot> slots;
    private final Mode mode;

    private QuickMoveRuleSet(AbstractContainerMenu menu, Mode mode) {
        this.slots = menu.slots;
        this.mode  = mode;
    }

    // -----------------------------------------------------------------------
    // Factories
    // -----------------------------------------------------------------------

    public static QuickMoveRuleSet of(AbstractContainerMenu menu) {
        return of(menu, Mode.RELAXED);
    }

    public static QuickMoveRuleSet of(AbstractContainerMenu menu, Mode mode) {
        return new QuickMoveRuleSet(menu, mode);
    }

    // -----------------------------------------------------------------------
    // Rule registration
    // -----------------------------------------------------------------------

    public QuickMoveRuleSet addContainerSlotRule(int index) {
        return addContainerSlotRule(index, Predicates.alwaysTrue());
    }

    public QuickMoveRuleSet addContainerSlotRule(int... indices) {
        for (int i : indices) addContainerSlotRule(i);
        return this;
    }

    public QuickMoveRuleSet addContainerSlotRule(int index, Predicate<Slot> filter) {
        return addContainerSlotRule(index, false, filter);
    }

    public QuickMoveRuleSet addContainerSlotRule(int index, boolean reverseDirection, Predicate<Slot> filter) {
        return addRule(new Rule(RuleType.CONTAINER_SLOT, index, index + 1, reverseDirection,
                slot -> IS_INVENTORY.test(slot) && slots.get(index).mayPlace(slot.getItem()) && filter.test(slot)));
    }

    public QuickMoveRuleSet addContainerRule(Container container) {
        return addContainerRule(
                inclusiveStart(s -> s.container == container),
                exclusiveEnd(  s -> s.container == container));
    }

    public QuickMoveRuleSet addContainerRule(int startIndex, int endIndex) {
        return addRule(new Rule(RuleType.CONTAINER, startIndex, endIndex, false,
                slot -> slot.index >= inclusiveStart(IS_INVENTORY_ITEMS)
                        && slot.index < exclusiveEnd(IS_INVENTORY_ITEMS)));
    }

    public QuickMoveRuleSet addInventoryRules() {
        return addInventoryRules(true);
    }

    public QuickMoveRuleSet addInventoryRules(boolean reverseDirection) {
        addInventoryRule(RuleType.INVENTORY_ARMOR, reverseDirection, IS_INVENTORY_ARMOR);
        return addInventoryRule(RuleType.INVENTORY_ITEMS, reverseDirection, IS_INVENTORY_ITEMS);
    }

    private QuickMoveRuleSet addInventoryRule(RuleType type, boolean reverse, Predicate<Slot> filter) {
        return addRule(new Rule(type, inclusiveStart(filter), exclusiveEnd(filter),
                reverse, IS_INVENTORY.negate()));
    }

    public QuickMoveRuleSet addInventoryCompartmentRules() {
        addCompartmentPair(RuleType.TO_ARMOR,   RuleType.FROM_ARMOR,   IS_INVENTORY_ARMOR, IS_INVENTORY_ITEMS);
        return addCompartmentPair(RuleType.TO_HOTBAR, RuleType.FROM_HOTBAR, IS_HOTBAR, IS_NOT_HOTBAR);
    }

    private QuickMoveRuleSet addCompartmentPair(RuleType to, RuleType from,
                                                 Predicate<Slot> toFilter, Predicate<Slot> fromFilter) {
        addRule(new Rule(to,
                inclusiveStart(toFilter), exclusiveEnd(toFilter), false,
                slot -> slot.index >= inclusiveStart(fromFilter) && slot.index < exclusiveEnd(fromFilter)));
        return addRule(new Rule(from,
                inclusiveStart(fromFilter), exclusiveEnd(fromFilter), false,
                slot -> slot.index >= inclusiveStart(toFilter) && slot.index < exclusiveEnd(toFilter)));
    }

    private QuickMoveRuleSet addRule(Rule rule) {
        rules.add(rule);
        return this;
    }

    // -----------------------------------------------------------------------
    // Execution
    // -----------------------------------------------------------------------

    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot.hasItem()) {
            ItemStack inSlot = slot.getItem();
            copy = inSlot.copy();

            for (Rule rule : rules) {
                if (rule.isValid() && rule.filter().test(slot)) {
                    if (!moveItemStackTo(inSlot, rule.startIndex(), rule.endIndex(), rule.reverse())) {
                        if (mode == Mode.STRICT) return ItemStack.EMPTY;
                    } else {
                        break;
                    }
                }
            }

            if (inSlot.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (inSlot.getCount() == copy.getCount()) return ItemStack.EMPTY;
            slot.onTake(player, inSlot);
        }

        return mode == Mode.RELAXED ? ItemStack.EMPTY : copy;
    }

    // -----------------------------------------------------------------------
    // Inlined moveItemStackTo
    // (AbstractContainerMenu.moveItemStackTo is package-private in MC 26.1.2,
    //  so we reproduce the logic here rather than calling through a functional
    //  interface that the compiler won't allow from outside net.minecraft.)
    // -----------------------------------------------------------------------

    private boolean moveItemStackTo(ItemStack stack, int startIndex, int endIndex, boolean reverse) {
        boolean moved = false;
        int i = reverse ? endIndex - 1 : startIndex;

        // Pass 1: try to stack onto existing matching slots
        if (stack.isStackable()) {
            while (!stack.isEmpty() && (reverse ? i >= startIndex : i < endIndex)) {
                Slot slot = slots.get(i);
                ItemStack inSlot = slot.getItem();

                if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(stack, inSlot)) {
                    int combined = inSlot.getCount() + stack.getCount();
                    int max = slot.getMaxStackSize(inSlot);
                    if (combined <= max) {
                        stack.setCount(0);
                        inSlot.setCount(combined);
                        slot.setChanged();
                        moved = true;
                    } else if (inSlot.getCount() < max) {
                        stack.shrink(max - inSlot.getCount());
                        inSlot.setCount(max);
                        slot.setChanged();
                        moved = true;
                    }
                }
                i += reverse ? -1 : 1;
            }
        }

        // Pass 2: try to place into empty slots
        if (!stack.isEmpty()) {
            i = reverse ? endIndex - 1 : startIndex;
            while (reverse ? i >= startIndex : i < endIndex) {
                Slot slot = slots.get(i);
                if (slot.getItem().isEmpty() && slot.mayPlace(stack)) {
                    int max = slot.getMaxStackSize(stack);
                    slot.setByPlayer(stack.split(Math.min(stack.getCount(), max)));
                    slot.setChanged();
                    moved = true;
                    break;
                }
                i += reverse ? -1 : 1;
            }
        }

        return moved;
    }

    // -----------------------------------------------------------------------
    // Index helpers
    // -----------------------------------------------------------------------

    private int inclusiveStart(Predicate<Slot> p) {
        for (int i = 0; i < slots.size(); i++) if (p.test(slots.get(i))) return i;
        return -1;
    }

    private int exclusiveEnd(Predicate<Slot> p) {
        for (int i = slots.size() - 1; i >= 0; i--) if (p.test(slots.get(i))) return i + 1;
        return -1;
    }

    // -----------------------------------------------------------------------
    // Types
    // -----------------------------------------------------------------------

    /**
     * Controls what happens after a rule applies:
     * <ul>
     *   <li>{@link #STRICT}  — return EMPTY immediately if the matched rule fails to move anything.</li>
     *   <li>{@link #RELAXED} — always return EMPTY; skip full slots and try next attempt (most common).</li>
     *   <li>{@link #LENIENT} — items can be split across multiple rules in one pass.</li>
     * </ul>
     */
    public enum Mode { STRICT, RELAXED, LENIENT }

    private enum RuleType {
        CONTAINER_SLOT, CONTAINER,
        INVENTORY_ITEMS, INVENTORY_ARMOR,
        TO_HOTBAR, FROM_HOTBAR, TO_ARMOR, FROM_ARMOR
    }

    private record Rule(RuleType type, int startIndex, int endIndex,
                        boolean reverse, Predicate<Slot> filter) {
        boolean isValid() { return startIndex != -1 && endIndex != -1; }
    }
}
