package zcylas.totality.api.rpg.check;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.stats.AbilityScore;

/**
 * Defines a single ability check — what stat is being tested and against what DC.
 * Use AbilityChecks factory methods to create common checks.
 */
public record AbilityCheck(
        Identifier id,
        AbilityScore score,
        int dc,
        boolean proficient
) {}