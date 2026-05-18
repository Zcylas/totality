package zcylas.totality.api.rpg.skills.core;

/**
 * Holds the level and accumulated XP for a single skill.
 * Starts at level 10 like Skyrim — all skills begin at a base competency.
 */
public class SkillData {

    private int level = 10;
    private int xp = 0;

    public SkillData() {}

    public int getLevel() { return level; }
    public int getXp() { return xp; }

    public void setLevelDirectly(int level) {
        this.level = Math.max(1, level);
    }

    public void setXpDirectly(int xp) {
        this.xp = Math.max(0, xp);
    }

    /**
     * Adds XP to this skill.
     * Returns true if a level-up occurred.
     */
    public boolean addXp(Skill skill, int amount) {
        xp += amount;
        boolean leveledUp = false;
        while (xp >= skill.getXpRequired(level) && level < 200) {
            xp -= skill.getXpRequired(level);
            level++;
            leveledUp = true;
        }
        return leveledUp;
    }
}