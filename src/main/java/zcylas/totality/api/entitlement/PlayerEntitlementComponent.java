package zcylas.totality.api.entitlement;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

/**
 * Server-authoritative player entitlement component on the Totality component framework (canonical §3.1).
 *
 * <p>Only the {@link EntitlementLedger} is saved. The sync packet never carries the raw ledger: it carries
 * the owner's display view (non-hidden {@link EntitlementDisplaySnapshot}s of client-view types), so hidden
 * content, grant provenance and secret requirements never leave the server. There is no client-side
 * instance; the client reads {@code ClientEntitlementView}.
 */
public final class PlayerEntitlementComponent implements SyncedComponent, CopyableComponent<PlayerEntitlementComponent> {

    private final @Nullable ServerPlayer player;
    private PlayerEntitlementState state = new PlayerEntitlementState();

    public PlayerEntitlementComponent(@Nullable ServerPlayer player) {
        this.player = player;
    }

    public PlayerEntitlementState state() {
        return state;
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void readData(ValueInput input) {
        EntitlementLedger ledger = input.read("ledger", EntitlementLedger.CODEC).orElseGet(EntitlementLedger::new);
        state = new PlayerEntitlementState(ledger);
        // Registries are complete before any player loads: quarantine removed content, restore returned content.
        ledger.sortOrphans(EntitlementCatalog.INSTANCE::isRegisteredContent);
    }

    @Override
    public void writeData(ValueOutput output) {
        output.store("ledger", EntitlementLedger.CODEC, state.ledger());
    }

    // ── Respawn ───────────────────────────────────────────────────────────────

    /** Lossless copy (e.g. returning from the End). */
    @Override
    public void copyFrom(PlayerEntitlementComponent other, HolderLookup.Provider registries) {
        copyForRespawn(other, false);
    }

    /** Respawn copy; {@code died} drops UNTIL_DEATH grants. Provider grants are rebuilt after respawn. */
    public void copyForRespawn(PlayerEntitlementComponent other, boolean died) {
        state = other.state.copyForRespawn(died);
    }

    // ── Sync: display view only ───────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        EntitlementDisplaySnapshot.writeView(buf, state.revision(), EntitlementService.INSTANCE.displayView(recipient));
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        // Never instantiated client-side; ClientEntitlementView consumes the packet.
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer recipient) {
        return recipient == player;
    }
}
