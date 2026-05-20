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
                    "Those trained in this skill deliver deadlier critical hits."),
    MINING("Mining", 10, Category.GATHERING,
        "Governs skill with pickaxes and the extraction of ores and stone. " +
                "Those trained in this skill can unearth materials others would miss."),
    ALCHEMY("Alchemy", 6, Category.MAGIC,
            "The art of combining magical ingredients to brew potions and poisons. " +
                    "Those trained in this skill brew more potent mixtures and discover effects others would miss."),
    RITUAL_ARTS("Ritual Arts", 8, Category.MAGIC,
        "The art of drawing chalk glyphs, preparing altars, and performing rituals. " +
                "Those trained in this skill channel greater power through their ceremonies.");


    // ── Category ──────────────────────────────────────────────────────────────

    public enum Category {
        COMBAT,   // warm red/orange
        MAGIC,    // blue/purple
        SURVIVAL, // green
        THIEF,
        GATHERING  // earthy green// dark purple/grey
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
            case THIEF    -> 0xFF886699;
            case GATHERING -> 0xFF558833; // earthy green, distinct from SURVIVAL// dark purple/grey
        };
    }
    public net.minecraft.world.item.ItemStack getIconItem() {
        return switch (this) {
            case ONE_HANDED  -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
            case MINING      -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
            case ALCHEMY     -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.POTION);
            case RITUAL_ARTS -> new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_EYE);
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