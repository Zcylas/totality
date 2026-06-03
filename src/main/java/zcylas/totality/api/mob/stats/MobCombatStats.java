package zcylas.totality.api.mob.stats;

import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.rpg.combat.ProficiencyBonus;
import zcylas.totality.api.rpg.classes.PlayerClassComponent;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.*;

public class MobCombatStats {

    private boolean initialized = false;
    private int     level       = 1;
    private MobRank rank        = MobRank.E;
    private int     ac          = 10;
    private int     attackBonus = 0;
    private Map<AbilityScore, Integer> stats = new EnumMap<>(AbilityScore.class);
    private @Nullable MobStatBlock statBlock = null;

    // ── Init ──────────────────────────────────────────────────────────────────

    public void initialize(LivingEntity entity) {
        if (initialized) return;
        initialized = true;

        MobStatBlock block = MobStatBlockRegistry.get(entity);
        if (block == null) {
            // No stat block — vanilla mob with default stats
            for (AbilityScore s : AbilityScore.values()) stats.put(s, 10);
            ac = 10;
            return;
        }

        this.statBlock = block;

        // Roll level
        int maxLvl = block.getMaxLevel() != null ? block.getMaxLevel() : 100;
        this.level = block.getMinLevel() + entity.getRandom().nextInt(
                Math.max(1, maxLvl - block.getMinLevel() + 1));

        // Roll rank
        this.rank = block.rollRank(entity.getRandom());
        RarityVariant variant = block.getVariant(rank);
        if (variant != null) {
            this.level = Math.min(100, this.level + variant.getLevelBonus());
        }

        // Compute stats with rank multiplier
        double multiplier = variant != null ? variant.getStatMultiplier() : 1.0;
        Map<AbilityScore, Integer> base = block.computeStats(this.level);
        for (Map.Entry<AbilityScore, Integer> e : base.entrySet())
            stats.put(e.getKey(), (int) Math.round(e.getValue() * multiplier));

        // AC and attack bonus
        this.ac = block.computeAC(stats);
        int classLevel = PlayerClassComponent.toClassLevel(this.level);
        int profBonus  = ProficiencyBonus.forClassLevel(classLevel);
        AbilityScore atkScore = block.getAttackAbilityScore();
        this.attackBonus = MobStatBlock.modifier(stats.getOrDefault(atkScore, 10)) + profBonus;

        // Broadcast stats to nearby players
        if (entity.level() instanceof net.minecraft.server.level.ServerLevel sl) {
            zcylas.totality.networking.mob.MobStatsSyncPayload payload =
                    new zcylas.totality.networking.mob.MobStatsSyncPayload(
                            entity.getId(), this.level, this.rank.ordinal(), this.ac);
            sl.getPlayers(p -> p.distanceToSqr(entity.position()) < 1024)
                    .forEach(p -> net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(p, payload));
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isInitialized()  { return initialized; }
    public int     getLevel()       { return level; }
    public MobRank getRank()        { return rank; }
    public int     getAC()          { return ac; }
    public int     getAttackBonus() { return attackBonus; }
    public @Nullable MobStatBlock getStatBlock() { return statBlock; }

    public int getStat(AbilityScore score) {
        return stats.getOrDefault(score, 10);
    }

    public int getModifier(AbilityScore score) {
        return MobStatBlock.modifier(getStat(score));
    }

    public int getProficiencyBonus() {
        return ProficiencyBonus.forClassLevel(
                PlayerClassComponent.toClassLevel(this.level));
    }
}