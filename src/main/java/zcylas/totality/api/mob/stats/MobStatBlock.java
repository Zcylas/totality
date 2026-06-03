package zcylas.totality.api.mob.stats;

import com.google.gson.annotations.SerializedName;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import zcylas.totality.api.rpg.stats.AbilityScore;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class MobStatBlock {

    @SerializedName("entity_type")    private String  entityType  = "";
    @SerializedName("display_name")   private String  displayName = "";
    @SerializedName("lore")           private String  lore        = "";
    @SerializedName("size")           private String  size        = "medium";
    @SerializedName("categories")     private List<String> categories = List.of();

    @SerializedName("min_level")      private int     minLevel    = 1;
    @SerializedName("max_level")      private @Nullable Integer maxLevel = null;

    @SerializedName("base_stats")     private Map<String, Integer> baseStats     = new HashMap<>();
    @SerializedName("natural_armor")  private int                  naturalArmor  = 0;
    @SerializedName("growth_weights") private Map<String, Double>  growthWeights = new HashMap<>();

    @SerializedName("passives")       private List<String> passives    = List.of();
    @SerializedName("abilities")      private List<String> abilities   = List.of();
    @SerializedName("resistances")    private List<String> resistances = List.of();
    @SerializedName("immunities")     private List<String> immunities  = List.of();

    @SerializedName("attack_type")    private String attackType = "melee";
    @SerializedName("attack_stat")    private @Nullable String attackStat = null;
    @SerializedName("ac_stat")        private @Nullable String acStat     = null;

    @SerializedName("faction")        private String faction    = "hostile";
    @SerializedName("aggro_type")     private String aggroType  = "hostile";
    @SerializedName("spawn_weight")   private int    spawnWeight = 10;

    @SerializedName("rank_color_override") private @Nullable Integer rankColorOverride = null;
    @SerializedName("rarity_variants")     private List<RarityVariant> rarityVariants  = List.of();

    @SerializedName("attack_damage_type")
    private @Nullable String attackDamageType = null;
    // ── Accessors ─────────────────────────────────────────────────────────────

    public Identifier getEntityType() { return Identifier.parse(entityType); }
    public String  getDisplayName()         { return displayName; }
    public String  getLore()               { return lore; }
    public int     getMinLevel()           { return minLevel; }
    public @Nullable Integer getMaxLevel() { return maxLevel; }
    public int     getNaturalArmor()       { return naturalArmor; }
    public List<String> getCategories()    { return categories; }
    public List<String> getResistances()   { return resistances; }
    public List<String> getImmunities()    { return immunities; }
    public List<RarityVariant> getRarityVariants() { return rarityVariants; }
    public @Nullable Integer getRankColorOverride() { return rankColorOverride; }
    public boolean isRanged() { return "ranged".equals(attackType); }
    public @Nullable String getAttackDamageTypeName() { return attackDamageType; }
    // ── Computed ──────────────────────────────────────────────────────────────

    /** All ability scores at the given mob level. */
    public Map<AbilityScore, Integer> computeStats(int level) {
        Map<AbilityScore, Integer> stats = new EnumMap<>(AbilityScore.class);
        for (AbilityScore s : AbilityScore.values())
            stats.put(s, baseStats.getOrDefault(s.name().toLowerCase(), 10));

        int points = Math.max(0, level - 1) * 5;
        for (Map.Entry<String, Double> e : growthWeights.entrySet()) {
            try {
                AbilityScore s = AbilityScore.valueOf(e.getKey().toUpperCase());
                stats.merge(s, (int) Math.round(points * e.getValue()), Integer::sum);
            } catch (Exception ignored) {}
        }
        return stats;
    }

    /** AC = 10 + modifier(acStat) + naturalArmor */
    public int computeAC(Map<AbilityScore, Integer> stats) {
        AbilityScore acScore = parseScore(acStat, AbilityScore.DEX);
        return 10 + modifier(stats.getOrDefault(acScore, 10)) + naturalArmor;
    }

    /** Ability score used for attack rolls. */
    public AbilityScore getAttackAbilityScore() {
        if (attackStat != null) return parseScore(attackStat, AbilityScore.STR);
        return isRanged() ? AbilityScore.DEX : AbilityScore.STR;
    }

    /** Roll a rarity rank based on variant weights. */
    public MobRank rollRank(RandomSource random) {
        if (rarityVariants.isEmpty()) return MobRank.E;
        int totalWeight = rarityVariants.stream().mapToInt(RarityVariant::getWeight).sum();
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (RarityVariant v : rarityVariants) {
            cumulative += v.getWeight();
            if (roll < cumulative) return v.getRank();
        }
        return MobRank.E;
    }

    public @Nullable RarityVariant getVariant(MobRank rank) {
        return rarityVariants.stream()
                .filter(v -> v.getRank() == rank)
                .findFirst().orElse(null);
    }

    public static int modifier(int score) { return (score - 10) / 2; }

    private static AbilityScore parseScore(@Nullable String s, AbilityScore fallback) {
        if (s == null) return fallback;
        try { return AbilityScore.valueOf(s.toUpperCase()); }
        catch (Exception e) { return fallback; }
    }
}