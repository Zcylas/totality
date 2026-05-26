package zcylas.totality.api.rpg.resources;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

/**
 * Stores all current player resources — stamina, mana, and future resources
 * like thirst, temperature, sanity, etc.
 *
 * This is the single source of truth for runtime resource values.
 * PlayerStaminaManager and PlayerManaManager are thin APIs over this component.
 */
public class PlayerResourceComponent implements SyncedComponent, CopyableComponent<PlayerResourceComponent> {

    private int stamina = -1; // -1 = not yet initialized, will be set to max on first access
    private int mana    = -1;
    // Future resources:
    // private int thirst = -1;
    // private float temperature = 20f;
    // private int sanity = -1;

    private final ServerPlayer player;

    public PlayerResourceComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── Stamina ───────────────────────────────────────────────────────────────

    public int getStamina() { return stamina; }

    public void setStamina(int value) {
        this.stamina = value;
    }

    public boolean isStaminaInitialized() { return stamina >= 0; }

    // ── Mana ──────────────────────────────────────────────────────────────────

    public int getMana() { return mana; }

    public void setMana(int value) {
        this.mana = value;
    }

    public boolean isManaInitialized() { return mana >= 0; }

    // ── Sync ──────────────────────────────────────────────────────────────────

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            ResourceComponents.RESOURCES.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player);
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(stamina);
        buf.writeInt(mana);
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        stamina = buf.readInt();
        mana    = buf.readInt();
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("stamina", stamina);
        output.putInt("mana",    mana);
    }

    @Override
    public void readData(ValueInput input) {
        stamina = input.getIntOr("stamina", -1);
        mana    = input.getIntOr("mana",    -1);
    }

    // ── Respawn copy ──────────────────────────────────────────────────────────

    @Override
    public void copyFrom(PlayerResourceComponent other, HolderLookup.Provider registries) {
        // On death, reset resources — don't copy current values
        this.stamina = -1;
        this.mana    = -1;
    }
}