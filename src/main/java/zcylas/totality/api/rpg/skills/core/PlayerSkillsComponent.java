package zcylas.totality.api.rpg.skills.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.api.rpg.stats.PlayerStats;
import zcylas.totality.api.rpg.stats.StatsComponents;

/**
 * Player component that stores all skill data.
 * Persists on death via copyFrom.
 * Synced to client via writeSyncPacket/applySyncPacket.
 */
public class PlayerSkillsComponent implements SyncedComponent, CopyableComponent<PlayerSkillsComponent> {

    private final PlayerSkills skills = new PlayerSkills();
    private final ServerPlayer player;

    public PlayerSkillsComponent(ServerPlayer player) {
        this.player = player;
    }

    public PlayerSkills getSkills() {
        return skills;
    }

    /**
     * Adds XP to a skill and handles level-up side effects.
     * Returns true if a level-up occurred.
     */
    public boolean addSkillXp(Skill skill, int xpAmount) {
        int newLevel = skills.addXp(skill, xpAmount);
        if (newLevel > 0) {
            onSkillLevelUp(skill, newLevel);
            sync();
            return true;
        }
        sync();
        return false;
    }

    /**
     * Called when a skill levels up.
     * Awards character XP equal to the new skill level (Skyrim formula).
     */
    private void onSkillLevelUp(Skill skill, int newLevel) {
        if (player == null) return;

        // Award character XP
        PlayerStats stats = StatsComponents.getStats(player);
        boolean characterLevelUp = stats.addCharacterXp(newLevel);

        // Notify player of skill level up
        zcylas.totality.networking.notification.SendNotificationPayload.send(
                player,
                "📈 " + skill.getDisplayName() + " reached level " + newLevel + "!",
                zcylas.totality.networking.notification.SendNotificationPayload.GREEN
        );

        // Notify player of character level up
        if (characterLevelUp) {
            zcylas.totality.networking.notification.SendNotificationPayload.send(
                    player,
                    "⭐ Level " + stats.getLevel() + "! +" + stats.getUnspentAttributePoints()
                            + " attribute point, +1 mastery point",
                    zcylas.totality.networking.notification.SendNotificationPayload.GOLD
            );
            MasteriesComponents.get(player).getMasteries().addMasteryPoints(1);
            MasteriesComponents.get(player).sync();
            StatsComponents.get(player).sync();
        }
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            SkillsComponents.PLAYER_SKILLS.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        Skill[] skillValues = Skill.values();
        buf.writeInt(skillValues.length);
        for (Skill skill : skillValues) {
            buf.writeInt(skills.getLevel(skill));
            buf.writeInt(skills.getXp(skill));
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        Skill[] skillValues = Skill.values();
        for (int i = 0; i < count && i < skillValues.length; i++) {
            int level = buf.readInt();
            int xp = buf.readInt();
            SkillData data = skills.getData(skillValues[i]);
            data.setLevelDirectly(level);
            data.setXpDirectly(xp);
        }
        ClientSkillsManager.apply(skills);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        for (Skill skill : Skill.values()) {
            String key = skill.name().toLowerCase();
            output.putInt("sk_lvl_" + key, skills.getLevel(skill));
            output.putInt("sk_xp_"  + key, skills.getXp(skill));
        }
    }

    @Override
    public void readData(ValueInput input) {
        for (Skill skill : Skill.values()) {
            String key = skill.name().toLowerCase();
            SkillData data = skills.getData(skill);
            data.setLevelDirectly(input.getIntOr("sk_lvl_" + key, 1));
            data.setXpDirectly(input.getIntOr("sk_xp_"  + key, 0));
        }
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerSkillsComponent other, HolderLookup.Provider registries) {
        for (Skill skill : Skill.values()) {
            SkillData data = skills.getData(skill);
            data.setLevelDirectly(other.skills.getLevel(skill));
            data.setXpDirectly(other.skills.getXp(skill));
        }
    }
}