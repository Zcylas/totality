package zcylas.totality.api.rpg.check;

/**
 * The result of a resolved ability check.
 */
public record AbilityCheckResult(
        int roll,
        int modifier,
        int total,
        boolean success
) {}