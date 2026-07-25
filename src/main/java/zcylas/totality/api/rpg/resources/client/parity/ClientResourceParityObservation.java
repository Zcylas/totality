package zcylas.totality.api.rpg.resources.client.parity;

import net.minecraft.resources.Identifier;

import java.util.Objects;

/**
 * One immutable, bounded parity observation for a single Resource id — the entire externally
 * visible state {@link ClientResourceParityTracker} exposes per resource. Never grows unbounded:
 * this record always represents only the <b>latest</b> comparison, never a history list.
 *
 * <p>{@code firstMismatchTick} is {@code null} whenever {@code classification} is not
 * {@link ClientResourceParityClassification#TRANSITIONAL_MISMATCH} or
 * {@link ClientResourceParityClassification#PERSISTENT_MISMATCH} — there is no pending mismatch
 * episode to date from. When present, it must be a non-negative tick no later than {@code
 * lastObservedTick} (external-review correction — a mismatch cannot have first been observed after
 * its own most recent observation). Raw formatted/log-ready strings are deliberately not part of
 * this slice (Phase 3B-2C/Phase 3B-3 concern); callers needing a display string derive it from the
 * typed summaries themselves.
 *
 * <p>{@code graceFrozen} may be {@code true} only alongside {@code TRANSITIONAL_MISMATCH} or {@code
 * PERSISTENT_MISMATCH} (external-review correction) — freezing is a grace-progression concept, and
 * no other classification tracks a grace deadline at all, so a frozen flag on any of them would be
 * meaningless and is rejected as a constructor invariant violation rather than silently ignored.
 */
public record ClientResourceParityObservation(
        Identifier resourceId,
        ClientResourceParityClassification classification,
        ClientResourceParitySummary genericSummary,
        ClientResourceParitySummary legacySummary,
        Long firstMismatchTick,
        long lastObservedTick,
        int observationCount,
        boolean graceFrozen
) {
    public ClientResourceParityObservation {
        Objects.requireNonNull(resourceId, "resourceId");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(genericSummary, "genericSummary");
        Objects.requireNonNull(legacySummary, "legacySummary");
        boolean tracksMismatch = classification == ClientResourceParityClassification.TRANSITIONAL_MISMATCH
                || classification == ClientResourceParityClassification.PERSISTENT_MISMATCH;
        if (tracksMismatch && firstMismatchTick == null) {
            throw new IllegalArgumentException("firstMismatchTick must be set while a mismatch is pending");
        }
        if (!tracksMismatch && firstMismatchTick != null) {
            throw new IllegalArgumentException("firstMismatchTick must be null outside a pending mismatch");
        }
        if (lastObservedTick < 0) {
            throw new IllegalArgumentException("lastObservedTick must be >= 0, was " + lastObservedTick);
        }
        if (observationCount < 1) {
            throw new IllegalArgumentException("observationCount must be >= 1, was " + observationCount);
        }
        if (firstMismatchTick != null) {
            if (firstMismatchTick < 0) {
                throw new IllegalArgumentException("firstMismatchTick must be >= 0, was " + firstMismatchTick);
            }
            if (firstMismatchTick > lastObservedTick) {
                throw new IllegalArgumentException(
                        "firstMismatchTick (" + firstMismatchTick + ") must not be after lastObservedTick (" + lastObservedTick + ")");
            }
        }
        if (graceFrozen && !tracksMismatch) {
            throw new IllegalArgumentException("graceFrozen must be false outside a pending mismatch");
        }
    }
}
