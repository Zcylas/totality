// api/rpg/ancestry/SpeciesCategory.java
package zcylas.totality.api.rpg.ancestry;

import java.util.List;

/**
 * UI-only grouping for species selection.
 * Category is a filter, not a core identity layer.
 * The player is a SpeciesData + OriginData, not a Category.
 */
public enum SpeciesCategory {

    HUMANOID("Humanoid", "◈",
            "The most common peoples of the known world. Diverse in culture " +
                    "and origin, humanoids have spread across every corner of existence."),

    GIANT_KIN("Giant-kin", "◉",
            "Races of exceptional size and physical power. Born of ancient " +
                    "giant blood, they tower above most other peoples."),

    CELESTIAL("Celestial", "✦",
            "Races touched by divine or heavenly power. Whether by birth or " +
                    "blessing, celestials carry a spark of something greater within them."),

    INFERNAL("Infernal", "⬡",
            "Races born of dark or demonic influence. Marked by their heritage, " +
                    "infernal races walk a fine line between their nature and their choices."),

    BEASTFOLK("Beastfolk", "❋",
            "Races with animal-like features and instincts. Deeply connected " +
                    "to the natural world, beastfolk bring unique perspectives to any group."),

    UNDEAD("Undead", "☽",
            "Races bound to death or undeath. Whether cursed or chosen, " +
                    "the undead exist between the living world and what lies beyond."),

    ALIEN("Alien", "◎",
            "Races originating from beyond the known world entirely. " +
                    "Ancient, evolved, and utterly unlike anything native to this universe, " +
                    "alien species bring biology and perspectives that defy conventional understanding."),

    OTHER("Other", "◇",
            "Races that defy easy classification. Ancient, mysterious, " +
                    "or simply unlike anything else in the known world.");

    private final String displayName;
    private final String icon;
    private final String description;

    SpeciesCategory(String displayName, String icon, String description) {
        this.displayName = displayName;
        this.icon        = icon;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getIcon()        { return icon; }
    public String getDescription() { return description; }

    /** Returns all categories that have at least one species registered. */
    public static List<SpeciesCategory> getUsedCategories() {
        return SpeciesRegistry.getUsedCategories();
    }
}