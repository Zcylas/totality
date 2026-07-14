package zcylas.totality.api.economy.value;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.Optional;

/**
 * A single central item-value definition. {@code referenceStack} carries both the item
 * identity and, via {@code ItemStack.CODEC}'s component patch, an optional PARTIAL
 * required-component predicate — reuses the exact same codec {@code ShopEntry} already
 * uses for the identical reason (enchanted books, potion variants, etc. need full
 * component/NBT resolution, not just a bare item ID).
 *
 * <p>A rule whose reference stack has an EMPTY component patch (no {@code "components"}
 * authored in JSON) is a plain item-ID fallback. A rule with a NON-EMPTY patch only
 * requires the components it explicitly authors to match — see {@link #matches(ItemStack)}.
 *
 * <p>{@code referenceStack.getComponentsPatch()} (not {@code getComponents()}) is what
 * makes this correct: {@code ItemStack.CODEC} serializes/deserializes exactly the
 * explicit component PATCH relative to the item's own defaults, not a fully-merged
 * component map — decoding a bare {@code {"id": "minecraft:bread"}} produces an empty
 * patch even though the real Bread item has non-trivial default components (food data,
 * max stack size, etc.). Matching against {@code getComponents()} instead would silently
 * pull in every one of those materialized defaults as required-match components, which is
 * exactly the failure mode the design document's implementation caution warns against.
 */
public record ItemValueRule(ItemStack referenceStack, long baseValue) {

    public static final Codec<ItemValueRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("item").forGetter(ItemValueRule::referenceStack),
            Codec.LONG.fieldOf("base_value").forGetter(ItemValueRule::baseValue)
    ).apply(instance, ItemValueRule::new));

    /** True if this rule authors at least one explicit component requirement — false for a
     *  plain item-ID fallback rule. */
    public boolean isComponentAware() {
        return !referenceStack.getComponentsPatch().isEmpty();
    }

    /** Number of explicitly authored component requirements — 0 for a plain fallback rule.
     *  Used to pick the most-specific matching rule when several apply. */
    public int specificity() {
        return referenceStack.getComponentsPatch().size();
    }

    /**
     * True if {@code real} is the same item and (for component-aware rules) satisfies every
     * explicitly authored component requirement. Components this rule does not declare are
     * never inspected on {@code real}, and stack count is never compared.
     */
    public boolean matches(ItemStack real) {
        if (real.getItem() != referenceStack.getItem()) return false;
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : referenceStack.getComponentsPatch().entrySet()) {
            if (!componentMatches(real, entry.getKey(), entry.getValue())) return false;
        }
        return true;
    }

    private static boolean componentMatches(ItemStack real, DataComponentType<?> type, Optional<?> required) {
        Object actual = real.get(type);
        if (required.isEmpty()) return actual == null;
        return required.get().equals(actual);
    }
}
