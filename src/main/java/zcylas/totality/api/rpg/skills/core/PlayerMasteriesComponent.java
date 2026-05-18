package zcylas.totality.api.rpg.skills.core;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

/**
 * Player component that stores mastery unlock data.
 * Persists on death, synced to client.
 */
public class PlayerMasteriesComponent implements SyncedComponent, CopyableComponent<PlayerMasteriesComponent> {

    private final PlayerMasteries masteries = new PlayerMasteries();
    private final ServerPlayer player;

    public PlayerMasteriesComponent(ServerPlayer player) {
        this.player = player;
    }

    public PlayerMasteries getMasteries() { return masteries; }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            MasteriesComponents.PLAYER_MASTERIES.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player);
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(masteries.getMasteryPoints());
        var ranks = masteries.getAllUnlockedRanks();
        buf.writeInt(ranks.size());
        for (var entry : ranks.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        masteries.setMasteryPointsDirectly(buf.readInt());
        masteries.clearRanks();
        int count = buf.readInt();
        for (int i = 0; i < count; i++) {
            String id = buf.readUtf();
            int rank = buf.readInt();
            masteries.setRankDirectly(id, rank);
        }
        ClientMasteriesManager.apply(masteries);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("pm_points", masteries.getMasteryPoints());
        var ranks = masteries.getAllUnlockedRanks();
        output.putInt("pm_count", ranks.size());
        int i = 0;
        for (var entry : ranks.entrySet()) {
            output.putString("pm_id_" + i, entry.getKey());
            output.putInt("pm_rank_" + i, entry.getValue());
            i++;
        }
    }

    @Override
    public void readData(ValueInput input) {
        masteries.setMasteryPointsDirectly(input.getIntOr("pm_points", 0));
        int count = input.getIntOr("pm_count", 0);
        for (int i = 0; i < count; i++) {
            String id = input.getStringOr("pm_id_" + i, "");
            int rank = input.getIntOr("pm_rank_" + i, 0);
            if (!id.isEmpty()) masteries.setRankDirectly(id, rank);
        }
    }

    // ── Death copy ────────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerMasteriesComponent other, HolderLookup.Provider registries) {
        // Masteries survive death
        masteries.setMasteryPointsDirectly(other.masteries.getMasteryPoints());
        for (var entry : other.masteries.getAllUnlockedRanks().entrySet()) {
            masteries.setRankDirectly(entry.getKey(), entry.getValue());
        }
    }
}