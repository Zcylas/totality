package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceSource;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers Phase 3B-2B test-plan items 1-6: the generic façade result to parity summary mapper. */
class ClientGenericParitySummaryMapperTest {

    private static final Identifier MANA = Identifier.fromNamespaceAndPath("totality", "mana");
    private static final Identifier SPELL_SLOTS = Identifier.fromNamespaceAndPath("totality", "spell_slots");
    private static final Identifier RAGE = Identifier.fromNamespaceAndPath("totality", "rage");

    @Test
    void scalarMappingPreservesAllQuantities() {
        var result = new ClientResourceQueryResult.Scalar(
                MANA, 42, 100, 7, 1, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        var summary = (ClientResourceParitySummary.Scalar) mapped.summary();
        assertEquals(42, summary.currentUnits());
        assertEquals(100, summary.maximumUnits());
        assertEquals(7, summary.overflowUnits());
        assertEquals(1, summary.unitScale());
    }

    @Test
    void scalarMappingPreservesFreshTrust() {
        var result = new ClientResourceQueryResult.Scalar(
                MANA, 42, 100, 0, 1, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        assertFalse(mapped.pendingResync());
    }

    @Test
    void scalarMappingPreservesPendingResyncTrust() {
        var result = new ClientResourceQueryResult.Scalar(
                MANA, 42, 100, 0, 1, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.PENDING_RESYNC);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        assertTrue(mapped.pendingResync());
    }

    @Test
    void partitionedMappingPreservesAllPartitions() {
        var result = ClientResourceQueryResult.Partitioned.of(
                SPELL_SLOTS,
                List.of(
                        new ClientResourceQueryResult.Partitioned.Partition(1, 3, 4, 0),
                        new ClientResourceQueryResult.Partitioned.Partition(2, 0, 0, 0)),
                1, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.FRESH);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        var summary = (ClientResourceParitySummary.Partitioned) mapped.summary();
        assertEquals(2, summary.partitions().size());
        assertEquals(3, summary.partition(1).orElseThrow().currentUnits());
        assertEquals(4, summary.partition(1).orElseThrow().maximumUnits());
        assertEquals(0, summary.partition(2).orElseThrow().currentUnits());
    }

    @Test
    void partitionedMappingPreservesPendingResyncTrust() {
        var result = ClientResourceQueryResult.Partitioned.of(
                SPELL_SLOTS, List.of(new ClientResourceQueryResult.Partitioned.Partition(1, 0, 0, 0)),
                1, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, ClientResourceTrust.PENDING_RESYNC);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        assertTrue(mapped.pendingResync());
    }

    @Test
    void unavailableMappingPreservesExactReason() {
        var result = ClientResourceQueryResult.unavailable(RAGE, ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        var summary = (ClientResourceParitySummary.Unavailable) mapped.summary();
        assertEquals(ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER, summary.reason());
    }

    @Test
    void mapperNeverFabricatesValuesForUnavailableResults() {
        var result = ClientResourceQueryResult.unavailable(MANA, ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET);
        var mapped = ClientGenericParitySummaryMapper.map(result);
        assertTrue(mapped.summary() instanceof ClientResourceParitySummary.Unavailable);
        // Unavailable has no numeric fields at all — structurally cannot carry a fabricated value.
        assertFalse(mapped.pendingResync());
    }
}
