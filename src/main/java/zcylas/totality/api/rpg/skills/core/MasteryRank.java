package zcylas.totality.api.rpg.skills.core;

/**
 * One rank of a mastery.
 * A mastery can have 1 or more ranks, each requiring a higher skill level.
 */
public class MasteryRank {

    private final int requiredLevel;
    private final String description;

    public MasteryRank(int requiredLevel, String description) {
        this.requiredLevel = requiredLevel;
        this.description = description;
    }

    public int getRequiredLevel() { return requiredLevel; }
    public String getDescription() { return description; }
}