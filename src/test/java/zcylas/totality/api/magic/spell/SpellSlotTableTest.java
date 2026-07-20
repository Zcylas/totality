package zcylas.totality.api.magic.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization coverage for {@link SpellSlotTable} — the audit found essentially no existing
 * automated coverage for this system. These tests characterize the existing progression tables
 * exactly as authored; they do not rewrite or reinterpret any progression values.
 */
class SpellSlotTableTest {

    @Test
    void fullCasterTableCoversClassLevelsOneThroughThirty() {
        assertEquals(30, SpellSlotTable.FULL_CASTER.length);
        assertEquals(30, SpellSlotTable.HALF_CASTER.length);
        assertEquals(30, SpellSlotTable.WARLOCK_PACT.length);
    }

    @Test
    void everyStandardCasterRowHasTenSlotTiers() {
        for (int[] row : SpellSlotTable.FULL_CASTER) {
            assertEquals(10, row.length);
        }
        for (int[] row : SpellSlotTable.HALF_CASTER) {
            assertEquals(10, row.length);
        }
    }

    @Test
    void warlockPactRowsAreCountAndTierPairs() {
        for (int[] row : SpellSlotTable.WARLOCK_PACT) {
            assertEquals(2, row.length);
        }
    }

    @Test
    void fullCasterLevelOneGrantsTwoFirstLevelSlotsOnly() {
        int[] slots = SpellSlotTable.forFullCaster(1);
        assertEquals(2, slots[0]);
        for (int i = 1; i < slots.length; i++) {
            assertEquals(0, slots[i], "index " + i);
        }
    }

    @Test
    void fullCasterLevelTwentyMatchesDnd5eCap() {
        // D&D 5e level-20 full caster: 4/3/3/3/3/2/2/1/1, no 10th-level slot yet.
        int[] slots = SpellSlotTable.forFullCaster(20);
        assertArrayEquals(new int[] {4, 3, 3, 3, 3, 2, 2, 1, 1, 0}, slots);
    }

    @Test
    void tenthLevelSlotFirstUnlocksAtEpicLevelTwentyFive() {
        assertEquals(0, SpellSlotTable.forFullCaster(24)[9], "no 10th-level slot before level 25");
        assertEquals(1, SpellSlotTable.forFullCaster(25)[9], "level 25 is the authored epic-progression unlock point");
    }

    @Test
    void secondTenthLevelSlotUnlocksAtLevelThirty() {
        assertEquals(2, SpellSlotTable.forFullCaster(30)[9]);
    }

    @Test
    void forFullCasterClampsAboveThirtyToTheLevelThirtyRow() {
        assertArrayEquals(SpellSlotTable.forFullCaster(30), SpellSlotTable.forFullCaster(999));
    }

    @Test
    void forFullCasterClampsBelowOneToTheLevelOneRow() {
        assertArrayEquals(SpellSlotTable.forFullCaster(1), SpellSlotTable.forFullCaster(0));
        assertArrayEquals(SpellSlotTable.forFullCaster(1), SpellSlotTable.forFullCaster(-5));
    }

    @Test
    void halfCasterLevelOneGrantsNoSlots() {
        // D&D 5e: half-casters (Paladin/Ranger) gain no slots until class level 2.
        int[] slots = SpellSlotTable.forHalfCaster(1);
        for (int value : slots) {
            assertEquals(0, value);
        }
    }

    @Test
    void halfCasterLevelTwentyMatchesDnd5eCap() {
        int[] slots = SpellSlotTable.forHalfCaster(20);
        assertArrayEquals(new int[] {4, 3, 3, 3, 3, 0, 0, 0, 0, 0}, slots);
    }

    @Test
    void warlockPactCapsPermanentlyAtFifthLevelSlots() {
        for (int level = 9; level <= 30; level++) {
            assertEquals(5, SpellSlotTable.forWarlock(level)[1],
                    "Warlock Pact Magic slot tier must never exceed 5, level " + level);
        }
    }

    @Test
    void warlockPactSlotCountGrowsAcrossLevels() {
        assertEquals(1, SpellSlotTable.forWarlock(1)[0]);
        assertTrue(SpellSlotTable.forWarlock(30)[0] > SpellSlotTable.forWarlock(1)[0],
                "pact slot count must grow from level 1 to level 30");
    }

    // ── Multiclass combined caster level ────────────────────────────────────────────────────

    @Test
    void combinedCasterLevelSumsFullCasterLevelsDirectly() {
        assertEquals(5, SpellSlotTable.combinedCasterLevel(5, 0, 0));
    }

    @Test
    void combinedCasterLevelFloorsHalfCasterContribution() {
        // 3 half-caster levels contribute floor(3/2) = 1, per D&D 5e multiclassing rules.
        assertEquals(1, SpellSlotTable.combinedCasterLevel(0, 3, 0));
        assertEquals(2, SpellSlotTable.combinedCasterLevel(0, 4, 0));
    }

    @Test
    void combinedCasterLevelFloorsThirdCasterContribution() {
        // 4 third-caster levels contribute floor(4/3) = 1.
        assertEquals(1, SpellSlotTable.combinedCasterLevel(0, 0, 4));
        assertEquals(2, SpellSlotTable.combinedCasterLevel(0, 0, 6));
    }

    @Test
    void combinedCasterLevelPoolsAllThreeContributionsTogether() {
        // Wizard 5 / Paladin 4 / Eldritch Knight 6: 5 + floor(4/2) + floor(6/3) = 5 + 2 + 2 = 9.
        assertEquals(9, SpellSlotTable.combinedCasterLevel(5, 4, 6));
    }

    @Test
    void combinedCasterLevelCapsAtThirty() {
        assertEquals(30, SpellSlotTable.combinedCasterLevel(50, 0, 0));
    }

    @Test
    void combinedCasterLevelHasNoWarlockParameterAtAll() throws NoSuchMethodException {
        // CasterProgression.WARLOCK is excluded from the pool at the caller (SpellSlotRecalculator)
        // level — combinedCasterLevel itself takes only full/half/third parameters, structurally
        // incapable of accepting a Warlock contribution.
        assertEquals(3,
                SpellSlotTable.class.getDeclaredMethod("combinedCasterLevel", int.class, int.class, int.class)
                        .getParameterCount());
    }
}
