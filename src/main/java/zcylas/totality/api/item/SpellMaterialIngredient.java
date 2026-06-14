package zcylas.totality.api.item;

/**
 * Marks an item as a valid spell material component.
 *
 * Items implementing this interface:
 *   - Can be stored in the Component Pouch
 *   - Show "Spell Component" in their tooltip
 *   - Are searchable by the casting system when checking material requirements
 *
 * Whether a specific item satisfies a spell's material requirement is declared
 * on the spell side via {@link zcylas.totality.api.magic.spell.SpellMaterial}.
 *
 * Usage:
 * <pre>
 *   public class BatGuanoItem extends Item implements SpellMaterialIngredient { }
 * </pre>
 */
public interface SpellMaterialIngredient {

    /**
     * Whether this material can be substituted by an Arcane Focus or Component Pouch.
     * Returns false for costly components (those with a specific gold cost).
     * Default: true — most materials are replaceable.
     */
    default boolean canBeSubstituted() { return true; }

    /**
     * Short display name shown in Component Pouch UI and spell tooltip.
     * Defaults to the item's own translation key display.
     */
    default String getMaterialName() { return ""; }
}