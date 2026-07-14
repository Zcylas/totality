package zcylas.totality.api.economy.value;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Pure, data-load-independent conflict detection between two {@link ItemValueRule}s already
 * known to reference the same {@code Item}. Isolated from {@link ItemValueRegistry} so the
 * matching/ambiguity logic can be exercised directly without needing a full datapack load
 * (see {@code ItemValueVerification}).
 */
final class ItemValueRuleConflicts {

    private ItemValueRuleConflicts() {}

    /** Two rules for the same item are duplicates if they author the exact same component
     *  patch — including two plain fallbacks (both empty patches). */
    static boolean isDuplicate(ItemValueRule a, ItemValueRule b) {
        return a.referenceStack().getComponentsPatch().equals(b.referenceStack().getComponentsPatch());
    }

    /**
     * Two component-aware rules for the same item are ambiguous if they are equally specific
     * (same number of declared component keys) and NOT duplicates, and there is no key they
     * both declare with conflicting required values — if every shared key agrees (or they
     * share none), a single real stack could satisfy every declared component from BOTH rules
     * simultaneously, and neither rule is more specific than the other. This is the genuine
     * "equally-specific, no clear winner" conflict.
     *
     * <p>If they DO share a key with different required values, no stack could ever satisfy
     * both at once, so they never actually compete — this is exactly the Water Bottle vs.
     * Potion of Healing case (both declare only {@code minecraft:potion_contents}, with
     * different required values) and must NOT be flagged as ambiguous.
     */
    static boolean isAmbiguous(ItemValueRule a, ItemValueRule b) {
        DataComponentPatch patchA = a.referenceStack().getComponentsPatch();
        DataComponentPatch patchB = b.referenceStack().getComponentsPatch();

        if (patchA.size() != patchB.size()) return false;
        if (isDuplicate(a, b)) return false;
        return couldBothMatchSameStack(patchA, patchB);
    }

    private static boolean couldBothMatchSameStack(DataComponentPatch a, DataComponentPatch b) {
        Map<DataComponentType<?>, Optional<?>> declaredByA = new HashMap<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : a.entrySet()) {
            declaredByA.put(entry.getKey(), entry.getValue());
        }

        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : b.entrySet()) {
            Optional<?> requiredByA = declaredByA.get(entry.getKey());
            if (requiredByA != null && !requiredByA.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }
}
