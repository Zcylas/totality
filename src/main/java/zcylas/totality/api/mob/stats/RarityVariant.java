package zcylas.totality.api.mob.stats;

import com.google.gson.annotations.SerializedName;

public class RarityVariant {
    @SerializedName("rarity")          private String rarity        = "common";
    @SerializedName("weight")          private int    weight         = 1;
    @SerializedName("level_bonus")     private int    levelBonus     = 0;
    @SerializedName("stat_multiplier") private double statMultiplier = 1.0;

    public SpawnRarity getRarity()      { return SpawnRarity.fromString(rarity); }
    public int         getWeight()      { return weight; }
    public int         getLevelBonus()  { return levelBonus; }
    public double      getStatMultiplier() { return statMultiplier; }
}