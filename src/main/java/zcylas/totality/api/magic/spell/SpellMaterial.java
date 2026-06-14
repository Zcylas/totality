package zcylas.totality.api.magic.spell;

import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Describes the material component a spell requires and whether it can be
 * replaced by an Arcane Focus or Component Pouch.
 *
 * {@code requiredItems} lists the specific items the casting system searches
 * for in the player's inventory (or Component Pouch). Each item in the list
 * must be present — one of each is consumed on cast.
 *
 * If the list is empty but the spell has a MATERIAL component, any
 * Arcane Focus or Component Pouch satisfies the requirement automatically
 * with no item consumption.
 *
 * Per D&D 5e:
 *   - Components with NO gold cost → replaceable by focus/pouch
 *   - Components WITH a gold cost → must always be the actual item
 */
public record SpellMaterial(
        List<Item> requiredItems, // items that must be present (one of each consumed)
        int goldCost,             // 0 = replaceable; >0 = costly, non-replaceable
        String description        // tooltip text e.g. "a tiny ball of bat guano and sulphur"
) {
    /** Replaceable material with no specific items — satisfied by focus/pouch alone. */
    public static SpellMaterial replaceable(String description) {
        return new SpellMaterial(List.of(), 0, description);
    }

    /** Replaceable material tied to specific items. Focus/pouch may substitute. */
    public static SpellMaterial replaceableItems(List<Item> items, String description) {
        return new SpellMaterial(List.copyOf(items), 0, description);
    }

    /**
     * Costly material — NOT replaceable by focus or pouch.
     * The actual item must always be present and is always consumed.
     */
    public static SpellMaterial costly(Item item, int goldCost, String description) {
        return new SpellMaterial(List.of(item), goldCost, description);
    }

    public boolean isReplaceable() { return goldCost == 0; }
    public boolean isCostly()      { return goldCost > 0; }
}