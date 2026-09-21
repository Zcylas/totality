package zcylas.totality.api.core.rpgutils.rarity;

/**
 * Groups {@link ItemRarity} constants into families so the renderer and future comparison
 * logic can distinguish "how powerful" a rarity is from "which quality ladder it belongs to."
 * Standard/Religious/Industrial are independent parallel ladders — CRUDE is not "worse than
 * Common," it simply belongs to a different family. Future authored quality ladders for
 * unrelated systems (Food, Furniture, crafted products, other profession-specific quality
 * tiers) are expected to define their own dedicated rarity/quality enum rather than adding
 * more constants here — {@link ItemRarity} is scoped to combat/magic/industrial equipment.
 */
public enum RarityFamily {
    STANDARD,
    SPECIAL,
    RELIGIOUS,
    INDUSTRIAL
}
