package zcylas.totality.api.rpg.resources.client.presentation;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.PlayerResourceRegistry;
import zcylas.totality.api.rpg.resources.TestResourceBootstrap;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;
import zcylas.totality.api.rpg.resources.client.ClientResourceReaderRegistry;
import zcylas.totality.api.rpg.resources.client.ClientResourceService;
import zcylas.totality.api.rpg.resources.client.ClientResourceSource;
import zcylas.totality.api.rpg.resources.client.ClientResourceTrust;
import zcylas.totality.api.rpg.resources.client.ClientResourceUnavailableReason;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 3C focused tests for {@link ClientResourcePresentationResolver} — the one shared
 * scalar/partitioned presentation resolution + legacy-fallback helper every migrated Phase 3C
 * consumer (HUD, ClassTab, SpellRadialScreen, OverviewTab) routes through. Test numbering below
 * follows the canonical Phase 3C task's "CORE RESOLUTION" (1-8) and "SPELL SLOTS" (22-30)
 * numbering where a test maps directly onto one of those items; Rage-specific extensions are
 * appended at the end, unnumbered.
 *
 * <p>Uses a synthetic {@link zcylas.totality.api.rpg.resources.client.ClientResourceReader} that
 * returns a pre-built {@link ClientResourceQueryResult} directly (rather than driving a full
 * Phase 3A wire-sync simulation like {@code ClientResourceServiceTest} does) since this resolver's
 * own contract begins at "a {@link ClientResourceService} query already returned some result" —
 * the wire-sync-to-query-result path itself is already covered by
 * {@code GenericSyncClientResourceReaderTest}/{@code ClientResourceServiceTest} and is out of
 * scope here.
 */
class ClientResourcePresentationResolverTest {

    private static ClientResourceService serviceReturning(Identifier resourceId, ClientResourceQueryResult result) {
        TestResourceBootstrap.ensureProductionResourcesRegistered();
        ClientResourceService service =
                new ClientResourceService(PlayerResourceRegistry.INSTANCE, new ClientResourceReaderRegistry());
        service.registerReader(resourceId, definition -> result);
        return service;
    }

    private static ClientResourceQueryResult.Scalar scalar(
            Identifier id, long current, long max, long unitScale, ClientResourceTrust trust) {
        return new ClientResourceQueryResult.Scalar(
                id, current, max, 0, unitScale, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, trust);
    }

    private static ClientResourceQueryResult.Partitioned partitioned(
            Identifier id, long unitScale, ClientResourceTrust trust,
            ClientResourceQueryResult.Partitioned.Partition... partitions) {
        return ClientResourceQueryResult.Partitioned.of(
                id, List.of(partitions), unitScale, ClientResourceSource.GENERIC_SYNCHRONIZED_VIEW, trust);
    }

    // ── CORE RESOLUTION ──────────────────────────────────────────────────────────────────────

    // 1. Fresh Generic scalar value is preferred over legacy fallback.
    @Test
    void freshGenericScalarIsPreferredOverLegacyFallback() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.MANA,
                scalar(PlayerResourceIds.MANA, 40, 100, 1, ClientResourceTrust.FRESH));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.MANA, () -> 999L, () -> 999L);
        assertEquals(40L, result.current());
        assertEquals(100L, result.maximum());
        assertTrue(result.generic());
    }

    // 2. PENDING_RESYNC Generic scalar remains readable and preferred.
    @Test
    void pendingResyncGenericScalarRemainsReadableAndPreferred() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.MANA,
                scalar(PlayerResourceIds.MANA, 40, 100, 1, ClientResourceTrust.PENDING_RESYNC));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.MANA, () -> 999L, () -> 999L);
        assertEquals(40L, result.current());
        assertEquals(100L, result.maximum());
        assertTrue(result.generic(), "PENDING_RESYNC must still be preferred over the legacy fallback, never blanked");
    }

    // 3. Unavailable Generic scalar uses fallback.
    @Test
    void unavailableGenericScalarUsesFallback() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.MANA,
                ClientResourceQueryResult.unavailable(PlayerResourceIds.MANA, ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.MANA, () -> 12L, () -> 34L);
        assertEquals(12L, result.current());
        assertEquals(34L, result.maximum());
        assertFalse(result.generic());
    }

    // 4. Unavailable never becomes fabricated zero.
    @Test
    void unavailableNeverBecomesFabricatedZero() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.RAGE,
                ClientResourceQueryResult.unavailable(PlayerResourceIds.RAGE, ClientResourceUnavailableReason.NOT_AVAILABLE_TO_PLAYER));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.RAGE, () -> 3L, () -> 6L);
        assertEquals(3L, result.current(), "the resolver must defer to the caller's real legacy value, never invent a 0");
        assertEquals(6L, result.maximum());
    }

    // 5. Current/max values remain correctly ordered.
    @Test
    void currentMaxOrderingIsPreserved() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.STAMINA,
                scalar(PlayerResourceIds.STAMINA, 25, 150, 1, ClientResourceTrust.FRESH));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.STAMINA, () -> 0L, () -> 0L);
        assertTrue(result.current() <= result.maximum());
        assertEquals(25L, result.current());
        assertEquals(150L, result.maximum());
    }

    // 6. Unit scale conversion is deterministic and overflow-safe.
    @Test
    void unitScaleConversionIsDeterministicAndOverflowSafe() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.RAGE,
                scalar(PlayerResourceIds.RAGE, 1_000_000_000_000L, 2_000_000_000_000L, 10, ClientResourceTrust.FRESH));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.RAGE, () -> 0L, () -> 0L);
        assertEquals(100_000_000_000L, result.current());
        assertEquals(200_000_000_000L, result.maximum());

        // Deterministic: repeated resolution against the same underlying result yields the same output.
        ClientResourcePresentationResolver.ScalarPresentation again =
                resolver.resolveScalar(PlayerResourceIds.RAGE, () -> 0L, () -> 0L);
        assertEquals(result.current(), again.current());
        assertEquals(result.maximum(), again.maximum());
    }

    // 7. Malformed values do not crash the UI resolver (values at the extreme of the valid range).
    @Test
    void valuesNearLongMaxDoNotCrashTheResolver() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.MANA,
                scalar(PlayerResourceIds.MANA, Long.MAX_VALUE - 1, Long.MAX_VALUE, 1, ClientResourceTrust.FRESH));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result = assertDoesNotThrow(
                () -> resolver.resolveScalar(PlayerResourceIds.MANA, () -> 0L, () -> 0L));
        assertEquals(Long.MAX_VALUE - 1, result.current());
        assertEquals(Long.MAX_VALUE, result.maximum());
    }

    // 8. Legacy supplier is not evaluated when a valid Generic result exists.
    @Test
    void legacySupplierIsNotEvaluatedWhenAValidGenericResultExists() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.MANA,
                scalar(PlayerResourceIds.MANA, 40, 100, 1, ClientResourceTrust.FRESH));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        AtomicBoolean legacyEvaluated = new AtomicBoolean(false);
        resolver.resolveScalar(PlayerResourceIds.MANA,
                () -> { legacyEvaluated.set(true); return 0L; },
                () -> { legacyEvaluated.set(true); return 0L; });
        assertFalse(legacyEvaluated.get(), "legacy suppliers must not be evaluated when the Generic result is used");
    }

    // ── SPELL SLOTS (partitioned) ────────────────────────────────────────────────────────────

    // 22. Generic partition keys remain 1 through 10.
    @Test
    void partitionKeysRemainOneThroughTen() {
        ClientResourceQueryResult.Partitioned.Partition[] partitions =
                new ClientResourceQueryResult.Partitioned.Partition[10];
        for (int level = 1; level <= 10; level++) {
            partitions[level - 1] = new ClientResourceQueryResult.Partitioned.Partition(level, level % 2, 2, 0);
        }
        ClientResourceService service = serviceReturning(PlayerResourceIds.SPELL_SLOTS,
                partitioned(PlayerResourceIds.SPELL_SLOTS, 1, ClientResourceTrust.FRESH, partitions));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        for (int level = 1; level <= 10; level++) {
            ClientResourcePresentationResolver.PartitionPresentation result =
                    resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, level, () -> -1L, () -> -1L);
            assertTrue(result.generic(), "tier " + level + " must resolve from the Generic partitioned result");
        }
    }

    // 23. Current units mean remaining slots. 24. Maximum units remain maximum slots.
    @Test
    void currentUnitsMeanRemainingSlotsAndMaximumUnitsMeanMaximumSlots() {
        ClientResourceQueryResult.Partitioned.Partition partition =
                new ClientResourceQueryResult.Partitioned.Partition(3, 2, 4, 0);
        ClientResourceService service = serviceReturning(PlayerResourceIds.SPELL_SLOTS,
                partitioned(PlayerResourceIds.SPELL_SLOTS, 1, ClientResourceTrust.FRESH, partition));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.PartitionPresentation result =
                resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 3, () -> -1L, () -> -1L);
        assertEquals(2L, result.current(), "current must be remaining slots, never used slots");
        assertEquals(4L, result.maximum());
    }

    // 25. Tier 1 is not reindexed to 0. 26. Tier 10 survives.
    @Test
    void tierOneAndTierTenSurviveWithoutReindexing() {
        ClientResourceQueryResult.Partitioned.Partition t1 = new ClientResourceQueryResult.Partitioned.Partition(1, 1, 1, 0);
        ClientResourceQueryResult.Partitioned.Partition t10 = new ClientResourceQueryResult.Partitioned.Partition(10, 0, 1, 0);
        ClientResourceService service = serviceReturning(PlayerResourceIds.SPELL_SLOTS,
                partitioned(PlayerResourceIds.SPELL_SLOTS, 1, ClientResourceTrust.FRESH, t1, t10));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        assertTrue(resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 1, () -> -1L, () -> -1L).generic());
        assertTrue(resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 10, () -> -1L, () -> -1L).generic());
        assertFalse(resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 0, () -> -1L, () -> -1L).generic(),
                "there is no tier 0 — a query for it must fall back rather than resolve from the Generic map");
    }

    // 27. All-zero non-caster partitions remain valid.
    @Test
    void allZeroNonCasterPartitionRemainsValid() {
        ClientResourceQueryResult.Partitioned.Partition partition =
                new ClientResourceQueryResult.Partitioned.Partition(1, 0, 0, 0);
        ClientResourceService service = serviceReturning(PlayerResourceIds.SPELL_SLOTS,
                partitioned(PlayerResourceIds.SPELL_SLOTS, 1, ClientResourceTrust.FRESH, partition));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.PartitionPresentation result =
                resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 1, () -> -1L, () -> -1L);
        assertTrue(result.generic(), "an all-zero partition (non-caster) is a valid Generic success, never treated as unavailable");
        assertEquals(0L, result.current());
        assertEquals(0L, result.maximum());
    }

    // 28. Unavailable Generic partitions use ClientSpellSlotManager-style fallback.
    @Test
    void unavailableGenericPartitionUsesFallback() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.SPELL_SLOTS,
                ClientResourceQueryResult.unavailable(PlayerResourceIds.SPELL_SLOTS, ClientResourceUnavailableReason.NOT_SYNCHRONIZED_YET));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.PartitionPresentation result =
                resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 1, () -> 2L, () -> 4L);
        assertFalse(result.generic());
        assertEquals(2L, result.current());
        assertEquals(4L, result.maximum());
    }

    // 29 (SpellRadialScreen itself does not mutate/recalculate — proven at the resolver boundary):
    // resolvePartition never touches anything beyond the query result and the caller's suppliers.
    @Test
    void resolvePartitionNeverEvaluatesLegacySupplierWhenGenericPartitionExists() {
        ClientResourceQueryResult.Partitioned.Partition partition =
                new ClientResourceQueryResult.Partitioned.Partition(5, 1, 3, 0);
        ClientResourceService service = serviceReturning(PlayerResourceIds.SPELL_SLOTS,
                partitioned(PlayerResourceIds.SPELL_SLOTS, 1, ClientResourceTrust.FRESH, partition));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        AtomicBoolean legacyEvaluated = new AtomicBoolean(false);
        resolver.resolvePartition(PlayerResourceIds.SPELL_SLOTS, 5,
                () -> { legacyEvaluated.set(true); return -1L; },
                () -> { legacyEvaluated.set(true); return -1L; });
        assertFalse(legacyEvaluated.get());
    }

    // ── RAGE-specific extensions ─────────────────────────────────────────────────────────────

    // 21. PENDING_RESYNC Rage remains displayable.
    @Test
    void pendingResyncRageRemainsDisplayable() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.RAGE,
                scalar(PlayerResourceIds.RAGE, 1, 2, 1, ClientResourceTrust.PENDING_RESYNC));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.RAGE, () -> 0L, () -> 0L);
        assertTrue(result.generic());
        assertEquals(1L, result.current());
        assertEquals(2L, result.maximum());
    }

    // 19. Missing Rage is not fabricated as 0/0 (a present 0/0 is a distinct, valid success).
    @Test
    void presentRageZeroZeroIsAValidGenericSuccessNotFallback() {
        ClientResourceService service = serviceReturning(PlayerResourceIds.RAGE,
                scalar(PlayerResourceIds.RAGE, 0, 0, 1, ClientResourceTrust.FRESH));
        ClientResourcePresentationResolver resolver = new ClientResourcePresentationResolver(service);

        ClientResourcePresentationResolver.ScalarPresentation result =
                resolver.resolveScalar(PlayerResourceIds.RAGE, () -> 9L, () -> 9L);
        assertTrue(result.generic(), "a present 0/0 Rage scalar is a valid Generic success, never routed to fallback");
        assertEquals(0L, result.current());
        assertEquals(0L, result.maximum());
    }
}
