package zcylas.totality.api.core.movement;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

/**
 * Stores the player's active movement state.
 *
 * Only activelyFlying is persisted and synced — unlocked MovementModes
 * are computed at runtime from the player's passive abilities via
 * MovementModeProvider, not stored here.
 */
public class PlayerMovementComponent implements SyncedComponent, CopyableComponent<PlayerMovementComponent> {

    /** Whether the player has toggled biological flight ON. */
    private boolean activelyFlying = false;

    private final ServerPlayer player;

    public PlayerMovementComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Flight state ──────────────────────────────────────────────────────────

    public boolean isActivelyFlying() {
        return activelyFlying;
    }

    /**
     * Toggles biological flight on or off.
     * Also applies the MC ability flag so the player can actually fly.
     */
    public void setActivelyFlying(boolean flying) {
        this.activelyFlying = flying;
        player.getAbilities().flying = flying;
        player.getAbilities().mayfly = flying;
        player.onUpdateAbilities();
        sync();
    }

    // ── Sync ─────────────────────────────────────────────────────────────────

    private void sync() {
        if (!player.level().isClientSide()) {
            MovementComponents.MOVEMENT.sync((ComponentProvider) player);
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeBoolean(activelyFlying);
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        this.activelyFlying = buf.readBoolean();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void readData(ValueInput input) {
        this.activelyFlying = input.getBooleanOr("activelyFlying", false);
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putBoolean("activelyFlying", activelyFlying);
    }

    // ── Copy (respawn) ────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerMovementComponent other,
                         net.minecraft.core.HolderLookup.Provider registries) {
        this.activelyFlying = other.activelyFlying;
    }
}