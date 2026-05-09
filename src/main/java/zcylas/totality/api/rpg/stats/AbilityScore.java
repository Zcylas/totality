package zcylas.totality.api.rpg.stats;

/**
 * The eight ability scores of the Totality RPG system.
 *
 * STR — Strength:     melee damage, carry weight
 * DEX — Dexterity:    attack speed, dodge chance, ranged accuracy
 * CON — Constitution: max HP scaling
 * END — Endurance:    max Stamina scaling, poison resistance
 * INT — Intelligence: max Mana scaling, spell power
 * WIS — Wisdom:       skill XP gain rate, magic resistance
 * CHA — Charisma:     NPC interactions, merchant prices
 * FTH — Faith:        religion favour gain, divine intervention power
 */
public enum AbilityScore {
    STR("Strength",     "Governs melee damage and carry weight."),
    DEX("Dexterity",    "Governs attack speed, dodge chance and ranged accuracy."),
    CON("Constitution", "Governs maximum health points."),
    END("Endurance",    "Governs maximum stamina and poison resistance."),
    INT("Intelligence", "Governs maximum mana and spell power."),
    WIS("Wisdom",       "Governs skill XP gain rate and magic resistance."),
    CHA("Charisma",     "Governs NPC interactions and merchant prices."),
    FTH("Faith",        "Governs religion favour gain and divine intervention.");

    private final String displayName;
    private final String description;

    AbilityScore(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Calculates the DnD-style modifier for a given score value.
     * modifier = floor((score - 10) / 2)
     *
     * Examples:
     *  10 → 0
     *  12 → +1
     *   8 → -1
     *   4 → -3
     *  20 → +5
     */
    public static int getModifier(int score) {
        return Math.floorDiv(score - 10, 2);
    }
}