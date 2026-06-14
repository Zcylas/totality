package zcylas.totality.api.magic.spell;

/**
 * Standard spell slot tables for each caster archetype.
 *
 * Row  = class level (1-based).
 * Column = slot level index (0-based): index 0 = 1st-level slots, index 9 = 10th-level slots.
 *
 * ── Multiclassing ──────────────────────────────────────────────────────────
 * Spell slots are pooled into one shared bank. Calculate combined caster level:
 *   Full casters  × 1   (Wizard, Sorcerer, Cleric, Druid, Bard)
 *   Half casters  × ½↓  (Paladin, Ranger — floor division)
 *   Third casters × ⅓↓  (Eldritch Knight / Spellblade — floor division)
 *   Warlock       → SEPARATE Pact Magic pool, never added to the shared pool
 * Then call {@link #forFullCaster(int)} with the combined level.
 * Use {@link #combinedCasterLevel} as the calculation helper.
 *
 * ── Warlock ─────────────────────────────────────────────────────────────────
 * Pact Magic slots cap at 5th level permanently (D&D accurate).
 * Levels 6–9 high-level spells are handled via Mystic Arcanum (once per long
 * rest, separate class feature — not tracked in SpellSlotComponent).
 * Levels 21–30 extend via Extended Arcanum (same mechanic, deeper patron gifts).
 * The pact slot table only tracks count growth, never level growth.
 */
public final class SpellSlotTable {

    // ── Full caster ───────────────────────────────────────────────────────────
    // Levels 1–20: D&D 5e exactly.
    // Levels 21–30:
    //   • 1st-level grows to 6  (utility, rituals — feels plentiful at high level)
    //   • 2nd-level grows to 5
    //   • High-level slots fill their 2nd and 3rd counts progressively
    //   • Level 25: first 10th-level slot (epic magic milestone)
    //   • Level 30: second 10th-level slot (full epic ceiling)
    public static final int[][] FULL_CASTER = {
            // 1st  2nd  3rd  4th  5th  6th  7th  8th  9th 10th
            {  2,   0,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  1
            {  3,   0,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  2
            {  4,   2,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  3
            {  4,   3,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  4
            {  4,   3,   2,   0,   0,   0,   0,   0,   0,   0 }, // lv  5
            {  4,   3,   3,   0,   0,   0,   0,   0,   0,   0 }, // lv  6
            {  4,   3,   3,   1,   0,   0,   0,   0,   0,   0 }, // lv  7
            {  4,   3,   3,   2,   0,   0,   0,   0,   0,   0 }, // lv  8
            {  4,   3,   3,   3,   1,   0,   0,   0,   0,   0 }, // lv  9
            {  4,   3,   3,   3,   2,   0,   0,   0,   0,   0 }, // lv 10
            {  4,   3,   3,   3,   2,   1,   0,   0,   0,   0 }, // lv 11
            {  4,   3,   3,   3,   2,   1,   0,   0,   0,   0 }, // lv 12
            {  4,   3,   3,   3,   2,   1,   1,   0,   0,   0 }, // lv 13
            {  4,   3,   3,   3,   2,   1,   1,   0,   0,   0 }, // lv 14
            {  4,   3,   3,   3,   2,   1,   1,   1,   0,   0 }, // lv 15
            {  4,   3,   3,   3,   2,   1,   1,   1,   0,   0 }, // lv 16
            {  4,   3,   3,   3,   2,   1,   1,   1,   1,   0 }, // lv 17
            {  4,   3,   3,   3,   3,   1,   1,   1,   1,   0 }, // lv 18
            {  4,   3,   3,   3,   3,   2,   1,   1,   1,   0 }, // lv 19
            {  4,   3,   3,   3,   3,   2,   2,   1,   1,   0 }, // lv 20 ← D&D 5e cap
            // ── Totality extension ────────────────────────────────────────────
            {  5,   4,   3,   3,   3,   2,   2,   2,   1,   0 }, // lv 21 1st→5, 2nd→4, 8th→2
            {  5,   4,   3,   3,   3,   2,   2,   2,   2,   0 }, // lv 22 9th→2
            {  5,   4,   3,   3,   3,   3,   2,   2,   2,   0 }, // lv 23 6th→3
            {  5,   4,   3,   3,   3,   3,   3,   2,   2,   0 }, // lv 24 7th→3
            {  5,   4,   3,   3,   3,   3,   3,   3,   2,   1 }, // lv 25 8th→3, 10th unlocks!
            {  5,   5,   3,   3,   3,   3,   3,   3,   3,   1 }, // lv 26 2nd→5, 9th→3
            {  6,   5,   4,   3,   3,   3,   3,   3,   3,   1 }, // lv 27 1st→6, 3rd→4
            {  6,   5,   4,   4,   3,   3,   3,   3,   3,   1 }, // lv 28 4th→4
            {  6,   5,   4,   4,   4,   3,   3,   3,   3,   1 }, // lv 29 5th→4
            {  6,   5,   4,   4,   4,   4,   3,   3,   3,   2 }, // lv 30 6th→4, 2nd 10th slot
    };

    // ── Half caster (Ranger, Paladin) ─────────────────────────────────────────
    // For multiclassing always use forFullCaster(combinedLevel) instead.
    // This table is for single-class half-casters only.
    // Levels 1–20: D&D 5e (caps at 5th-level spells at level 20).
    // Levels 21–30: slowly unlock 6th and 7th-level spells (a level 30 half-caster
    // has a combined contribution of 15, equivalent to a lv-15 full caster).
    public static final int[][] HALF_CASTER = {
            // 1st  2nd  3rd  4th  5th  6th  7th  8th  9th 10th
            {  0,   0,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  1
            {  2,   0,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  2
            {  3,   0,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  3
            {  3,   0,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  4
            {  4,   2,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  5
            {  4,   2,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  6
            {  4,   3,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  7
            {  4,   3,   0,   0,   0,   0,   0,   0,   0,   0 }, // lv  8
            {  4,   3,   2,   0,   0,   0,   0,   0,   0,   0 }, // lv  9
            {  4,   3,   2,   0,   0,   0,   0,   0,   0,   0 }, // lv 10
            {  4,   3,   3,   0,   0,   0,   0,   0,   0,   0 }, // lv 11
            {  4,   3,   3,   0,   0,   0,   0,   0,   0,   0 }, // lv 12
            {  4,   3,   3,   1,   0,   0,   0,   0,   0,   0 }, // lv 13
            {  4,   3,   3,   1,   0,   0,   0,   0,   0,   0 }, // lv 14
            {  4,   3,   3,   2,   1,   0,   0,   0,   0,   0 }, // lv 15
            {  4,   3,   3,   2,   1,   0,   0,   0,   0,   0 }, // lv 16
            {  4,   3,   3,   3,   1,   0,   0,   0,   0,   0 }, // lv 17
            {  4,   3,   3,   3,   2,   0,   0,   0,   0,   0 }, // lv 18
            {  4,   3,   3,   3,   2,   0,   0,   0,   0,   0 }, // lv 19
            {  4,   3,   3,   3,   3,   0,   0,   0,   0,   0 }, // lv 20 ← D&D cap
            // ── Totality extension ────────────────────────────────────────────
            {  4,   3,   3,   3,   3,   1,   0,   0,   0,   0 }, // lv 21 6th unlocks
            {  4,   3,   3,   3,   3,   1,   0,   0,   0,   0 }, // lv 22
            {  5,   4,   3,   3,   3,   1,   1,   0,   0,   0 }, // lv 23 1st→5, 2nd→4, 7th unlocks
            {  5,   4,   3,   3,   3,   1,   1,   0,   0,   0 }, // lv 24
            {  5,   4,   3,   3,   3,   2,   1,   1,   0,   0 }, // lv 25 6th→2, 8th unlocks
            {  5,   4,   3,   3,   3,   2,   1,   1,   0,   0 }, // lv 26
            {  5,   4,   4,   3,   3,   2,   2,   1,   0,   0 }, // lv 27 3rd→4, 7th→2
            {  5,   4,   4,   3,   3,   2,   2,   1,   0,   0 }, // lv 28
            {  5,   4,   4,   4,   3,   2,   2,   1,   1,   0 }, // lv 29 4th→4, 9th unlocks
            {  5,   4,   4,   4,   3,   2,   2,   2,   1,   0 }, // lv 30 8th→2
    };

    // ── Warlock Pact Magic ────────────────────────────────────────────────────
    // Pact Magic caps at 5th-level slots permanently (D&D accurate).
    // The slot LEVEL never increases beyond 5. Only slot COUNT grows.
    // Higher-level spells (6th–10th) are handled via Mystic Arcanum and
    // Extended Arcanum class features — they are NOT tracked here.
    // Short-rest recovery. Values: [slot_count, slot_level].
    public static final int[][] WARLOCK_PACT = {
            { 1, 1 }, { 2, 1 }, { 2, 2 }, { 2, 2 }, { 2, 3 }, // lv  1– 5
            { 2, 3 }, { 2, 4 }, { 2, 4 }, { 2, 5 }, { 2, 5 }, // lv  6–10 ← 5th level cap
            { 3, 5 }, { 3, 5 }, { 3, 5 }, { 3, 5 }, { 3, 5 }, // lv 11–15
            { 3, 5 }, { 4, 5 }, { 4, 5 }, { 4, 5 }, { 4, 5 }, // lv 16–20
            // Totality: no level increase, just more slots as the pact deepens
            { 4, 5 }, { 4, 5 }, { 5, 5 }, { 5, 5 }, { 5, 5 }, // lv 21–25
            { 5, 5 }, { 5, 5 }, { 6, 5 }, { 6, 5 }, { 6, 5 }, // lv 26–30
    };

    // ── Lookup helpers ────────────────────────────────────────────────────────

    /** Full caster, or combined multiclass level. */
    public static int[] forFullCaster(int classLevel) {
        return FULL_CASTER[Math.clamp(classLevel - 1, 0, FULL_CASTER.length - 1)];
    }

    /** Half caster, single-class only. For multiclassing use forFullCaster(combinedLevel). */
    public static int[] forHalfCaster(int classLevel) {
        return HALF_CASTER[Math.clamp(classLevel - 1, 0, HALF_CASTER.length - 1)];
    }

    /** Warlock Pact Magic: [slotCount, slotLevel]. Never exceeds 5th-level slots. */
    public static int[] forWarlock(int classLevel) {
        return WARLOCK_PACT[Math.clamp(classLevel - 1, 0, WARLOCK_PACT.length - 1)];
    }

    /**
     * Calculates the combined spellcasting level for multiclassing.
     * Warlock is NOT included — Pact Magic is always a separate pool.
     *
     * @param fullCasterLevels  total levels in full-caster classes
     * @param halfCasterLevels  total levels in half-caster classes (Paladin, Ranger)
     * @param thirdCasterLevels total levels in third-caster classes (Eldritch Knight, Spellblade)
     */
    public static int combinedCasterLevel(int fullCasterLevels,
                                          int halfCasterLevels,
                                          int thirdCasterLevels) {
        return Math.min(30,
                fullCasterLevels
                        + (halfCasterLevels  / 2)   // floor per D&D 5e rules
                        + (thirdCasterLevels / 3));  // floor
    }

    private SpellSlotTable() {}
}