package zcylas.totality.api.rpg.stats;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds all RPG stats for a player.
 * Stored in PlayerStatsComponent, persists on death, synced to client.
 *
 * Level cap: 100
 * Starting ability scores: 10 for all
 * Attribute points per level: 5, freely distributed across any AbilityScore
 */
public class PlayerStats {

    public static final int MAX_LEVEL = 100;
    public static final int BASE_SCORE = 10;
    public static final int ATTRIBUTE_POINTS_PER_LEVEL = 5;

    private int level = 1;
    private int characterXp = 0;
    private int unspentAttributePoints = 0;
    private final Map<AbilityScore, Integer> scores = new EnumMap<>(AbilityScore.class);

    public PlayerStats() {
        for (AbilityScore score : AbilityScore.values()) {
            scores.put(score, BASE_SCORE);
        }
    }

    // ── Level ─────────────────────────────────────────────────────────────────

    public int getLevel() { return level; }

    public boolean isMaxLevel() { return level >= MAX_LEVEL; }

    /**
     * Attempts to level up. Returns true if level-up occurred.
     * Awards ATTRIBUTE_POINTS_PER_LEVEL unspent points on level-up.
     */
    public boolean tryLevelUp() {
        if (isMaxLevel()) return false;
        level++;
        unspentAttributePoints += ATTRIBUTE_POINTS_PER_LEVEL;
        characterXp = 0;
        return true;
    }

    /** Used by serialization only — bypasses level-up logic. */
    public void setLevelDirectly(int level) {
        this.level = Math.clamp(level, 1, MAX_LEVEL);
    }

    // ── Character XP ──────────────────────────────────────────────────────────

    public int getCharacterXp() { return characterXp; }

    /**
     * XP required to reach the next level.
     * Formula: (level + 3) * 25 — scales gradually like Skyrim.
     */
    public int getXpRequiredForNextLevel() {
        return (level + 3) * 25;
    }

    /**
     * Adds character XP. Returns true if a level-up occurred.
     */
    public boolean addCharacterXp(int amount) {
        if (isMaxLevel()) return false;
        characterXp += amount;
        if (characterXp >= getXpRequiredForNextLevel()) {
            return tryLevelUp();
        }
        return false;
    }

    /** Used by serialization only. */
    public void setCharacterXpDirectly(int xp) {
        this.characterXp = Math.max(0, xp);
    }

    // ── Attribute Points ──────────────────────────────────────────────────────

    public int getUnspentAttributePoints() { return unspentAttributePoints; }

    /**
     * Spends one attribute point into the given score.
     * Returns true if the point was spent successfully.
     */
    public boolean spendAttributePoint(AbilityScore score) {
        if (unspentAttributePoints <= 0) return false;
        scores.merge(score, 1, Integer::sum);
        unspentAttributePoints--;
        return true;
    }

    /** Used by serialization only. */
    public void setUnspentAttributePointsDirectly(int points) {
        this.unspentAttributePoints = Math.max(0, points);
    }

    // ── Ability Scores ────────────────────────────────────────────────────────

    public int getScore(AbilityScore score) {
        return scores.getOrDefault(score, BASE_SCORE);
    }

    public int getModifier(AbilityScore score) {
        return AbilityScore.getModifier(getScore(score));
    }

    /**
     * Sets a score directly — used for class/race bonuses and serialization.
     */
    public void setScore(AbilityScore score, int value) {
        scores.put(score, Math.max(1, value));
    }

    public Map<AbilityScore, Integer> getAllScores() {
        return new EnumMap<>(scores);
    }

    // ── Derived stats ─────────────────────────────────────────────────────────

    /**
     * Max HP bonus from CON modifier.
     * At CON 10 (modifier 0) → bonus 0 → total HP = 100 (base) + 0 = 100.
     */
    public int getMaxHpBonus() {
        return getModifier(AbilityScore.CON) * 5;
    }

    /**
     * Max Stamina bonus from END modifier.
     * At END 10 (modifier 0) → bonus 0 → total stamina = 100 (base) + 0 = 100.
     */
    public int getMaxStaminaBonus() {
        return getModifier(AbilityScore.END) * 10;
    }

    /**
     * Max Mana bonus from INT modifier.
     * At INT 10 (modifier 0) → bonus 0 → total mana = 100 (base) + 0 = 100.
     */
    public int getMaxManaBonus() {
        return getModifier(AbilityScore.INT) * 10;
    }

    /**
     * Attack damage bonus from STR modifier.
     * Each STR modifier point = +0.5 attack damage.
     */
    public float getStrAttackBonus() {
        return getModifier(AbilityScore.STR) * 0.5f;
    }
}