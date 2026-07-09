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
    STR("Strength",     "Governs melee damage and carry weight.",                     "⚔", 0xFFCC4444),
    DEX("Dexterity",    "Governs attack speed, dodge chance and ranged accuracy.",     "◎", 0xFFCC8833),
    CON("Constitution", "Governs maximum health points.",                             "♥", 0xFF44AACC),
    END("Endurance",    "Governs maximum stamina and poison resistance.",             "⚡", 0xFF44CC88),
    INT("Intelligence", "Governs maximum mana and spell power.",                      "✦", 0xFFAA44CC),
    WIS("Wisdom",       "Governs skill XP gain rate and magic resistance.",           "◈", 0xFF4488CC),
    CHA("Charisma",     "Governs NPC interactions and merchant prices.",              "★", 0xFFCCAA33),
    FTH("Faith",        "Governs religion favour gain and divine intervention.",      "✝", 0xFFCCCCCC);

    private final String displayName;
    private final String description;
    private final String icon;
    private final int iconColor;

    AbilityScore(String displayName, String description, String icon, int iconColor) {
        this.displayName = displayName;
        this.description = description;
        this.icon = icon;
        this.iconColor = iconColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /** Unicode glyph used to represent this score in UI (character screen, dice bonus cards, etc.). */
    public String getIcon() {
        return icon;
    }

    public int getIconColor() {
        return iconColor;
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