package zcylas.totality.api.rpg.check;

import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.Map;

/**
 * Maps D&D 5e skill names (as used in dialogue roll specs, e.g. "Persuasion") to their
 * governing ability score, so a roll's flavor skill name actually drives which stat gets
 * added to the total instead of the roll being flat d20 + situational bonuses only.
 */
public final class SkillAbilityMap {

    private static final Map<String, AbilityScore> SKILLS = Map.ofEntries(
            Map.entry("Athletics", AbilityScore.STR),
            Map.entry("Acrobatics", AbilityScore.DEX),
            Map.entry("Sleight of Hand", AbilityScore.DEX),
            Map.entry("Stealth", AbilityScore.DEX),
            Map.entry("Arcana", AbilityScore.INT),
            Map.entry("History", AbilityScore.INT),
            Map.entry("Investigation", AbilityScore.INT),
            Map.entry("Nature", AbilityScore.INT),
            Map.entry("Religion", AbilityScore.INT),
            Map.entry("Animal Handling", AbilityScore.WIS),
            Map.entry("Insight", AbilityScore.WIS),
            Map.entry("Medicine", AbilityScore.WIS),
            Map.entry("Perception", AbilityScore.WIS),
            Map.entry("Survival", AbilityScore.WIS),
            Map.entry("Deception", AbilityScore.CHA),
            Map.entry("Intimidation", AbilityScore.CHA),
            Map.entry("Performance", AbilityScore.CHA),
            Map.entry("Persuasion", AbilityScore.CHA)
    );

    @Nullable
    public static AbilityScore resolve(String skillName) {
        return SKILLS.get(skillName);
    }

    private SkillAbilityMap() {}
}
