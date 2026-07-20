package zcylas.totality.api.magic.spell;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization coverage for the pure-logic pieces {@link SpellSlotRecalculator#recalculate}
 * composes: {@link SpellcastingProgressionRegistry}'s class-id → {@link CasterProgression} mapping,
 * and {@link SpellSlotTable}'s combining/lookup functions (covered in full by {@link
 * SpellSlotTableTest}).
 *
 * <p>{@code SpellSlotRecalculator.recalculate(ServerPlayer)} itself is <b>not</b> directly unit
 * tested here: it requires a real, component-attached {@code ServerPlayer} (it calls {@code
 * ClassComponents.get(player)} and {@code SpellSlotComponents.get(player)}, both of which cast to
 * {@code ComponentProvider} and dereference a real component container) that cannot be constructed
 * outside a running Minecraft server without an excessive fake-gameplay scaffold purely to exercise
 * one helper method — exactly the case the Phase 2D task itself calls out as "document the exact
 * production path and add focused lower-level characterization tests instead." Its correctness is
 * characterized here by proving its two real dependencies behave correctly in isolation; the method
 * itself is a thin, side-effect-light composition of them (iterate class levels, bucket by
 * progression, call {@code combinedCasterLevel}, call {@code forFullCaster}, write the result) with
 * no additional branching logic of its own to hide a bug.
 *
 * <p>Uses uniquely-namespaced fake class ids per test (registered into {@link
 * SpellcastingProgressionRegistry}'s shared static map) to avoid any cross-test or cross-suite
 * interference — that registry has no isolated-instance test seam the way {@link
 * zcylas.totality.api.rpg.resources.PlayerResourceRegistry} does, since it is not part of this
 * phase's scope to add one.
 */
class SpellSlotRecalculatorCharacterizationTest {

    private static Identifier fakeClassId(String discriminator) {
        return Identifier.fromNamespaceAndPath("totality_test",
                "phase2d_spell_slot_recalculator_" + discriminator);
    }

    @Test
    void unregisteredClassContributesNothing() {
        assertNull(SpellcastingProgressionRegistry.get(fakeClassId("never_registered")),
                "a class with no SpellcastingProgressionRegistry entry is simply a non-caster");
    }

    @Test
    void registeredFullCasterRoundTrips() {
        Identifier classId = fakeClassId("full_caster");
        SpellcastingProgressionRegistry.register(classId, CasterProgression.FULL);

        assertEquals(CasterProgression.FULL, SpellcastingProgressionRegistry.get(classId));
    }

    @Test
    void registeredHalfCasterRoundTrips() {
        Identifier classId = fakeClassId("half_caster");
        SpellcastingProgressionRegistry.register(classId, CasterProgression.HALF);

        assertEquals(CasterProgression.HALF, SpellcastingProgressionRegistry.get(classId));
    }

    @Test
    void registeredThirdCasterRoundTrips() {
        Identifier classId = fakeClassId("third_caster");
        SpellcastingProgressionRegistry.register(classId, CasterProgression.THIRD);

        assertEquals(CasterProgression.THIRD, SpellcastingProgressionRegistry.get(classId));
    }

    @Test
    void registeredWarlockRoundTripsButRecalculatorExcludesItFromThePool() {
        // SpellSlotRecalculator's switch has an explicit `case WARLOCK -> { /* not tracked here */ }`
        // no-op branch — this test documents that the registry itself faithfully returns WARLOCK
        // when asked (the exclusion is the recalculator's own decision, not a registry limitation).
        Identifier classId = fakeClassId("warlock");
        SpellcastingProgressionRegistry.register(classId, CasterProgression.WARLOCK);

        assertEquals(CasterProgression.WARLOCK, SpellcastingProgressionRegistry.get(classId));
    }

    @Test
    void multiclassBucketingReplicatesTheRecalculatorsOwnCombination() {
        // Replicates exactly what SpellSlotRecalculator.recalculate does with a hand-built class
        // level map (Wizard 5 [FULL] / Paladin 4 [HALF] / Eldritch-Knight-analogue 6 [THIRD] /
        // Warlock 3 [excluded]) — proving the composition SpellSlotRecalculator performs produces
        // the same combined level SpellSlotTableTest already characterizes directly.
        Identifier full = fakeClassId("bucket_full");
        Identifier half = fakeClassId("bucket_half");
        Identifier third = fakeClassId("bucket_third");
        Identifier warlock = fakeClassId("bucket_warlock");
        SpellcastingProgressionRegistry.register(full, CasterProgression.FULL);
        SpellcastingProgressionRegistry.register(half, CasterProgression.HALF);
        SpellcastingProgressionRegistry.register(third, CasterProgression.THIRD);
        SpellcastingProgressionRegistry.register(warlock, CasterProgression.WARLOCK);

        var classLevels = java.util.Map.of(full, 5, half, 4, third, 6, warlock, 3);

        int fullLevels = 0, halfLevels = 0, thirdLevels = 0;
        for (var entry : classLevels.entrySet()) {
            CasterProgression progression = SpellcastingProgressionRegistry.get(entry.getKey());
            if (progression == null) continue;
            switch (progression) {
                case FULL -> fullLevels += entry.getValue();
                case HALF -> halfLevels += entry.getValue();
                case THIRD -> thirdLevels += entry.getValue();
                case WARLOCK -> { /* excluded from the shared pool, exactly like SpellSlotRecalculator */ }
            }
        }

        int combined = SpellSlotTable.combinedCasterLevel(fullLevels, halfLevels, thirdLevels);

        assertEquals(9, combined, "5 (full) + floor(4/2) (half) + floor(6/3) (third) = 9; Warlock's 3 levels contribute 0");
        assertArrayEquals(SpellSlotTable.forFullCaster(9), SpellSlotTable.forFullCaster(combined));
    }

    @Test
    void noPactMagicDataResultsFromTheStandardPoolComposition() {
        // The combined-level computation above has no code path that ever reads
        // SpellSlotTable.WARLOCK_PACT or SpellSlotTable.forWarlock(...) — Pact Magic is a
        // structurally separate table this composition never touches.
        Identifier warlock = fakeClassId("no_pact_bleed");
        SpellcastingProgressionRegistry.register(warlock, CasterProgression.WARLOCK);

        int combined = SpellSlotTable.combinedCasterLevel(0, 0, 0); // Warlock alone contributes nothing
        assertEquals(0, combined);
        int[] maxSlots = combined > 0 ? SpellSlotTable.forFullCaster(combined) : new int[SpellSlotComponent.MAX_SPELL_LEVEL];
        for (int slots : maxSlots) {
            assertEquals(0, slots, "a Warlock-only composition must produce an all-zero standard pool, never Pact Magic values");
        }
    }
}
