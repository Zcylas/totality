package zcylas.totality.api.soulgem;

/**
 * Outcome of {@link SoulCaptureService#attemptCapture}. See that class's Javadoc for the exact
 * check order (2026-09-17 review-correction pass added {@link #STACKED_VESSEL_REQUIRES_SPLIT}).
 */
public enum SoulCaptureResult {
    SUCCESS,
    ALREADY_FILLED,
    STACKED_VESSEL_REQUIRES_SPLIT,
    RANK_ZERO_INELIGIBLE,
    CATEGORY_REJECTED,
    RANK_TOO_STRONG,
    NOT_A_SOUL_GEM
}
