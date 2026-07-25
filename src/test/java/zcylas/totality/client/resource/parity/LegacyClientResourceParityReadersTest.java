package zcylas.totality.client.resource.parity;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.magic.spell.ClientSpellSlotManager;
import zcylas.totality.api.rpg.classes.ClientClassManager;
import zcylas.totality.api.rpg.classes.TotalityClasses;
import zcylas.totality.api.rpg.resources.client.parity.ClientResourceParitySummary;
import zcylas.totality.networking.mana.ClientManaManager;
import zcylas.totality.networking.stamina.ClientStaminaManager;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers Phase 3B-2B test-plan items 7-11 and 14-16: the legacy Mana/Stamina/spell-slot readers
 * and the Rage-expectation derivation. These specific methods never call {@code
 * Minecraft.getInstance()} — {@code ClientManaManager}/{@code ClientStaminaManager}/{@code
 * ClientSpellSlotManager}/{@code ClientClassManager} are all plain static holders with zero
 * Minecraft dependency of their own — so they are directly testable here despite this class as a
 * whole being {@code @Environment(EnvType.CLIENT)}.
 *
 * <p>{@code rageSummary()} (items 12-13, requiring a real local player and attached {@code
 * PlayerChargesComponent}) is deliberately <b>not</b> exercised here — it is confirmed only by
 * direct source inspection (cited in the implementation report) and the later manual validation
 * checklist, consistent with the established precedent that a real {@code Minecraft.getInstance()}
 * read is untestable without a running client.
 *
 * <p><b>External-review correction (2026-07-25):</b> every test method here mutates one or more of
 * the four shared static legacy holders. {@link #resetStaticLegacyState()} restores a neutral
 * baseline after every test (via {@code @AfterEach}, which JUnit runs even when a test's assertion
 * fails), so no test's static mutation can leak into another test regardless of execution order.
 */
class LegacyClientResourceParityReadersTest {

    @AfterEach
    void resetStaticLegacyState() {
        ClientManaManager.sync(100, 100);
        ClientStaminaManager.sync(100, 100);
        ClientSpellSlotManager.apply(new int[10], new int[10]);
        ClientClassManager.apply(Map.of(), null, null);
    }

    @Test
    void manaLegacySnapshotUsesCurrentMaxOverflowZeroScaleOne() {
        ClientManaManager.sync(37, 120);
        var summary = (ClientResourceParitySummary.Scalar) LegacyClientResourceParityReaders.INSTANCE.manaSummary();
        assertEquals(37, summary.currentUnits());
        assertEquals(120, summary.maximumUnits());
        assertEquals(0, summary.overflowUnits());
        assertEquals(1, summary.unitScale());
    }

    @Test
    void staminaLegacySnapshotUsesCurrentMaxOverflowZeroScaleOne() {
        ClientStaminaManager.sync(58, 130);
        var summary = (ClientResourceParitySummary.Scalar) LegacyClientResourceParityReaders.INSTANCE.staminaSummary();
        assertEquals(58, summary.currentUnits());
        assertEquals(130, summary.maximumUnits());
        assertEquals(0, summary.overflowUnits());
        assertEquals(1, summary.unitScale());
    }

    @Test
    void spellSlotLegacySnapshotContainsExactlyLevelsOneThroughTen() {
        int[] max = new int[10];
        int[] used = new int[10];
        for (int i = 0; i < 10; i++) {
            max[i] = i + 1;
            used[i] = 0;
        }
        ClientSpellSlotManager.apply(max, used);

        var summary = (ClientResourceParitySummary.Partitioned) LegacyClientResourceParityReaders.INSTANCE.spellSlotSummary();
        assertEquals(10, summary.partitions().size());
        for (int level = 1; level <= 10; level++) {
            assertTrue(summary.partition(level).isPresent(), "missing level " + level);
        }
    }

    @Test
    void spellSlotCurrentEqualsRemainingNotUsed() {
        int[] max = new int[10];
        int[] used = new int[10];
        max[2] = 5;  // level 3
        used[2] = 2; // remaining should be 3, never the raw used count (2)
        ClientSpellSlotManager.apply(max, used);

        var summary = (ClientResourceParitySummary.Partitioned) LegacyClientResourceParityReaders.INSTANCE.spellSlotSummary();
        var level3 = summary.partition(3).orElseThrow();
        assertEquals(3, level3.currentUnits());
        assertEquals(5, level3.maximumUnits());
    }

    @Test
    void spellSlotPartitionsAreAscendingAndImmutable() {
        ClientSpellSlotManager.apply(new int[10], new int[10]);
        var summary = (ClientResourceParitySummary.Partitioned) LegacyClientResourceParityReaders.INSTANCE.spellSlotSummary();
        assertEquals(java.util.List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), java.util.List.copyOf(summary.partitions().keySet()));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> summary.partitions().put(11, new ClientResourceParitySummary.Partitioned.Partition(11, 0, 0, 0)));
    }

    @Test
    void rageExpectedTrueForBarbarian() {
        ClientClassManager.apply(Map.of(TotalityClasses.BARBARIAN_ID, 1), null, null);
        assertTrue(LegacyClientResourceParityReaders.INSTANCE.rageExpectedForPlayer());
    }

    @Test
    void rageExpectedFalseForAnotherClass() {
        var otherClassId = net.minecraft.resources.Identifier.fromNamespaceAndPath("totality", "wizard");
        ClientClassManager.apply(Map.of(otherClassId, 1), null, null);
        assertFalse(LegacyClientResourceParityReaders.INSTANCE.rageExpectedForPlayer());
    }

    @Test
    void rageExpectedFalseWhenNoClassIsPresent() {
        ClientClassManager.apply(Map.of(), null, null);
        assertFalse(LegacyClientResourceParityReaders.INSTANCE.rageExpectedForPlayer());
    }
}
