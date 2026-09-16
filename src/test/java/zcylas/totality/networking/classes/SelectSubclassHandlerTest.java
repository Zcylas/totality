package zcylas.totality.networking.classes;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.classes.ClassCategory;
import zcylas.totality.api.rpg.classes.ClassData;
import zcylas.totality.api.rpg.classes.ClassRegistry;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.classes.SubclassData;
import zcylas.totality.api.rpg.classes.SubclassRegistry;
import zcylas.totality.api.dice.Dice;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the pre-existing production bug fixed alongside the Class Tab Quick
 * Level-Up feature (see {@code TOTALITY_CLASS_TAB_QUICK_LEVEL_UP_IMPLEMENTATION_2026-09-16.md}) and
 * for the subsequent per-class subclass storage migration (also 2026-09-16, same report's amended
 * sections): before {@link SelectSubclassHandler} existed, a subclass choice reached via ordinary
 * class leveling was silently discarded; and before the per-class migration, a single global
 * subclass slot meant a Barbarian subclass choice would incorrectly block a Wizard subclass choice
 * on the same multiclass character.
 *
 * <p>Exercises {@link SelectSubclassHandler#apply}, the pure validation+apply core, directly
 * against a {@code new PlayerClassComponent(null)} — mirroring this codebase's established
 * precedent (e.g. {@code PlayerChargesRageCharacterizationTest}, {@code
 * ClassLevelUpRegistryMulticlassTest}) of testing component logic without a real {@code
 * ServerPlayer}, since {@code apply} never dereferences one.
 *
 * <p>Registers small, test-only {@link ClassData}/{@link SubclassData} fixtures directly into the
 * real {@link ClassRegistry}/{@link SubclassRegistry} singletons — these are simple mutable maps
 * with no freeze/isolation mechanism (unlike {@code PlayerResourceRegistry}), so tests register
 * their own fixtures under test-only identifiers rather than depending on full production class
 * registration (which never runs outside a real mod-initialized environment).
 */
class SelectSubclassHandlerTest {

    private static final Identifier TEST_CLASS_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_class");
    private static final Identifier OTHER_CLASS_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_other_class");
    private static final Identifier TEST_SUBCLASS_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_subclass_a");
    private static final Identifier OTHER_SUBCLASS_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_subclass_b");
    private static final Identifier OTHER_CLASS_SUBCLASS_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_other_class_subclass");
    private static final Identifier WRONG_CLASS_SUBCLASS_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_wrong_class_subclass");
    private static final Identifier UNKNOWN_ID =
            Identifier.fromNamespaceAndPath("totality_test", "select_subclass_handler_unknown");

    private static final int SUBCLASS_UNLOCK_LEVEL = 3;

    @BeforeAll
    static void registerFixtures() {
        ClassRegistry.register(classData(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL));
        ClassRegistry.register(classData(OTHER_CLASS_ID, SUBCLASS_UNLOCK_LEVEL));
        SubclassRegistry.register(subclassData(TEST_SUBCLASS_ID, TEST_CLASS_ID));
        SubclassRegistry.register(subclassData(OTHER_SUBCLASS_ID, TEST_CLASS_ID));
        SubclassRegistry.register(subclassData(OTHER_CLASS_SUBCLASS_ID, OTHER_CLASS_ID));
        SubclassRegistry.register(subclassData(WRONG_CLASS_SUBCLASS_ID, OTHER_CLASS_ID));
    }

    private static ClassData classData(Identifier id, int subclassUnlockLevel) {
        return new ClassData(id, ClassCategory.MARTIAL, id.getPath(), "Test class",
                Dice.D8, Dice.D8, Dice.D8, List.of(), List.of(), List.of(), null,
                Map.of(), List.of(), subclassUnlockLevel);
    }

    private static SubclassData subclassData(Identifier id, Identifier parentClassId) {
        return new SubclassData(id, parentClassId, id.getPath(), "Test subclass", List.of(), List.of());
    }

    @Test
    void anAlreadyOwnedClassReachingItsMilestoneCanSelectAndPersistASubclass() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);

        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID);

        assertTrue(applied);
        assertTrue(comp.hasSubclass(TEST_CLASS_ID));
        assertEquals(TEST_SUBCLASS_ID, comp.getSubclassId(TEST_CLASS_ID));
    }

    @Test
    void aSubclassBelongingToADifferentClassIsRejected() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);

        // WRONG_CLASS_SUBCLASS_ID's parentClassId is OTHER_CLASS_ID, not TEST_CLASS_ID.
        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, WRONG_CLASS_SUBCLASS_ID);

        assertFalse(applied);
        assertFalse(comp.hasSubclass(TEST_CLASS_ID));
    }

    @Test
    void anUnknownClassIsRejected() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);

        boolean applied = SelectSubclassHandler.apply(comp, UNKNOWN_ID, TEST_SUBCLASS_ID);

        assertFalse(applied);
        assertFalse(comp.hasSubclass(TEST_CLASS_ID));
    }

    @Test
    void anUnknownSubclassIsRejected() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);

        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, UNKNOWN_ID);

        assertFalse(applied);
        assertFalse(comp.hasSubclass(TEST_CLASS_ID));
    }

    @Test
    void aClassThePlayerDoesNotOwnIsRejectedEvenIfTheSubclassPairingIsValid() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        // Never called setClassLevel(TEST_CLASS_ID, ...) — the player does not own this class.

        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID);

        assertFalse(applied);
        assertFalse(comp.hasSubclass(TEST_CLASS_ID));
    }

    @Test
    void reachingTheClassOneLevelBelowTheMilestoneIsRejected() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL - 1);

        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID);

        assertFalse(applied);
        assertFalse(comp.hasSubclass(TEST_CLASS_ID));
    }

    @Test
    void aClassLevelAboveTheMilestoneStillAllowsSelection() {
        // A player who leveled past the milestone without ever completing selection (e.g. through
        // further ordinary leveling while the bug this fixes was still present) must still be able
        // to select a subclass afterward — the check is "at least," not "exactly."
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL + 2);

        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID);

        assertTrue(applied);
        assertEquals(TEST_SUBCLASS_ID, comp.getSubclassId(TEST_CLASS_ID));
    }

    @Test
    void anAlreadyOwnedClassCannotReplaceAnExistingSubclass() {
        // The current design treats a subclass choice as permanent — no replacement path exists
        // anywhere in the codebase (ConfirmClassScreen's own "This choice is permanent" warning).
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);
        comp.selectSubclass(TEST_CLASS_ID, TEST_SUBCLASS_ID);

        boolean applied = SelectSubclassHandler.apply(comp, TEST_CLASS_ID, OTHER_SUBCLASS_ID);

        assertFalse(applied, "an already-chosen subclass must not be replaceable through this path");
        assertEquals(TEST_SUBCLASS_ID, comp.getSubclassId(TEST_CLASS_ID), "the original subclass must be untouched");
    }

    @Test
    void rejectionNeverMutatesStateEvenPartially() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        // Deliberately does not own TEST_CLASS_ID, so this call is rejected for ownership.
        SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID);

        assertFalse(comp.hasSubclass(TEST_CLASS_ID));
        assertNull(comp.getSubclassId(TEST_CLASS_ID));
        assertFalse(comp.hasClass(TEST_CLASS_ID), "a rejected call must never grant the class either");
    }

    // ── Per-class subclass migration (2026-09-16): two owned classes must hold independent
    // subclasses, and a subclass choice for one must never affect the other. ─────────────────────

    @Test
    void twoDifferentOwnedClassesCanEachHoldADifferentSubclassSimultaneously() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);
        comp.setClassLevel(OTHER_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);

        assertTrue(SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID));
        assertTrue(SelectSubclassHandler.apply(comp, OTHER_CLASS_ID, OTHER_CLASS_SUBCLASS_ID));

        assertEquals(TEST_SUBCLASS_ID, comp.getSubclassId(TEST_CLASS_ID));
        assertEquals(OTHER_CLASS_SUBCLASS_ID, comp.getSubclassId(OTHER_CLASS_ID));
    }

    @Test
    void selectingASubclassForOneClassDoesNotMakeAnotherOwnedClassReportHasSubclassTrue() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);
        comp.setClassLevel(OTHER_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);

        assertTrue(SelectSubclassHandler.apply(comp, TEST_CLASS_ID, TEST_SUBCLASS_ID));

        assertTrue(comp.hasSubclass(TEST_CLASS_ID));
        assertFalse(comp.hasSubclass(OTHER_CLASS_ID),
                "a subclass chosen for TEST_CLASS_ID must never make OTHER_CLASS_ID report hasSubclass == true");
    }

    @Test
    void aSubclassAlreadyChosenForOneClassDoesNotBlockChoosingASubclassForAnotherOwnedClass() {
        // The exact bug this migration fixes: Barbarian already having Berserker must not prevent
        // choosing a Wizard subclass on the same character.
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(TEST_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);
        comp.setClassLevel(OTHER_CLASS_ID, SUBCLASS_UNLOCK_LEVEL);
        comp.selectSubclass(TEST_CLASS_ID, TEST_SUBCLASS_ID);

        boolean applied = SelectSubclassHandler.apply(comp, OTHER_CLASS_ID, OTHER_CLASS_SUBCLASS_ID);

        assertTrue(applied, "an existing subclass on a DIFFERENT class must never block this class's own selection");
        assertEquals(OTHER_CLASS_SUBCLASS_ID, comp.getSubclassId(OTHER_CLASS_ID));
        assertEquals(TEST_SUBCLASS_ID, comp.getSubclassId(TEST_CLASS_ID), "the first class's subclass must be untouched");
    }
}
