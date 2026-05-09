package zcylas.totality.api.rpg.skills.core;

/**
 * All currently implemented skills in the Totality RPG system.
 *
 * XP required to level: skillImproveMult × currentLevel^1.95 (Skyrim formula)
 * Character XP gained on level up: new skill level
 */
public enum Skill {

    ONE_HANDED("One-Handed", 10, Category.COMBAT,
            "The art of combat using one-handed weapons such as swords, daggers, war axes and maces. " +
                    "Those trained in this skill deliver deadlier critical hits.");

    // ── Category ──────────────────────────────────────────────────────────────

    public enum Category {
        COMBAT,   // warm red/orange
        MAGIC,    // blue/purple
        SURVIVAL, // green
        THIEF     // dark purple/grey
    }

    private final String displayName;
    private final int skillImproveMult;
    private final Category category;
    private final String description;

    Skill(String displayName, int skillImproveMult, Category category, String description) {
        this.displayName = displayName;
        this.skillImproveMult = skillImproveMult;
        this.category = category;
        this.description = description;
    }

    public String getDisplayName()    { return displayName; }
    public int getSkillImproveMult()  { return skillImproveMult; }
    public Category getCategory()     { return category; }
    public String getDescription()    { return description; }

    /**
     * Returns the display color for this skill's name based on its category.
     */
    public int getCategoryColor() {
        return switch (category) {
            case COMBAT   -> 0xFFFF6633; // warm red/orange
            case MAGIC    -> 0xFF8866FF; // blue/purple
            case SURVIVAL -> 0xFF44BB44; // green
            case THIEF    -> 0xFF886699; // dark purple/grey
        };
    }

    /**
     * XP required to reach the next level from currentLevel.
     * Formula: skillImproveMult × currentLevel^1.95
     */
    public int getXpRequired(int currentLevel) {
        return (int)(skillImproveMult * Math.pow(currentLevel, 1.95));
    }
}