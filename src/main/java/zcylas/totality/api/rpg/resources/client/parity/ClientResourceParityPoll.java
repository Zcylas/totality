package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.resources.PlayerResourceIds;
import zcylas.totality.api.rpg.resources.client.ClientResourceQueryResult;

import java.util.function.BiFunction;

/**
 * Pure Phase 3B-2B polling algorithm — the one place that knows the full set of four
 * generic-sync-eligible parity pairs and which comparator/policy owns each. No Minecraft/Fabric
 * dependency: driven entirely through the {@link ClientResourceParityGenericAccess}/{@link
 * ClientResourceParityLegacyAccess} seams, so it is directly unit-testable with synthetic fakes.
 *
 * <p>Every call to {@link #pollOnce} performs exactly four comparisons — one generic façade query,
 * one legacy read, one pure comparator/policy invocation, and one {@link
 * ClientResourceParityTracker#observe} call, for each of {@code totality:mana}, {@code
 * totality:stamina}, {@code totality:spell_slots}, {@code totality:rage} — using freshly read
 * values every time, never reusing a stored value and never calling {@link
 * ClientResourceParityTracker#advanceDeadline} (that method remains a narrow, separate diagnostic
 * helper, not part of this polling path). This is intentionally bounded, non-optimized work: four
 * small comparisons per call is accepted, not something this slice trims into notification hooks.
 *
 * <p><b>External-review correction (2026-07-25):</b> Rage is never skipped, regardless of whether
 * the legacy Rage source is structurally available. A prior version of this method returned early
 * for Rage when {@code legacy.rageSummary()} was empty, which could leave a stale prior Rage
 * observation visible in the tracker. {@code legacy.rageSummary()} now always returns a {@link
 * ClientResourceParitySummary} (never an {@code Optional}), so every valid-player poll performs
 * exactly four generic queries, four legacy reads, four comparator/policy invocations, and four
 * {@code tracker.observe} calls — never fewer.
 *
 * <p>Owns only: obtaining fresh inputs, selecting the correct comparator, and supplying the current
 * tick/pending-resync metadata to the tracker. It never assigns a classification itself — {@link
 * ClientResourceScalarParityComparator}, {@link ClientSpellSlotParityComparator}, and {@link
 * ClientRageParityPolicy} remain the sole authorities for that decision.
 */
public final class ClientResourceParityPoll {

    private ClientResourceParityPoll() {}

    /**
     * Polls all four eligible Resources once, at {@code tick}, submitting each fresh outcome to
     * {@code tracker}. Callers (the real client-only coordinator, or a test) are responsible for
     * supplying a valid, monotonically-non-decreasing {@code tick} — this method itself has no
     * tick-counter or lifecycle concept of its own; see {@link ClientResourceParityLifecycle} for
     * that.
     */
    public static void pollOnce(
            ClientResourceParityTracker tracker,
            long tick,
            ClientResourceParityGenericAccess generic,
            ClientResourceParityLegacyAccess legacy) {
        pollScalar(tracker, tick, PlayerResourceIds.MANA, generic, legacy.manaSummary(),
                ClientResourceScalarParityComparator::compare);
        pollScalar(tracker, tick, PlayerResourceIds.STAMINA, generic, legacy.staminaSummary(),
                ClientResourceScalarParityComparator::compare);
        pollSpellSlots(tracker, tick, generic, legacy.spellSlotSummary());
        pollRage(tracker, tick, generic, legacy);
    }

    private static void pollScalar(
            ClientResourceParityTracker tracker,
            long tick,
            Identifier resourceId,
            ClientResourceParityGenericAccess generic,
            ClientResourceParitySummary legacySummary,
            BiFunction<ClientResourceParitySummary, ClientResourceParitySummary, ClientResourceParityOutcome> comparator) {
        ClientResourceQueryResult genericResult = generic.queryScalar(resourceId);
        ClientGenericParitySummaryMapper.MappedResult mapped = ClientGenericParitySummaryMapper.map(genericResult);
        ClientResourceParityOutcome outcome = comparator.apply(mapped.summary(), legacySummary);
        tracker.observe(resourceId, tick, outcome, mapped.summary(), legacySummary, mapped.pendingResync());
    }

    private static void pollSpellSlots(
            ClientResourceParityTracker tracker,
            long tick,
            ClientResourceParityGenericAccess generic,
            ClientResourceParitySummary legacySummary) {
        ClientResourceQueryResult genericResult = generic.queryPartitioned(PlayerResourceIds.SPELL_SLOTS);
        ClientGenericParitySummaryMapper.MappedResult mapped = ClientGenericParitySummaryMapper.map(genericResult);
        ClientResourceParityOutcome outcome = ClientSpellSlotParityComparator.compare(mapped.summary(), legacySummary);
        tracker.observe(PlayerResourceIds.SPELL_SLOTS, tick, outcome, mapped.summary(), legacySummary, mapped.pendingResync());
    }

    private static void pollRage(
            ClientResourceParityTracker tracker,
            long tick,
            ClientResourceParityGenericAccess generic,
            ClientResourceParityLegacyAccess legacy) {
        // A genuinely absent local player / charges component is a structural anomaly (should not
        // occur while polling is active at all — see ClientResourceParityLifecycle, which only
        // ever calls into a poll while a local player exists), never fabricated as a 0/0 value —
        // but it must still be observed as a fresh MODEL_MISMATCH, replacing any stale prior Rage
        // observation, never silently skipped. legacy.rageSummary() always returns a summary.
        ClientResourceParitySummary legacySummary = legacy.rageSummary();
        ClientResourceQueryResult genericResult = generic.queryScalar(PlayerResourceIds.RAGE);
        ClientGenericParitySummaryMapper.MappedResult mapped = ClientGenericParitySummaryMapper.map(genericResult);
        boolean rageExpectedForPlayer = legacy.rageExpectedForPlayer();
        ClientResourceParityOutcome outcome =
                ClientRageParityPolicy.compare(rageExpectedForPlayer, mapped.summary(), legacySummary);
        tracker.observe(PlayerResourceIds.RAGE, tick, outcome, mapped.summary(), legacySummary, mapped.pendingResync());
    }
}
