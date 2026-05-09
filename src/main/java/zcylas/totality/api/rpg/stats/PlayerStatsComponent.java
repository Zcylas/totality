package zcylas.totality.api.rpg.stats;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.mana.PlayerManaManager;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;

/**
 * Player component that stores all RPG stats for a player.
 * Also persists current stamina, mana and HP so they survive world reloads.
 */
public class PlayerStatsComponent implements SyncedComponent, CopyableComponent<PlayerStatsComponent> {

    private final PlayerStats stats = new PlayerStats();
    private final ServerPlayer player;

    // Persisted resource values — restored on join after stat modifiers are applied
    // -1 means "not yet saved, use max on first load"
    private int savedStamina = -1;
    private int savedMana = -1;
    private float savedHp = -1;

    public PlayerStatsComponent(ServerPlayer player) {
        this.player = player;
    }

    public PlayerStats getStats() {
        return stats;
    }

    // ── Resource save/restore ─────────────────────────────────────────────────

    /**
     * Called from StatsServerEvents on disconnect — saves current resource values
     * so they can be restored next login.
     */
    public void saveCurrentResources() {
        if (player == null) return;
        savedStamina = PlayerStaminaManager.getStamina(player);
        savedMana    = PlayerManaManager.getMana(player);
        savedHp      = player.getHealth();
    }

    /**
     * Called from StatsServerEvents on join, after stat modifiers are applied.
     * Restores stamina, mana and HP to their saved values, clamped to current max.
     */
    public void restoreResources() {
        if (player == null) return;

        // Stamina
        int maxStamina = PlayerStaminaManager.getMaxStamina(player);
        int staminaToRestore = savedStamina < 0 ? maxStamina
                : Math.min(savedStamina, maxStamina);
        PlayerStaminaManager.setStamina(player, staminaToRestore);

        // Mana
        int maxMana = PlayerManaManager.getMaxMana(player);
        int manaToRestore = savedMana < 0 ? maxMana
                : Math.min(savedMana, maxMana);
        PlayerManaManager.setMana(player, manaToRestore);

        // HP — vanilla saves HP but applies it before our CON modifier
        // so we clamp the saved HP to our actual max after modifiers
        float maxHp = player.getMaxHealth();
        float hpToRestore = savedHp < 0 ? maxHp
                : Math.min(savedHp, maxHp);
        player.setHealth(hpToRestore);
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
        int level   = buf.readInt();
        int xp      = buf.readInt();
        int unspent = buf.readInt();
        int[] scores = new int[AbilityScore.values().length];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = buf.readInt();
        }
        stats.setLevelDirectly(level);
        stats.setCharacterXpDirectly(xp);
        stats.setUnspentAttributePointsDirectly(unspent);
        AbilityScore[] scoreValues = AbilityScore.values();
        for (int i = 0; i < scoreValues.length; i++) {
            stats.setScore(scoreValues[i], scores[i]);
        }
        ClientStatsManager.apply(stats);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("ps_level",   stats.getLevel());
        output.putInt("ps_xp",      stats.getCharacterXp());
        output.putInt("ps_unspent", stats.getUnspentAttributePoints());
        for (AbilityScore score : AbilityScore.values()) {
            output.putInt("ps_" + score.name().toLowerCase(), stats.getScore(score));
        }
        // Save current resource values
        output.putInt("ps_stamina", savedStamina);
        output.putInt("ps_mana",    savedMana);
        output.putFloat("ps_hp",    savedHp);
    }

    @Override
    public void readData(ValueInput input) {
        stats.setLevelDirectly(input.getIntOr("ps_level", 1));
        stats.setCharacterXpDirectly(input.getIntOr("ps_xp", 0));
        stats.setUnspentAttributePointsDirectly(input.getIntOr("ps_unspent", 0));
        for (AbilityScore score : AbilityScore.values()) {
            stats.setScore(score, input.getIntOr("ps_" + score.name().toLowerCase(),
                    PlayerStats.BASE_SCORE));
        }
        // Load saved resource values
        savedStamina = input.getIntOr("ps_stamina", -1);
        savedMana    = input.getIntOr("ps_mana",    -1);
        savedHp      = input.getFloatOr("ps_hp",    -1f);
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerStatsComponent other, HolderLookup.Provider registries) {
        stats.setLevelDirectly(other.stats.getLevel());
        stats.setCharacterXpDirectly(other.stats.getCharacterXp());
        stats.setUnspentAttributePointsDirectly(other.stats.getUnspentAttributePoints());
        for (AbilityScore score : AbilityScore.values()) {
            stats.setScore(score, other.stats.getScore(score));
        }
        // On death, restore to full resources
        savedStamina = -1;
        savedMana    = -1;
        savedHp      = -1;
    }
}