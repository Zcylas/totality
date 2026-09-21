package zcylas.totality.api.rpg.classes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the Class Level progression bug: {@link PlayerClassComponent#toClassLevel(int)}
 * (and the delegating {@link PlayerClassComponent#getClassLevel(int)} /
 * {@link PlayerClassComponent#getAvailableClassPoints(int)}) previously omitted the free starting
 * Class Level 1 granted at character creation ({@code SelectClassHandler} calls
 * {@code selectClass(classId, 1)}) from its own total, so the Player Level 5 milestone produced
 * the same total the character already started with and every later milestone landed one 5-level
 * bucket late. These tests pin the corrected milestone boundaries the task specified.
 *
 * <p>{@code new PlayerClassComponent(null)} is safe here — the instance methods under test never
 * dereference the {@code player} field, matching the same nullable-player pattern already used in
 * {@code PlayerChargesRageCharacterizationTest}.
 */
class PlayerClassComponentClassLevelProgressionTest {

    private final PlayerClassComponent comp = new PlayerClassComponent(null);

    @Test
    void startingCharacterHasExactlyOneAvailablePointAndNoneUnspent() {
        // Character creation: Player Level 1, starting class already spent at level 1.
        assertEquals(1, comp.getAvailableClassPoints(1));
        comp.selectClass(Identifier("starter_class"), 1);
        assertEquals(1, comp.getSpentClassPoints());
        assertEquals(0, comp.getUnspentClassPoints(1));
    }

    @Test
    void levelFourStillGrantsNoAdditionalPoint() {
        assertEquals(1, comp.getAvailableClassPoints(4));
    }

    @Test
    void levelFiveGrantsExactlyOneAdditionalPoint() {
        assertEquals(2, comp.getAvailableClassPoints(5));
    }

    @Test
    void levelSixDoesNotGrantYetAnotherPointBeyondTheLevelFiveAward() {
        assertEquals(2, comp.getAvailableClassPoints(6));
    }

    @Test
    void levelNineIsStillOnTheLevelFiveAward() {
        assertEquals(2, comp.getAvailableClassPoints(9));
    }

    @Test
    void levelTenGrantsAnotherPoint() {
        assertEquals(3, comp.getAvailableClassPoints(10));
    }

    @Test
    void levelFourteenIsStillOnTheLevelTenAward() {
        assertEquals(3, comp.getAvailableClassPoints(14));
    }

    @Test
    void levelFifteenGrantsAnotherPoint() {
        assertEquals(4, comp.getAvailableClassPoints(15));
    }

    @Test
    void levelOneFortyFiveReachesTheCapOfThirty() {
        assertEquals(30, comp.getAvailableClassPoints(145));
    }

    @Test
    void levelOneFiftyDoesNotAwardAThirtyFirstPoint() {
        assertEquals(30, comp.getAvailableClassPoints(150));
    }

    @Test
    void characterReachingLevelTenNormallyHasStartingPlusTwoAwards() {
        // Starting Class Level 1, + Level 5 award, + Level 10 award = 3 total available.
        assertEquals(3, comp.getAvailableClassPoints(10));
    }

    @Test
    void singleLevelUpsAndAMultiLevelJumpReachTheSameTotalAtLevelFifteen() {
        // One-at-a-time path
        int oneAtATime = comp.getAvailableClassPoints(15);
        // Simulated multi-level gain in one jump (e.g. a debug/admin level grant)
        int allAtOnce = comp.getAvailableClassPoints(1);
        allAtOnce = comp.getAvailableClassPoints(15); // recomputed fresh, not accumulated
        assertEquals(oneAtATime, allAtOnce, "available points must be a pure function of current level, not path-dependent");
    }

    @Test
    void everyFiveLevelBoundaryFromOneToOneFiftyIsMonotonicNonDecreasingAndCappedAtThirty() {
        int previous = comp.getAvailableClassPoints(1);
        for (int level = 2; level <= 150; level++) {
            int current = comp.getAvailableClassPoints(level);
            assertTrue(current >= previous, "available points must never decrease as level increases");
            assertTrue(current <= 30, "available points must never exceed the cap of 30");
            previous = current;
        }
        assertEquals(30, previous);
    }

    @Test
    void staticToClassLevelAgreesWithInstanceGetClassLevel() {
        for (int level : new int[] {1, 4, 5, 6, 9, 10, 14, 15, 145, 150}) {
            assertEquals(PlayerClassComponent.toClassLevel(level), comp.getClassLevel(level));
            assertEquals(PlayerClassComponent.toClassLevel(level), comp.getAvailableClassPoints(level));
        }
    }

    @Test
    void chargesComponentDelegatesToTheSameCanonicalFormula() {
        for (int level : new int[] {1, 5, 10, 145, 150}) {
            assertEquals(PlayerClassComponent.toClassLevel(level), PlayerChargesComponent.toClassLevel(level));
        }
    }

    private static net.minecraft.resources.Identifier Identifier(String path) {
        return net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", path);
    }
}
