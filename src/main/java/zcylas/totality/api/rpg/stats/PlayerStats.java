package zcylas.totality.api.rpg.stats;

import java.util.EnumMap;
import java.util.Map;

/**
 * Holds all RPG stats for a player using a layered bonus system.
 * Final score = base(10) + spentPoints + originBonus + classBonus + itemBonus.
 * Any change to any layer triggers recalculate() automatically.
 */
public class PlayerStats {

    public static final int MAX_LEVEL                  = 100;
    public static final int BASE_SCORE                 = 10;
    public static final int ATTRIBUTE_POINTS_PER_LEVEL = 5;

    private int level                  = 1;
    private int characterXp            = 0;
    private int unspentAttributePoints = 0;

    // ── Bonus layers ──────────────────────────────────────────────────────────
    /** Points spent by the player via attribute point system */
    private final Map<AbilityScore, Integer> spentPoints = new EnumMap<>(AbilityScore.class);
    /** Bonuses from species origin (replaces racial + subrace layers) */
    private final Map<AbilityScore, Integer> originBonus = new EnumMap<>(AbilityScore.class);
    /** Bonuses from class */
    private final Map<AbilityScore, Integer> classBonus  = new EnumMap<>(AbilityScore.class);
    /** Bonuses from items/equipment */
    private final Map<AbilityScore, Integer> itemBonus   = new EnumMap<>(AbilityScore.class);

    /** Cached final scores — recalculated whenever any layer changes */
    private final Map<AbilityScore, Integer> finalScores = new EnumMap<>(AbilityScore.class);

    public PlayerStats() {
        for (AbilityScore score : AbilityScore.values()) {
            spentPoints.put(score, 0);
            originBonus.put(score, 0);
            classBonus .put(score, 0);
            itemBonus  .put(score, 0);
            finalScores.put(score, BASE_SCORE);
        }
    }

    // ── Recalculation ─────────────────────────────────────────────────────────

    /**
     * Recalculates all final scores from all bonus layers.
     * Call after any layer changes.
     */
    public void recalculate() {
        for (AbilityScore score : AbilityScore.values()) {
            int total = BASE_SCORE
                    + spentPoints.getOrDefault(score, 0)
                    + originBonus.getOrDefault(score, 0)
                    + classBonus .getOrDefault(score, 0)
                    + itemBonus  .getOrDefault(score, 0);
            finalScores.put(score, Math.max(1, total));
        }
    }

    // ── Layer setters ─────────────────────────────────────────────────────────

    public void setOriginBonus(AbilityScoreBonus bonus) {
        applyBonusToMap(originBonus, bonus);
        recalculate();
    }

    public void setClassBonus(AbilityScoreBonus bonus) {
        applyBonusToMap(classBonus, bonus);
        recalculate();
    }

    public void setItemBonus(AbilityScoreBonus bonus) {
        applyBonusToMap(itemBonus, bonus);
        recalculate();
    }

    public void clearOriginBonus() {
        originBonus.replaceAll((k, v) -> 0);
        recalculate();
    }

    public void clearClassBonus() {
        classBonus.replaceAll((k, v) -> 0);
        recalculate();
    }

    public void clearItemBonus() {
        itemBonus.replaceAll((k, v) -> 0);
        recalculate();
    }

    private void applyBonusToMap(Map<AbilityScore, Integer> map, AbilityScoreBonus bonus) {
        map.put(AbilityScore.STR, bonus.str());
        map.put(AbilityScore.DEX, bonus.dex());
        map.put(AbilityScore.CON, bonus.con());
        map.put(AbilityScore.END, bonus.end());
        map.put(AbilityScore.INT, bonus.intel());
        map.put(AbilityScore.WIS, bonus.wis());
        map.put(AbilityScore.CHA, bonus.cha());
        map.put(AbilityScore.FTH, bonus.fth());
    }

    // ── Score access ──────────────────────────────────────────────────────────

    public int getScore(AbilityScore score) {
        return finalScores.getOrDefault(score, BASE_SCORE);
    }

    public int getModifier(AbilityScore score) {
        return AbilityScore.getModifier(getScore(score));
    }

    public Map<AbilityScore, Integer> getAllScores() {
        return new EnumMap<>(finalScores);
    }

    // ── Attribute point spending ──────────────────────────────────────────────

    public int getUnspentAttributePoints() { return unspentAttributePoints; }

    public boolean spendAttributePoint(AbilityScore score) {
        if (unspentAttributePoints <= 0) return false;
        spentPoints.merge(score, 1, Integer::sum);
        unspentAttributePoints--;
        recalculate();
        return true;
    }

    public void setUnspentAttributePointsDirectly(int points) {
        this.unspentAttributePoints = Math.max(0, points);
    }

    // ── Level ─────────────────────────────────────────────────────────────────

    public int getLevel()            { return level; }
    public boolean isMaxLevel()      { return level >= MAX_LEVEL; }

    public boolean tryLevelUp() {
        if (isMaxLevel()) return false;
        level++;
        unspentAttributePoints += ATTRIBUTE_POINTS_PER_LEVEL;
        characterXp = 0;
        return true;
    }

    public void setLevelDirectly(int level) {
        this.level = Math.clamp(level, 1, MAX_LEVEL);
    }

    // ── Character XP ──────────────────────────────────────────────────────────

    public int getCharacterXp() { return characterXp; }

    public int getXpRequiredForNextLevel() {
        return (level + 3) * 25;
    }

    public boolean addCharacterXp(int amount) {
        if (isMaxLevel()) return false;
        characterXp += amount;
        if (characterXp >= getXpRequiredForNextLevel()) {
            return tryLevelUp();
        }
        return false;
    }

    public void setCharacterXpDirectly(int xp) {
        this.characterXp = Math.max(0, xp);
    }

    // ── Derived stats ─────────────────────────────────────────────────────────

    public int getMaxHpBonus() {
        return getModifier(AbilityScore.CON) * 5;
    }

    public int getMaxStaminaBonus() {
        return getModifier(AbilityScore.END) * 10;
    }

    public int getMaxManaBonus() {
        return getModifier(AbilityScore.INT) * 10;
    }

    public float getStrAttackBonus() {
        return getModifier(AbilityScore.STR) * 0.5f;
    }

    // ── Serialization helpers ─────────────────────────────────────────────────

    public void setSpentPointsDirectly(AbilityScore score, int value) {
        spentPoints.put(score, value);
    }

    public int getSpentPoints(AbilityScore score) {
        return spentPoints.getOrDefault(score, 0);
    }

    public void setOriginBonusDirectly(AbilityScore score, int value) {
        originBonus.put(score, value);
    }

    public int getOriginBonus(AbilityScore score) {
        return originBonus.getOrDefault(score, 0);
    }

    public void setClassBonusDirectly(AbilityScore score, int value) {
        classBonus.put(score, value);
    }

    public int getClassBonus(AbilityScore score) {
        return classBonus.getOrDefault(score, 0);
    }

    public void setItemBonusDirectly(AbilityScore score, int value) {
        itemBonus.put(score, value);
    }

    public int getItemBonus(AbilityScore score) {
        return itemBonus.getOrDefault(score, 0);
    }
}