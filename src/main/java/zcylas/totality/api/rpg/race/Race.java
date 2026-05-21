package zcylas.totality.api.rpg.race;

import zcylas.totality.api.rpg.skills.core.Skill;

import java.util.Map;

/**
 * Defines a playable race with starting skill bonuses.
 * Bonuses are applied on top of the default skill level (10) when a player
 * selects this race for the first time.
 *
 * Subraces are deferred to a future update.
 */
public enum Race {

    HUMAN("Human",
            "Adaptable and ambitious, humans have spread across the known world. " +
                    "Their lack of specialization is their greatest strength — they excel at everything.",
            Map.of(
                    Skill.ONE_HANDED, 2,
                    Skill.MINING,     2,
                    Skill.ALCHEMY,    2
            )),

    DWARF("Dwarf",
            "Stout and enduring, dwarves are master craftsmen and tireless miners. " +
                    "Their bond with stone and metal runs deeper than any other race.",
            Map.of(
                    Skill.MINING,     5,
                    Skill.ONE_HANDED, 3
            )),

    ELF("Elf",
            "Ancient and attuned to the unseen currents of magic, elves walk between " +
                    "the world of the living and the arcane with natural grace.",
            Map.of(
                    Skill.ALCHEMY,     5,
                    Skill.RITUAL_ARTS, 3
            ));

    private final String displayName;
    private final String description;
    private final Map<Skill, Integer> skillBonuses;

    Race(String displayName, String description, Map<Skill, Integer> skillBonuses) {
        this.displayName  = displayName;
        this.description  = description;
        this.skillBonuses = skillBonuses;
    }

    public String getDisplayName()          { return displayName; }
    public String getDescription()          { return description; }
    public Map<Skill, Integer> getSkillBonuses() { return skillBonuses; }
}