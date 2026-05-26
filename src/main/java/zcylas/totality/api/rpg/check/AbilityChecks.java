package zcylas.totality.api.rpg.check;

import net.minecraft.resources.Identifier;
import zcylas.totality.api.rpg.stats.AbilityScore;

public final class AbilityChecks {

    public static AbilityCheck strength(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.STR, dc, false);
    }
    public static AbilityCheck dexterity(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.DEX, dc, false);
    }
    public static AbilityCheck constitution(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.CON, dc, false);
    }
    public static AbilityCheck intelligence(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.INT, dc, false);
    }
    public static AbilityCheck wisdom(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.WIS, dc, false);
    }
    public static AbilityCheck charisma(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.CHA, dc, false);
    }
    public static AbilityCheck arcana(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.INT, dc, true); // proficient by default
    }
    public static AbilityCheck perception(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.WIS, dc, true);
    }
    public static AbilityCheck persuasion(Identifier id, int dc) {
        return new AbilityCheck(id, AbilityScore.CHA, dc, true);
    }

    private AbilityChecks() {}
}