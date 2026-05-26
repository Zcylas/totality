package zcylas.totality.api.rpg.stats;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

/**
 * Player component that stores all RPG stats for a player.
 * Also persists current HP so it survives world reloads.
 * Stamina and mana are now persisted by PlayerResourceComponent.
 */
public class PlayerStatsComponent implements SyncedComponent, CopyableComponent<PlayerStatsComponent> {

    private final PlayerStats stats = new PlayerStats();
    private final ServerPlayer player;

    private float savedHp = -1;

    public PlayerStatsComponent(ServerPlayer player) {
        this.player = player;
    }

    public PlayerStats getStats() { return stats; }

    // ── Resource save/restore ─────────────────────────────────────────────────

    public void saveCurrentResources() {
        if (player == null) return;
        savedHp = player.getHealth();
    }

    public void restoreResources() {
        if (player == null) return;
        float maxHp = player.getMaxHealth();
        player.setHealth(savedHp < 0 ? maxHp : Math.min(savedHp, maxHp));
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            StatsComponents.PLAYER_STATS.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(stats.getLevel());
        buf.writeInt(stats.getCharacterXp());
        buf.writeInt(stats.getUnspentAttributePoints());
        for (AbilityScore score : AbilityScore.values()) {
            buf.writeInt(stats.getScore(score));
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        stats.setLevelDirectly(buf.readInt());
        stats.setCharacterXpDirectly(buf.readInt());
        stats.setUnspentAttributePointsDirectly(buf.readInt());
        AbilityScore[] scoreValues = AbilityScore.values();
        int[] scores = new int[scoreValues.length];
        for (int i = 0; i < scores.length; i++) scores[i] = buf.readInt();
        for (int i = 0; i < scoreValues.length; i++) {
            stats.setSpentPointsDirectly(scoreValues[i],
                    scores[i] - PlayerStats.BASE_SCORE);
        }
        stats.recalculate();
        ClientStatsManager.apply(stats);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("ps_level",   stats.getLevel());
        output.putInt("ps_xp",      stats.getCharacterXp());
        output.putInt("ps_unspent", stats.getUnspentAttributePoints());
        for (AbilityScore score : AbilityScore.values()) {
            String key = score.name().toLowerCase();
            output.putInt("ps_spent_"  + key, stats.getSpentPoints(score));
            output.putInt("ps_origin_" + key, stats.getOriginBonus(score));
            output.putInt("ps_class_"  + key, stats.getClassBonus(score));
            output.putInt("ps_item_"   + key, stats.getItemBonus(score));
        }
        output.putFloat("ps_hp", savedHp);
    }

    @Override
    public void readData(ValueInput input) {
        stats.setLevelDirectly(input.getIntOr("ps_level", 1));
        stats.setCharacterXpDirectly(input.getIntOr("ps_xp", 0));
        stats.setUnspentAttributePointsDirectly(input.getIntOr("ps_unspent", 0));
        for (AbilityScore score : AbilityScore.values()) {
            String key = score.name().toLowerCase();
            stats.setSpentPointsDirectly(score,  input.getIntOr("ps_spent_"  + key, 0));
            stats.setOriginBonusDirectly(score,  input.getIntOr("ps_origin_" + key, 0));
            stats.setClassBonusDirectly(score,   input.getIntOr("ps_class_"  + key, 0));
            stats.setItemBonusDirectly(score,    input.getIntOr("ps_item_"   + key, 0));
        }
        stats.recalculate();
        savedHp = input.getFloatOr("ps_hp", -1f);
    }

    @Override
    public void copyFrom(PlayerStatsComponent other, HolderLookup.Provider registries) {
        stats.setLevelDirectly(other.stats.getLevel());
        stats.setCharacterXpDirectly(other.stats.getCharacterXp());
        stats.setUnspentAttributePointsDirectly(other.stats.getUnspentAttributePoints());
        for (AbilityScore score : AbilityScore.values()) {
            stats.setSpentPointsDirectly(score, other.stats.getSpentPoints(score));
            stats.setOriginBonusDirectly(score, other.stats.getOriginBonus(score));
            stats.setClassBonusDirectly(score,  other.stats.getClassBonus(score));
            stats.setItemBonusDirectly(score,   other.stats.getItemBonus(score));
        }
        stats.recalculate();
        savedHp = -1;
    }
}