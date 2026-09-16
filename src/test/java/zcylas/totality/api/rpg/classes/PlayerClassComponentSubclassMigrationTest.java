package zcylas.totality.api.rpg.classes;

import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the per-class subclass storage migration (2026-09-16, see the Class Tab
 * Quick Level-Up implementation report's amended sections): {@link PlayerClassComponent} used to
 * store exactly one global {@code subclassId} for the whole player, which could not represent a
 * multiclass character with a subclass chosen for more than one owned class — a Barbarian subclass
 * choice made {@code hasSubclass()} true globally, incorrectly blocking a later Wizard subclass
 * choice. Storage is now {@code Map<classId, subclassId>}.
 *
 * <p>Covers the storage/query API itself, the NBT persistence round-trip under the new per-class
 * format, and the one-time legacy migration from the old single-{@code "SubclassId"}-key format
 * (inferring the owning class via {@link SubclassRegistry#get}'s {@code parentClassId()}, the same
 * authoritative relationship every other subclass consumer already relies on).
 */
class PlayerClassComponentSubclassMigrationTest {

    private static final Identifier CLASS_A =
            Identifier.fromNamespaceAndPath("totality_test", "subclass_migration_class_a");
    private static final Identifier CLASS_B =
            Identifier.fromNamespaceAndPath("totality_test", "subclass_migration_class_b");
    private static final Identifier SUBCLASS_A =
            Identifier.fromNamespaceAndPath("totality_test", "subclass_migration_subclass_a");
    private static final Identifier SUBCLASS_B =
            Identifier.fromNamespaceAndPath("totality_test", "subclass_migration_subclass_b");
    private static final Identifier UNKNOWN_LEGACY_SUBCLASS =
            Identifier.fromNamespaceAndPath("totality_test", "subclass_migration_never_registered");

    @BeforeAll
    static void registerFixtures() {
        ClassRegistry.register(classData(CLASS_A));
        ClassRegistry.register(classData(CLASS_B));
        SubclassRegistry.register(subclassData(SUBCLASS_A, CLASS_A));
        SubclassRegistry.register(subclassData(SUBCLASS_B, CLASS_B));
    }

    private static ClassData classData(Identifier id) {
        return new ClassData(id, ClassCategory.MARTIAL, id.getPath(), "Test class",
                Dice.D8, Dice.D8, Dice.D8, List.of(), List.of(), List.of(), null,
                Map.of(), List.of(), 3);
    }

    private static SubclassData subclassData(Identifier id, Identifier parentClassId) {
        return new SubclassData(id, parentClassId, id.getPath(), "Test subclass", List.of(), List.of());
    }

    private static HolderLookup.Provider emptyRegistries() {
        return HolderLookup.Provider.create(Stream.of());
    }

    // ── Storage/query API ────────────────────────────────────────────────────────────────────

    @Test
    void oneClassCanSelectASubclass() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(CLASS_A, 3);
        comp.selectSubclass(CLASS_A, SUBCLASS_A);

        assertTrue(comp.hasSubclass(CLASS_A));
        assertEquals(SUBCLASS_A, comp.getSubclassId(CLASS_A));
    }

    @Test
    void twoOwnedClassesHoldIndependentSubclasses() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(CLASS_A, 3);
        comp.setClassLevel(CLASS_B, 3);
        comp.selectSubclass(CLASS_A, SUBCLASS_A);
        comp.selectSubclass(CLASS_B, SUBCLASS_B);

        assertEquals(SUBCLASS_A, comp.getSubclassId(CLASS_A));
        assertEquals(SUBCLASS_B, comp.getSubclassId(CLASS_B));
        assertEquals(Map.of(CLASS_A, SUBCLASS_A, CLASS_B, SUBCLASS_B), comp.getAllSubclassIds());
    }

    @Test
    void selectingASubclassForOneClassNeverAffectsAnotherClass() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(CLASS_A, 3);
        comp.setClassLevel(CLASS_B, 3);
        comp.selectSubclass(CLASS_A, SUBCLASS_A);

        assertTrue(comp.hasSubclass(CLASS_A));
        assertFalse(comp.hasSubclass(CLASS_B));
        assertNull(comp.getSubclassId(CLASS_B));
    }

    @Test
    void selectClassResetsAllSubclassEntriesForAFreshFirstTimeSelection() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(CLASS_A, 3);
        comp.selectSubclass(CLASS_A, SUBCLASS_A);

        comp.selectClass(CLASS_B, 1);

        assertFalse(comp.hasSubclass(CLASS_A), "selecting a brand-new class must wipe every prior subclass entry");
        assertTrue(comp.getAllSubclassIds().isEmpty());
    }

    @Test
    void resetClassClearsAllSubclassEntries() {
        PlayerClassComponent comp = new PlayerClassComponent(null);
        comp.setClassLevel(CLASS_A, 3);
        comp.setClassLevel(CLASS_B, 3);
        comp.selectSubclass(CLASS_A, SUBCLASS_A);
        comp.selectSubclass(CLASS_B, SUBCLASS_B);

        comp.resetClass();

        assertTrue(comp.getAllSubclassIds().isEmpty());
    }

    @Test
    void copyFromPreservesIndependentSubclassesPerClass() {
        PlayerClassComponent source = new PlayerClassComponent(null);
        source.setClassLevel(CLASS_A, 3);
        source.setClassLevel(CLASS_B, 3);
        source.selectSubclass(CLASS_A, SUBCLASS_A);
        source.selectSubclass(CLASS_B, SUBCLASS_B);

        PlayerClassComponent target = new PlayerClassComponent(null);
        target.copyFrom(source, emptyRegistries());

        assertEquals(SUBCLASS_A, target.getSubclassId(CLASS_A));
        assertEquals(SUBCLASS_B, target.getSubclassId(CLASS_B));
    }

    // ── NBT persistence: new per-class format ────────────────────────────────────────────────

    @Test
    void newSaveLoadRoundTripPreservesMultipleClassSubclassMappings() {
        PlayerClassComponent original = new PlayerClassComponent(null);
        original.setClassLevel(CLASS_A, 3);
        original.setClassLevel(CLASS_B, 5);
        original.selectSubclass(CLASS_A, SUBCLASS_A);
        original.selectSubclass(CLASS_B, SUBCLASS_B);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);

        PlayerClassComponent restored = new PlayerClassComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertEquals(SUBCLASS_A, restored.getSubclassId(CLASS_A));
        assertEquals(SUBCLASS_B, restored.getSubclassId(CLASS_B));
        assertEquals(3, restored.getClassLevel(CLASS_A));
        assertEquals(5, restored.getClassLevel(CLASS_B));
    }

    @Test
    void newFormatRoundTripWithNoSubclassesYetIsEmptyNotLegacyMigrated() {
        // SubclassCount == 0 (a real, already-per-class save with nothing chosen yet) must not be
        // confused with "this save predates the per-class format" (SubclassCount absent).
        PlayerClassComponent original = new PlayerClassComponent(null);
        original.setClassLevel(CLASS_A, 1);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);

        PlayerClassComponent restored = new PlayerClassComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertTrue(restored.getAllSubclassIds().isEmpty());
    }

    // ── Legacy migration from the old single-subclass format ────────────────────────────────

    @Test
    void legacySingleSubclassSaveMigratesIntoTheCorrectClassKeyedEntry() {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        out.putInt("ClassCount", 1);
        out.putString("ClassId_0", CLASS_A.toString());
        out.putInt("ClassLvl_0", 3);
        out.putString("SubclassId", SUBCLASS_A.toString()); // old format — no SubclassCount key at all

        PlayerClassComponent restored = new PlayerClassComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertEquals(SUBCLASS_A, restored.getSubclassId(CLASS_A),
                "the legacy subclass must be inferred onto its real owning class via SubclassRegistry");
        assertTrue(restored.getAllSubclassIds().size() == 1);
    }

    @Test
    void anUnknownOrMalformedLegacySubclassIdFailsSafelyWithoutCrashingOrGuessing() {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        out.putString("SubclassId", UNKNOWN_LEGACY_SUBCLASS.toString());

        PlayerClassComponent restored = new PlayerClassComponent(null);
        assertDoesNotThrow(() ->
                restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult())));

        assertTrue(restored.getAllSubclassIds().isEmpty(),
                "an unresolvable legacy subclass id must migrate nothing rather than guessing an owner");
    }

    @Test
    void legacyFormatWithNoSubclassAtAllMigratesToAnEmptyMap() {
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        out.putInt("ClassCount", 1);
        out.putString("ClassId_0", CLASS_A.toString());
        out.putInt("ClassLvl_0", 1);
        // No "SubclassId" key at all — a legacy character who never picked a subclass.

        PlayerClassComponent restored = new PlayerClassComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), out.buildResult()));

        assertTrue(restored.getAllSubclassIds().isEmpty());
    }

    @Test
    void repeatedLoadsOfANewFormatSaveNeverReconsultTheLegacyKeyOrDuplicateState() {
        // Simulate a character already migrated and saved once under the new format — writeData
        // never emits "SubclassId" again, so a repeated load only ever takes the SubclassCount
        // branch, and doing so twice in a row must be perfectly idempotent.
        PlayerClassComponent original = new PlayerClassComponent(null);
        original.setClassLevel(CLASS_A, 3);
        original.selectSubclass(CLASS_A, SUBCLASS_A);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);
        var saved = out.buildResult();

        assertNull(saved.get("SubclassId"), "the new format must never write the legacy key again");

        PlayerClassComponent restored = new PlayerClassComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), saved));
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING, emptyRegistries(), saved));

        assertEquals(1, restored.getAllSubclassIds().size(), "a repeated load must not duplicate the entry");
        assertEquals(SUBCLASS_A, restored.getSubclassId(CLASS_A));
    }

    // ── Sync packet round-trip ────────────────────────────────────────────────────────────────

    @Test
    void syncPacketRoundTripsMultipleClassSubclassMappings() {
        PlayerClassComponent original = new PlayerClassComponent(null);
        original.setClassLevel(CLASS_A, 3);
        original.setClassLevel(CLASS_B, 5);
        original.selectSubclass(CLASS_A, SUBCLASS_A);
        original.selectSubclass(CLASS_B, SUBCLASS_B);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), null);
        original.writeSyncPacket(buf, null);

        PlayerClassComponent restored = new PlayerClassComponent(null);
        restored.applySyncPacket(buf);

        assertEquals(SUBCLASS_A, restored.getSubclassId(CLASS_A));
        assertEquals(SUBCLASS_B, restored.getSubclassId(CLASS_B));
        assertEquals(0, buf.readableBytes(), "the entire sync payload must be consumed");
    }
}
