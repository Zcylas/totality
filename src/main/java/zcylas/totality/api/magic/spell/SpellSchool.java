package zcylas.totality.api.magic.spell;

import zcylas.totality.api.rpg.skills.core.Skill;

/**
 * The school a spell belongs to.
 *
 * Distinct from {@link Skill} — skills govern XP progression, masteries, and skill trees.
 * SpellSchool is metadata on the spell itself.
 *
 * The link: your level in the corresponding {@link Skill} passively scales spells of that school
 * (e.g. higher Destruction skill → more Destruction spell damage). That lookup happens in the
 * spell scaling layer, not here.
 *
 * Schools follow Skyrim naming rather than D&D:
 *   Evocation      → DESTRUCTION
 *   Abjuration     → RESTORATION  (protective spells join healing here)
 *   Transmutation  → ALTERATION
 *   Divination     → ALTERATION   (no separate Divination school)
 *   Enchantment    → ILLUSION     (mind magic unified)
 *   Necromancy     → NECROMANCY   (split from Conjuration unlike Skyrim)
 */
public enum SpellSchool {

    // ── Six schools ───────────────────────────────────────────────────────────

    DESTRUCTION(
            "Destruction",
            "Spells that damage and destroy. Mastering Destruction makes each spell hit harder.",
            0xFFFF4422,   // warm red
            Skill.DESTRUCTION
    ),

    RESTORATION(
            "Restoration",
            "Spells that heal, protect, and ward. Mastering Restoration extends duration and potency.",
            0xFFFFCC33,   // warm gold
            Skill.RESTORATION
    ),

    CONJURATION(
            "Conjuration",
            "Spells that summon creatures, bind souls, and open gates. Mastering Conjuration strengthens what is summoned.",
            0xFF8833FF,   // deep violet
            Skill.CONJURATION
    ),

    ILLUSION(
            "Illusion",
            "Spells that alter the mind — charm, fear, sleep, and silence. Mastering Illusion lets you affect stronger targets.",
            0xFFAA44EE,   // indigo-purple
            Skill.ILLUSION
    ),

    ALTERATION(
            "Alteration",
            "Spells that change the physical world — identify, unlock, detect. Mastering Alteration extends duration and range.",
            0xFF3399FF,   // sky blue
            Skill.ALTERATION
    ),

    NECROMANCY(
            "Necromancy",
            "Spells that draw on death — drain life, raise undead, curse. Mastering Necromancy strengthens undead and deepens curses.",
            0xFF44CC66,   // sickly corpse-green
            Skill.NECROMANCY
    );

    // ── Fields ────────────────────────────────────────────────────────────────

    private final String displayName;
    private final String description;
    /** ARGB color used in the spell UI — school label, card border tint. */
    private final int color;
    /** The Skill whose level passively scales spells of this school. */
    private final Skill skill;

    SpellSchool(String displayName, String description, int color, Skill skill) {
        this.displayName = displayName;
        this.description = description;
        this.color       = color;
        this.skill       = skill;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    /** ARGB color for UI rendering. */
    public int getColor()          { return color; }
    /** The corresponding Skill — used for spell scaling lookups. */
    public Skill getSkill()        { return skill; }
}