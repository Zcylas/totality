package zcylas.totality.api.rpg.classes;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the multiclass Class-Level-Up event bug: {@link ClassLevelUpRegistry#fire}
 * previously computed the {@code classLevel} it hands to a class's registered
 * {@link ClassLevelUpRegistry.LevelUpHandler} via {@link PlayerClassComponent#toClassLevel(int)} —
 * the player's GLOBAL Class Level entitlement across every class — regardless of which specific
 * class was actually being fired for. That happens to look correct for a single-class character
 * (their one class's level always equals the global entitlement), but is wrong the moment a
 * player multiclasses: a class at real level 1 could receive a {@code classLevel} of 6 just
 * because the player's total entitlement (driven by overall Player Level) was 6, silently
 * missing that class's own level-3/level-2 subclass-selection triggers (Barbarian Primal Path,
 * Monk subclass, Wizard Arcane Tradition — see {@code BarbarianClass}/{@code MonkClass}/
 * {@code WizardClass}) or firing them at the wrong moment entirely.
 *
 * <p>{@code fire()} itself now sources {@code classLevel} from
 * {@link PlayerClassComponent#getClassLevel(Identifier)} instead — the same per-class stored
 * value the Class tab UI and {@code AddClassLevelHandler} already read. Exercising {@code fire()}
 * literally requires a live {@code ServerPlayer} wired into the real component-attachment system
 * (it casts its {@code ServerPlayer} parameter to {@code ComponentProvider}, a mixin-applied
 * interface that only exists on a running server), which is outside what this project's existing
 * lightweight JUnit conventions support without introducing new test infrastructure — the same
 * boundary {@code PlayerChargesRageCharacterizationTest} respects by only ever exercising directly
 * constructed component instances. These tests instead pin the exact data-layer invariant the fix
 * depends on: a specific class's stored level must be read independently of both the player's
 * global entitlement and any other class's level, in a realistic multiclass scenario.
 */
class ClassLevelUpRegistryMulticlassTest {

    @Test
    void multiclassCharacterHasIndependentPerClassLevels() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TotalityClasses.BARBARIAN_ID, 1);
        comp.setClassLevel(TotalityClasses.WIZARD_ID, 5);

        assertEquals(1, comp.getClassLevel(TotalityClasses.BARBARIAN_ID));
        assertEquals(5, comp.getClassLevel(TotalityClasses.WIZARD_ID));
    }

    @Test
    void perClassLevelDoesNotEqualThePlayersGlobalEntitlementWhenMulticlassed() {
        // Realistic multiclass scenario: Player Level 25 → global entitlement 6
        // (1 starting + floor(25/5) = 1 + 5 = 6), spent as Barbarian 1 + Wizard 5.
        int playerLevel = 25;
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TotalityClasses.BARBARIAN_ID, 1);
        comp.setClassLevel(TotalityClasses.WIZARD_ID, 5);

        int globalEntitlement = comp.getAvailableClassPoints(playerLevel);
        assertEquals(6, globalEntitlement);

        // The bug: ClassLevelUpRegistry.fire used to hand every handler this global number.
        assertNotEquals(globalEntitlement, comp.getClassLevel(TotalityClasses.BARBARIAN_ID),
                "Barbarian's own level must not be the player's total entitlement");
        // Wizard happens to equal it here only because 5 of the 6 points went there — the fix
        // doesn't depend on that; the next test proves independence directly.
    }

    @Test
    void increasingOneClassDoesNotChangeAnotherClassLevel() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TotalityClasses.WIZARD_ID, 5);
        comp.setClassLevel(TotalityClasses.BARBARIAN_ID, 1);

        comp.addClassLevel(TotalityClasses.BARBARIAN_ID); // Barbarian 1 -> 2
        assertEquals(2, comp.getClassLevel(TotalityClasses.BARBARIAN_ID));
        assertEquals(5, comp.getClassLevel(TotalityClasses.WIZARD_ID),
                "leveling Barbarian must not affect Wizard's stored level");

        comp.addClassLevel(TotalityClasses.BARBARIAN_ID); // Barbarian 2 -> 3
        assertEquals(3, comp.getClassLevel(TotalityClasses.BARBARIAN_ID));
        assertEquals(5, comp.getClassLevel(TotalityClasses.WIZARD_ID),
                "Wizard's level must remain unaffected across multiple Barbarian level-ups");
    }

    @Test
    void subclassTriggerLevelMatchesTheRealClassLevelNotTheGlobalEntitlement() {
        // Player Level 10 -> global entitlement 3 (1 + floor(10/5) = 1 + 2 = 3).
        // All 3 points spent into Wizard; Barbarian not yet taken (level 0).
        int playerLevel = 10;
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TotalityClasses.WIZARD_ID, 3);

        int globalEntitlement = comp.getAvailableClassPoints(playerLevel);
        assertEquals(3, globalEntitlement);

        // Correct source (what the fixed fire() now uses): Wizard's real level is 3, so its
        // Arcane Tradition trigger (WizardClass checks `classLevel == 2`) correctly does NOT fire.
        int wizardRealLevel = comp.getClassLevel(TotalityClasses.WIZARD_ID);
        assertEquals(3, wizardRealLevel);
        assertNotEquals(2, wizardRealLevel);

        // Buggy source (what fire() used to use): the global entitlement also happens to be 3
        // here, which would have (coincidentally, for a single-class spend) agreed — the real
        // divergence shows up once a second class enters the picture:
        comp.addClassLevel(TotalityClasses.BARBARIAN_ID); // Barbarian 0 -> 1, still playerLevel 10

        int barbarianRealLevel = comp.getClassLevel(TotalityClasses.BARBARIAN_ID);
        int buggyGlobalValueFireUsedToPass = PlayerClassComponent.toClassLevel(playerLevel);

        assertEquals(1, barbarianRealLevel,
                "Barbarian just reached its own real level 1");
        assertEquals(3, buggyGlobalValueFireUsedToPass,
                "the old buggy source is still the player's total entitlement (3), unrelated to Barbarian's actual level");
        assertNotEquals(barbarianRealLevel, buggyGlobalValueFireUsedToPass,
                "the fix must not hand Barbarian's handler the global entitlement (3) instead of Barbarian's real level (1)");
    }
}
