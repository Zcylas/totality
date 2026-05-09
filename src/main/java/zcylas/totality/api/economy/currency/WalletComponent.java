package zcylas.totality.api.economy.currency;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

public class WalletComponent implements SyncedComponent, CopyableComponent<WalletComponent> {

    private long value;
    private final ServerPlayer player;

    public WalletComponent(ServerPlayer player) {
        this.player = player;
        this.value = 0;
    }

    public long getValue() {
        return value;
    }

    /**
     * Adds or subtracts from the wallet and syncs to client.
     * Use this for all normal transactions.
     */
    public void modify(long amount) {
        this.value = Math.max(0, this.value + amount);
        sync();
    }

    /**
     * Directly sets the value and syncs. Use sparingly.
     */
    public void setValue(long value) {
        this.value = Math.max(0, value);
        sync();
    }

    /**
     * Modifies without syncing or sending a message.
     */
    public void silentModify(long amount) {
        this.value = Math.max(0, this.value + amount);
    }

    /**
     * Returns true if the player can afford the given amount.
     */
    public boolean canAfford(long amount) {
        return this.value >= amount;
    }

    /**
     * Attempts to spend the given amount.
     * Returns true if successful, false if insufficient funds.
     */
    public boolean trySpend(long amount) {
        if (!canAfford(amount)) return false;
        modify(-amount);
        return true;
    }

    private void sync() {
        if (!player.level().isClientSide()) {
            CurrencyComponents.WALLET.sync((zcylas.totality.api.core.component.ComponentProvider) player);
        }
    }

    @Override
    public void readData(ValueInput input) {
        this.value = input.getLongOr("value", 0L);
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putLong("value", this.value);
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeLong(this.value);
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        this.value = buf.readLong();
    }

    @Override
    public void copyFrom(WalletComponent other, net.minecraft.core.HolderLookup.Provider registries) {
        this.value = other.value;
    }
}