package zcylas.totality.api.rpg.resources.presentation;

/**
 * How a resource is drawn when it does have presentation metadata. See
 * {@code TOTALITY_GENERIC_PLAYER_RESOURCE_API.md} §19.3.
 *
 * Phase 2A only ever registers {@link #BAR} (Health, Food). The remaining values are declared for
 * definition-shape completeness, matching the precedent set by {@code ResourceCapability} in
 * Phase 1 (a full canonical enum where only a subset of values has a real Phase 1/2A consumer).
 */
public enum ResourceDisplayType {
    BAR,
    PIPS,
    SLOTS,
    NUMBER,
    STATE_INDICATOR,
    HIDDEN
}
