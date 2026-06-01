package zcylas.totality.api.mob.stats;

import com.google.gson.annotations.SerializedName;

public class RarityVariant {
    @SerializedName("rank")             private String rank           = "E";
    @SerializedName("weight")           private int    weight         = 1;
    @SerializedName("level_bonus")      private int    levelBonus     = 0;
    @SerializedName("stat_multiplier")  private double statMultiplier = 1.0;

    public MobRank getRank() {
        try { return MobRank.valueOf(rank.toUpperCase()); }
        catch (Exception e) { return MobRank.E; }
    }
    public int    getWeight()         { return weight; }
    public int    getLevelBonus()     { return levelBonus; }
    public double getStatMultiplier() { return statMultiplier; }
}