package zcylas.totality.api.rpg.stats;

/**
 * Represents ability score bonuses granted by a race or subrace.
 * Maps to the AbilityScore enum (STR, DEX, CON, END, INT, WIS, CHA, FTH).
 */
public record AbilityScoreBonus(
        int str, int dex, int con, int end,
        int intel, int wis, int cha, int fth
) {
    public static final AbilityScoreBonus NONE = new AbilityScoreBonus(0,0,0,0,0,0,0,0);

    public static class Builder {
        private int str = 0, dex = 0, con = 0, end = 0;
        private int intel = 0, wis = 0, cha = 0, fth = 0;

        public Builder str(int v)   { this.str   = v; return this; }
        public Builder dex(int v)   { this.dex   = v; return this; }
        public Builder con(int v)   { this.con   = v; return this; }
        public Builder end(int v)   { this.end   = v; return this; }
        public Builder intel(int v) { this.intel = v; return this; }
        public Builder wis(int v)   { this.wis   = v; return this; }
        public Builder cha(int v)   { this.cha   = v; return this; }
        public Builder fth(int v)   { this.fth   = v; return this; }

        public AbilityScoreBonus build() {
            return new AbilityScoreBonus(str, dex, con, end, intel, wis, cha, fth);
        }
    }
}