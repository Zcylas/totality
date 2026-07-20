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
        component.recalculate(new int[] {4, 3, 2, 0, 0, 0, 0, 0, 0, 0});

        assertEquals(4, component.getMax(1));
        assertEquals(3, component.getMax(2));
        assertEquals(2, component.getMax(3));
        assertEquals(0, component.getMax(4));
    }

    @Test
    void recalculateClampsUsedToTheNewLowerMaximum() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);
        component.useSlot(1);
        component.useSlot(1); // used = 3

        component.recalculate(new int[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0}); // maximum shrinks below used

        assertEquals(2, component.getUsed(1), "used must clamp down to the new maximum");
        assertEquals(0, component.getRemaining(1));
    }

    @Test
    void useSlotConsumesOneAvailableSlot() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {2, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        assertTrue(component.useSlot(1));

        assertEquals(1, component.getUsed(1));
        assertEquals(1, component.getRemaining(1));
    }

    @Test
    void useSlotRejectsWhenNoneRemain() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {1, 0, 0, 0, 0, 0, 0, 0, 0, 0});
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
        component.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0, 0});
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
        // restoreSome(...) exists on the class but is not wired into RestListener#onRest for
        // RestType.SHORT — Phase 2D does not wire it, and it must not be treated as Pact Magic
        // ownership either (Pact Magic isn't implemented at all yet). This characterizes the
        // current, unchanged behavior.
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        component.useSlot(1);

        component.onRest(null, RestType.SHORT);

        assertEquals(1, component.getUsed(1), "a Short Rest must not restore standard spell slots");
    }

    @Test
    void invalidExternalSpellLevelsReadAsZeroRatherThanThrowing() {
        SpellSlotComponent component = new SpellSlotComponent(null);
        component.recalculate(new int[] {4, 0, 0, 0, 0, 0, 0, 0, 0, 0});

        assertEquals(0, component.getMax(0), "level 0 (cantrip) is out of range for this array");
        assertEquals(0, component.getMax(11), "level 11 exceeds MAX_SPELL_LEVEL");
        assertFalse(component.hasSlot(0));
        assertFalse(component.hasSlot(11));
        assertFalse(component.useSlot(0));
        assertFalse(component.useSlot(11));
    }

    @Test
    void persistenceRoundTripsMaxAndUsedForEveryLevel() {
        SpellSlotComponent original = new SpellSlotComponent(null);
        original.recalculate(new int[] {4, 3, 2, 0, 0, 0, 0, 0, 0, 1});
        original.useSlot(1);
        original.useSlot(10);

        TagValueOutput out = TagValueOutput.createWithoutContext(ProblemReporter.DISCARDING);
        original.writeData(out);

        SpellSlotComponent restored = new SpellSlotComponent(null);
        restored.readData(TagValueInput.create(ProblemReporter.DISCARDING,
                net.minecraft.core.HolderLookup.Provider.create(Stream.of()), out.buildResult()));

        for (int level = 1; level <= SpellSlotComponent.MAX_SPELL_LEVEL; level++) {
            assertEquals(original.getMax(level), restored.getMax(level), "level " + level);
            assertEquals(original.getUsed(level), restored.getUsed(level), "level " + level);
        }
    }

    @Test
    void copyFromPreservesBothMaxAndUsedArrays() {
        SpellSlotComponent source = new SpellSlotComponent(null);
        source.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0, 0});
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
        component.recalculate(new int[] {4, 3, 0, 0, 0, 0, 0, 0, 0, 0});
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
