package zcylas.totality.api.magic.spell;

import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.rest.RestType;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Characterization coverage for {@link SpellSlotComponent} — the audit found essentially no
 * existing automated coverage for this system. Constructed with a {@code null} {@code ServerPlayer}
 * throughout, matching the established precedent for legacy components under test (e.g. {@code
 * PlayerResourceComponent(null)} in {@code ManaResourceAdapterTest}) — {@code sync()} is guarded by
 * a {@code player != null} check, so this never dereferences the null player.
 */
class SpellSlotComponentTest {

    @Test
    void freshComponentDefaultsToAllZero() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        for (int level = 1; level <= SpellSlotComponent.MAX_SPELL_LEVEL; level++) {
            assertEquals(0, component.getMax(level), "level " + level);
            assertEquals(0, component.getUsed(level), "level " + level);
            assertEquals(0, component.getRemaining(level), "level " + level);
            assertFalse(component.hasSlot(level), "level " + level);
        }
    }

    @Test
    void recalculateStoresNewMaxima() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 2, 0, 0, 0, 0, 0, 0});

        assertEquals(4, component.getMax(1));
        assertEquals(3, component.getMax(2));
        assertEquals(2, component.getMax(3));
        assertEquals(0, component.getMax(4));
    }

    @Test
    void recalculateClampsUsedToTheNewLowerMaximum() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);
        component.useSlot(1);
        component.useSlot(1); // used = 3

        component.recalculate(new int[] {2, 0, 0, 0, 0, 0, 0, 0, 0}); // maximum shrinks below used

        assertEquals(2, component.getUsed(1), "used must clamp down to the new maximum");
        assertEquals(0, component.getRemaining(1));
    }

    @Test
    void useSlotConsumesOneAvailableSlot() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {2, 0, 0, 0, 0, 0, 0, 0, 0});

        assertTrue(component.useSlot(1));

        assertEquals(1, component.getUsed(1));
        assertEquals(1, component.getRemaining(1));
    }

    @Test
    void useSlotRejectsWhenNoneRemain() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {1, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);

        assertFalse(component.useSlot(1), "no slot remains at level 1");
        assertEquals(1, component.getUsed(1), "a rejected consumption must not increment used");
    }

    @Test
    void useSlotRejectsAtANonCasterLevel() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        assertFalse(component.useSlot(5), "no maximum was ever set for level 5");
    }

    @Test
    void longRestFullyRestoresEveryLevel() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);
        component.useSlot(1);
        component.useSlot(2);

        component.onRest(null, RestType.LONG);

        assertEquals(0, component.getUsed(1));
        assertEquals(0, component.getUsed(2));
        assertEquals(4, component.getRemaining(1));
        assertEquals(3, component.getRemaining(2));
    }

    @Test
    void shortRestDoesNotRestoreStandardSlots() {
        // Standard Spell Slots never restore on Short Rest — only Warlock Pact Magic would (Phase 7
        // scope, not implemented). This characterizes the current, unchanged behavior; the dead
        // restoreSome(...) method this once documented was removed in Phase 6 (2026-09-16) since it
        // was never wired into RestListener#onRest for RestType.SHORT in the first place.
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);

        component.onRest(null, RestType.SHORT);

        assertEquals(1, component.getUsed(1), "a Short Rest must not restore standard spell slots");
    }

    @Test
    void invalidExternalSpellLevelsReadAsZeroRatherThanThrowing() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0});

        assertEquals(0, component.getMax(0), "level 0 (cantrip) is out of range for this array");
        assertEquals(0, component.getMax(10), "level 10 exceeds MAX_SPELL_LEVEL — there is no ordinary tier 10 (Phase 6)");
        assertEquals(0, component.getMax(11), "level 11 exceeds MAX_SPELL_LEVEL");
        assertFalse(component.hasSlot(0));
        assertFalse(component.hasSlot(10));
        assertFalse(component.hasSlot(11));
        assertFalse(component.useSlot(0));
        assertFalse(component.useSlot(10));
        assertFalse(component.useSlot(11));
    }

    @Test
    void persistenceRoundTripsMaxAndUsedForEveryLevel() {
        SpellSlotComponent original = new SpellSlotComponent(null);
        original.recalculate(new int[] {4, 3, 2, 0, 0, 0, 0, 0, 1});
        original.useSlot(1);
        original.useSlot(9);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);

        SpellSlotComponent restored = new SpellSlotComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING,
                net.minecraft.core.HolderLookup.Provider.create(Stream.of()), out.buildResult()));

        for (int level = 1; level <= SpellSlotComponent.MAX_SPELL_LEVEL; level++) {
            assertEquals(original.getMax(level), restored.getMax(level), "level " + level);
            assertEquals(original.getUsed(level), restored.getUsed(level), "level " + level);
        }
        assertEquals(0, restored.getMax(10), "there is no ordinary tier-10 slot to persist");
    }

    // ── Correction pass (2026-09-16, Finding 3) ─────────────────────────────────────────────────
    // The round-trip test above only ever proves the CURRENT writer/reader agree with each other
    // (writeData never emits a 10th entry post-Phase-6, so the round trip never actually contains
    // historical tier-10 data). This test instead hand-builds the raw NBT shape a genuinely OLD,
    // pre-Phase-6 save would have persisted — including its historical "max_9"/"used_9" keys (the
    // zero-based index that represented the old ordinary 10th-level slot) — and reads it through the
    // real, current SpellSlotComponent#readData, proving those keys are structurally never consulted
    // rather than merely "happening to be absent from a fresh write."

    @Test
    void readDataIgnoresHistoricalTierTenKeysFromAGenuinelyOldPreMigrationSave() {
        // Hand-built exactly like writeData would have written it under the pre-Phase-6 (10-slot)
        // format: valid tiers 1-9 (keys max_0..max_8/used_0..used_8) plus a historical, spent 10th
        // slot (max_9=1, used_9=1) that only ever existed under the old MAX_SPELL_LEVEL=10 shape.
        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        int[] legacyMax  = {4, 3, 2, 0, 0, 0, 0, 0, 1};
        int[] legacyUsed = {1, 0, 0, 0, 0, 0, 0, 0, 0};
        for (int i = 0; i < legacyMax.length; i++) {
            out.putInt("max_" + i, legacyMax[i]);
            out.putInt("used_" + i, legacyUsed[i]);
        }
        out.putInt("max_9", 1);  // historical ordinary tier-10 maximum (index 9 = level 10)
        out.putInt("used_9", 1); // historical ordinary tier-10 slot, already spent

        SpellSlotComponent restored = new SpellSlotComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING,
                net.minecraft.core.HolderLookup.Provider.create(Stream.of()), out.buildResult()));

        for (int level = 1; level <= SpellSlotComponent.MAX_SPELL_LEVEL; level++) {
            assertEquals(legacyMax[level - 1], restored.getMax(level), "level " + level);
            assertEquals(legacyUsed[level - 1], restored.getUsed(level), "level " + level);
        }
        assertEquals(0, restored.getMax(10), "historical max_9 must never surface as level 10 — MAX_SPELL_LEVEL=9 has no index 9 to read it into");
        assertEquals(0, restored.getUsed(10), "historical used_9 must never surface as level 10");
        assertFalse(restored.hasSlot(10), "there is no ordinary tier-10 slot, spent or otherwise");
    }

    @Test
    void copyFromPreservesBothMaxAndUsedArrays() {
        SpellSlotComponent source = new SpellSlotComponent(null);
        source.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0});
        source.useSlot(1);

        SpellSlotComponent target = new SpellSlotComponent(null);
        target.copyFrom(source, net.minecraft.core.HolderLookup.Provider.create(Stream.of()));

        assertEquals(4, target.getMax(1));
        assertEquals(1, target.getUsed(1));
        assertEquals(3, target.getMax(2));
    }

    @Test
    void queryingViaMaybeGetPatternNeverMutatesTheComponent() {
        // Characterizes the exact reads StandardSpellSlotsResourceAdapter performs (getMax/getUsed
        // for every level) as genuinely side-effect-free — repeated reads must not change state.
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);

        for (int i = 0; i < 5; i++) {
            for (int level = 1; level <= SpellSlotComponent.MAX_SPELL_LEVEL; level++) {
                component.getMax(level);
                component.getUsed(level);
                component.getRemaining(level);
                component.hasSlot(level);
            }
        }

        assertEquals(4, component.getMax(1));
        assertEquals(1, component.getUsed(1));
        assertEquals(3, component.getMax(2));
        assertEquals(0, component.getUsed(2));
    }
}
