package zcylas.totality.api.rpg.skills.core;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A mastery belonging to a skill.
 * Has a unique id, display name, and one or more ranks.
 * The required level for unlocking is the first rank's required level.
 *
 * Used by MasteryRegistry to define all masteries per skill.
 * Used by PlayerMasteries to track which rank the player has unlocked.
 */
public class Mastery {

    private final String id;
    private final String name;
    private final List<MasteryRank> ranks;
    private final @Nullable String abilityId;

    public Mastery(String id, String name, List<MasteryRank> ranks, @Nullable String abilityId) {
        this.id = id;
        this.name = name;
        this.ranks = List.copyOf(ranks);
        this.abilityId = abilityId;
    }

    public String getId()   { return id; }
    public String getName() { return name; }
    public @Nullable String getAbilityId() { return abilityId; }

    /** Total number of ranks. */
    public int getRankCount() { return ranks.size(); }

    /** Get a specific rank (0-indexed). */
    public MasteryRank getRank(int index) { return ranks.get(index); }

    /**
     * The minimum skill level required to unlock rank 1.
     * Used for sorting masteries in the skill tree (bottom = low level, top = high level).
     */
    public int getBaseRequiredLevel() { return ranks.get(0).getRequiredLevel(); }

    /**
     * Required level to unlock a specific rank (1-indexed, like the player sees).
     * Rank 1 = index 0, rank 2 = index 1 etc.
     */
    public int getRequiredLevelForRank(int rank) {
        return ranks.get(rank - 1).getRequiredLevel();
    }

    /** Description for a specific rank (1-indexed). */
    public String getDescriptionForRank(int rank) {
        return ranks.get(rank - 1).getDescription();
    }

    /** Convenience — single rank mastery constructor. */
    public static Mastery of(String id, String name, int requiredLevel, String description) {
        return new Mastery(id, name, List.of(new MasteryRank(requiredLevel, description)), null);
    }

    public static Mastery of2(String id, String name,
                              int req1, String desc1,
                              int req2, String desc2) {
        return new Mastery(id, name, List.of(
                new MasteryRank(req1, desc1),
                new MasteryRank(req2, desc2)), null);
    }

    public static Mastery of3(String id, String name,
                              int req1, String desc1,
                              int req2, String desc2,
                              int req3, String desc3) {
        return new Mastery(id, name, List.of(
                new MasteryRank(req1, desc1),
                new MasteryRank(req2, desc2),
                new MasteryRank(req3, desc3)), null);
    }
    public static Mastery ofAbility(String id, String name, int requiredLevel,
                                    String description, String abilityId) {
        return new Mastery(id, name, List.of(new MasteryRank(requiredLevel, description)), abilityId);
    }
}