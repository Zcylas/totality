package zcylas.totality.api.rpg.skills.core;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import zcylas.totality.init.items.BasicWeaponItems;

/**
 * All currently implemented skills in the Totality RPG system.
 *
 * XP required to level: skillImproveMult × currentLevel^1.95 (Skyrim formula)
 * Character XP gained on level up: new skill level
 */
public enum Skill {

    // ── Combat ────────────────────────────────────────────────────────────────
    ONE_HANDED("One-Handed", 10, Category.COMBAT,
            "The art of combat using one-handed weapons such as swords, daggers, war axes and maces. " +
                    "Those trained in this skill deliver deadlier critical hits."),
    TWO_HANDED("Two-Handed", 12, Category.COMBAT,
            "The art of combat using two-handed weapons such as greatswords, battleaxes and warhammers. " +
                    "Those trained in this skill strike with greater force and reach."),
    THROWING("Throwing", 8, Category.COMBAT,
            "The art of combat using thrown weapons such as shurikens and throwing knives. " +
                    "Those trained in this skill throw with greater accuracy and force."),
    ARCHERY("Archery", 10, Category.COMBAT,
            "The art of combat using bows and crossbows. " +
                    "Those trained in this skill loose faster and more powerful shots."),
    BLOCK("Block", 8, Category.COMBAT,
            "The art of deflecting attacks with a shield or weapon. " +
                    "Those trained in this skill absorb more damage and can perform perfect parries."),
    UNARMED("Unarmed", 8, Category.COMBAT,
            "The art of combat without weapons. " +
                    "Those trained in this skill strike harder and can stagger enemies with their blows."),
    HEAVY_ARMOR("Heavy Armor", 10, Category.COMBAT,
            "The art of wearing heavy armor. " +
                    "Those trained in this skill reduce the movement penalty and increase protection."),
    LIGHT_ARMOR("Light Armor", 8, Category.COMBAT,
            "The art of wearing light armor. " +
                    "Those trained in this skill move more freely and evade attacks more easily."),

    // ── Movement ──────────────────────────────────────────────────────────────
    ATHLETICS("Athletics", 6, Category.MOVEMENT,
            "The mastery of running, swimming and endurance. " +
                    "Those trained in this skill move faster and tire less easily."),
    ACROBATICS("Acrobatics", 6, Category.MOVEMENT,
            "The mastery of jumping, rolling and falling. " +
                    "Those trained in this skill jump higher and take less fall damage."),
    EVASION("Evasion", 8, Category.MOVEMENT,
            "The art of avoiding attacks through quick movement and reflexes. " +
                    "Those trained in this skill dodge more effectively and recover faster."),

    // ── Magic ─────────────────────────────────────────────────────────────────
    ALCHEMY("Alchemy", 6, Category.MAGIC,
            "The art of combining magical ingredients to brew potions and poisons. " +
                    "Those trained in this skill brew more potent mixtures and discover effects others would miss."),
    RITUAL_ARTS("Ritual Arts", 8, Category.MAGIC,
            "The art of drawing chalk glyphs, preparing altars, and performing rituals. " +
                    "Those trained in this skill channel greater power through their ceremonies."),
    DESTRUCTION("Destruction", 10, Category.MAGIC,
            "The mastery of spells that damage and destroy. " +
                    "Those trained in this skill cast more powerful and efficient destruction spells."),
    RESTORATION("Restoration", 8, Category.MAGIC,
            "The mastery of spells that heal and protect. " +
                    "Those trained in this skill cast more powerful healing and ward spells."),
    CONJURATION("Conjuration", 10, Category.MAGIC,
            "The mastery of summoning creatures and binding souls. " +
                    "Those trained in this skill summon stronger entities for longer durations."),
    ILLUSION("Illusion", 10, Category.MAGIC,
            "The mastery of spells that alter the mind. " +
                    "Those trained in this skill affect more powerful enemies."),
    ALTERATION("Alteration", 8, Category.MAGIC,
            "The mastery of spells that alter the physical world. " +
                    "Those trained in this skill maintain alterations longer and at greater magnitude."),
    ENCHANTING("Enchanting", 10, Category.MAGIC,
            "The art of imbuing items with magical properties. " +
                    "Those trained in this skill create more powerful and lasting enchantments."),
    NECROMANCY("Necromancy", 10, Category.MAGIC,
            "The dark art of raising and commanding the undead. " +
                    "Those trained in this skill raise more powerful undead and maintain control for longer."),

    // ── Gathering ─────────────────────────────────────────────────────────────
    MINING("Mining", 10, Category.GATHERING,
            "Governs skill with pickaxes and the extraction of ores and stone. " +
                    "Those trained in this skill can unearth materials others would miss."),
    WOODCUTTING("Woodcutting", 8, Category.GATHERING,
            "The art of felling trees and processing wood. " +
                    "Those trained in this skill chop faster and yield more from each tree."),
    FARMING("Farming", 6, Category.GATHERING,
            "The art of cultivating crops and tending to the land. " +
                    "Those trained in this skill grow more bountiful harvests."),
    FISHING("Fishing", 6, Category.GATHERING,
            "The art of catching fish and aquatic creatures. " +
                    "Those trained in this skill catch more valuable and rare fish."),

    // ── Survival ──────────────────────────────────────────────────────────────
    HUNTING("Hunting", 8, Category.SURVIVAL,
            "The art of tracking, hunting and harvesting animals. " +
                    "Those trained in this skill can field dress and skin creatures for better yields."),
    HERBALISM("Herbalism", 6, Category.SURVIVAL,
            "The art of identifying and harvesting plants and fungi. " +
                    "Those trained in this skill find rarer ingredients and harvest more from each plant."),
    NAVIGATION("Navigation", 6, Category.SURVIVAL,
            "The art of reading the land, stars and maps to find your way. " +
                    "Those trained in this skill can locate structures, resources and points of interest more easily."),
    TAMING("Taming", 6, Category.SURVIVAL,
            "The art of bonding with and training animals and creatures. " +
                    "Those trained in this skill tame more powerful creatures and strengthen their companions."),
    PERCEPTION("Perception", 6, Category.SURVIVAL,
            "The art of awareness and observation of the surrounding world. " +
                    "Those trained in this skill passively detect hidden objects, traps and creatures nearby."),

    // ── Crafting ──────────────────────────────────────────────────────────────
    SMITHING("Smithing", 10, Category.CRAFTING,
            "The art of forging and improving weapons and armor. " +
                    "Those trained in this skill craft higher quality equipment and unlock advanced recipes."),
    COOKING("Cooking", 6, Category.CRAFTING,
            "The art of preparing food and drink. " +
                    "Those trained in this skill create more nourishing meals with additional effects."),
    TAILORING("Tailoring", 6, Category.CRAFTING,
            "The art of crafting and improving cloth armor and accessories. " +
                    "Those trained in this skill create lighter and more protective garments."),
    CARPENTRY("Carpentry", 6, Category.CRAFTING,
            "The art of crafting with wood. " +
                    "Those trained in this skill build sturdier and more complex wooden structures and items."),

    // ── Thief ─────────────────────────────────────────────────────────────────
    SNEAK("Sneak", 8, Category.THIEF,
            "The art of moving unseen and unheard. " +
                    "Those trained in this skill are harder to detect and deal more damage from the shadows."),
    LOCKPICKING("Lockpicking", 8, Category.THIEF,
            "The art of opening locks without a key. " +
                    "Those trained in this skill can crack more complex locks with greater ease."),
    PICKPOCKET("Pickpocket", 8, Category.THIEF,
            "The art of stealing from others without being noticed. " +
                    "Those trained in this skill can lift heavier items with less chance of detection."),
    INVESTIGATION("Investigation", 6, Category.THIEF,
            "The art of actively searching and examining the environment for clues and hidden things. " +
                    "Those trained in this skill uncover secrets, traps and valuables others would overlook."),

    // ── Speech ────────────────────────────────────────────────────────────────
    PERSUASION("Persuasion", 6, Category.SPEECH,
            "The art of convincing others through charm and reason. " +
                    "Those trained in this skill can sway NPCs more effectively and unlock favorable outcomes."),
    INTIMIDATION("Intimidation", 6, Category.SPEECH,
            "The art of using fear and force of will to influence others. " +
                    "Those trained in this skill can cow enemies and NPCs into compliance."),
    DECEPTION("Deception", 6, Category.SPEECH,
            "The art of lying, bluffing and misdirection. " +
                    "Those trained in this skill can mislead NPCs and disguise their true intentions."),
    INSIGHT("Insight", 6, Category.SPEECH,
            "The art of reading people and detecting lies. " +
                    "Those trained in this skill can sense deception and understand the true motives of others.");

    // ── Category ──────────────────────────────────────────────────────────────

    public enum Category {
        COMBAT,    // warm red/orange
        MAGIC,     // blue/purple
        SURVIVAL,  // green
        THIEF,     // dark purple/grey
        GATHERING, // earthy green
        MOVEMENT,  // cyan/teal
        CRAFTING,  // amber/orange
        SPEECH     // warm gold/tan
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
            case COMBAT    -> 0xFFFF6633; // warm red/orange
            case MAGIC     -> 0xFF8866FF; // blue/purple
            case SURVIVAL  -> 0xFF44BB44; // green
            case THIEF     -> 0xFF886699; // dark purple/grey
            case GATHERING -> 0xFF558833; // earthy green
            case MOVEMENT  -> 0xFF00CCBB; // cyan/teal
            case CRAFTING  -> 0xFFCC7722; // amber/orange
            case SPEECH    -> 0xFFDDAA33; // warm gold/tan
        };
    }

    public ItemStack getIconItem() {
        return switch (this) {
            case ONE_HANDED    -> new ItemStack(Items.IRON_SWORD);
            case TWO_HANDED    -> new ItemStack(Items.IRON_AXE);
            case THROWING      -> new ItemStack(BasicWeaponItems.IRON_SHURIKEN);
            case ARCHERY       -> new ItemStack(Items.BOW);
            case BLOCK         -> new ItemStack(Items.SHIELD);
            case UNARMED       -> new ItemStack(Items.IRON_INGOT); // placeholder
            case HEAVY_ARMOR   -> new ItemStack(Items.IRON_CHESTPLATE);
            case LIGHT_ARMOR   -> new ItemStack(Items.LEATHER_CHESTPLATE);
            case ATHLETICS     -> new ItemStack(Items.FEATHER);
            case ACROBATICS    -> new ItemStack(Items.SLIME_BALL);
            case EVASION       -> new ItemStack(Items.WIND_CHARGE);
            case ALCHEMY       -> new ItemStack(Items.POTION);
            case RITUAL_ARTS   -> new ItemStack(Items.ENDER_EYE);
            case DESTRUCTION   -> new ItemStack(Items.FIRE_CHARGE);
            case RESTORATION   -> new ItemStack(Items.GLISTERING_MELON_SLICE);
            case CONJURATION   -> new ItemStack(Items.SOUL_LANTERN);
            case ILLUSION      -> new ItemStack(Items.SPIDER_EYE);
            case ALTERATION    -> new ItemStack(Items.NETHER_STAR);
            case ENCHANTING    -> new ItemStack(Items.ENCHANTED_BOOK);
            case NECROMANCY    -> new ItemStack(Items.SKELETON_SKULL);
            case MINING        -> new ItemStack(Items.IRON_PICKAXE);
            case WOODCUTTING   -> new ItemStack(Items.OAK_LOG);
            case FARMING       -> new ItemStack(Items.WHEAT);
            case FISHING       -> new ItemStack(Items.FISHING_ROD);
            case HUNTING       -> new ItemStack(Items.BONE); // placeholder until hunting knife
            case HERBALISM     -> new ItemStack(Items.DANDELION);
            case NAVIGATION    -> new ItemStack(Items.COMPASS);
            case TAMING        -> new ItemStack(Items.LEAD);
            case PERCEPTION    -> new ItemStack(Items.SPYGLASS);
            case SMITHING      -> new ItemStack(Items.ANVIL);
            case COOKING       -> new ItemStack(Items.COOKED_BEEF);
            case TAILORING     -> new ItemStack(Items.STRING);
            case CARPENTRY     -> new ItemStack(Items.OAK_PLANKS);
            case SNEAK         -> new ItemStack(Items.LEATHER_BOOTS);
            case LOCKPICKING   -> new ItemStack(Items.TRIPWIRE_HOOK);
            case PICKPOCKET    -> new ItemStack(Items.RABBIT_FOOT);
            case INVESTIGATION -> new ItemStack(Items.PAPER);
            case PERSUASION    -> new ItemStack(Items.EMERALD);
            case INTIMIDATION  -> new ItemStack(Items.BLAZE_ROD);
            case DECEPTION     -> new ItemStack(Items.POISONOUS_POTATO);
            case INSIGHT       -> new ItemStack(Items.ENDER_EYE); // shares with RITUAL_ARTS for now
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