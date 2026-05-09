package zcylas.totality.api.rpg.skills.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds all skill data for a player.
 * Stored in PlayerSkillsComponent, persists on death, synced to client.
 */
public class PlayerSkills {

    private final Map<Skill, SkillData> skills = new EnumMap<>(Skill.class);

    public PlayerSkills() {
        for (Skill skill : Skill.values()) {
            skills.put(skill, new SkillData());
        }
    }

    public SkillData getData(Skill skill) {
        return skills.computeIfAbsent(skill, s -> new SkillData());
    }

    public int getLevel(Skill skill) {
        return getData(skill).getLevel();
    }

    public int getXp(Skill skill) {
        return getData(skill).getXp();
    }

    public int getXpRequired(Skill skill) {
        return skill.getXpRequired(getLevel(skill));
    }

    /**
     * Adds XP to a skill.
     * Returns the new skill level if a level-up occurred, or -1 if not.
     */
    public int addXp(Skill skill, int amount) {
        SkillData data = getData(skill);
        boolean leveledUp = data.addXp(skill, amount);
        return leveledUp ? data.getLevel() : -1;
    }

    public Map<Skill, SkillData> getAllSkills() {
        return skills;
    }
}