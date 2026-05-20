package zcylas.totality.api.magic.rune;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;
import zcylas.totality.networking.magic.rune.ClientRuneKnowledgeManager;

import java.util.HashSet;
import java.util.Set;

/**
 * Player component that tracks which runes the player has learned.
 * Storage: Set<String> of rune IDs (e.g. "break", "touch", "amplify")
 */
public class RuneKnowledgeComponent implements SyncedComponent, CopyableComponent<RuneKnowledgeComponent> {

    private final Set<String> knownRunes = new HashSet<>();
    private final ServerPlayer player;

    public RuneKnowledgeComponent(ServerPlayer player) {
        this.player = player;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** Returns true if this was a new discovery. */
    public boolean learnRune(String runeId) {
        return knownRunes.add(runeId);
    }

    public boolean knowsRune(String runeId) {
        return knownRunes.contains(runeId);
    }

    public Set<String> getKnownRunes() {
        return Set.copyOf(knownRunes);
    }

    public void sync() {
        if (player != null && !player.level().isClientSide()) {
            RuneComponents.KNOWLEDGE.sync(
                    (zcylas.totality.api.core.component.ComponentProvider) player
            );
        }
    }

    // ── Sync ──────────────────────────────────────────────────────────────────

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeInt(knownRunes.size());
        for (String runeId : knownRunes) {
            buf.writeUtf(runeId);
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        knownRunes.clear();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            knownRunes.add(buf.readUtf());
        }
        ClientRuneKnowledgeManager.apply(knownRunes);
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    public void writeData(ValueOutput output) {
        String joined = String.join("|", knownRunes);
        output.putString("rk_runes", joined);
    }

    @Override
    public void readData(ValueInput input) {
        knownRunes.clear();
        String data = input.getStringOr("rk_runes", "");
        if (!data.isEmpty()) {
            for (String id : data.split("\\|")) {
                if (!id.isBlank()) knownRunes.add(id);
            }
        }
    }

    // ── Respawn copy ──────────────────────────────────────────────────────────

    @Override
    public void copyFrom(RuneKnowledgeComponent other, HolderLookup.Provider registries) {
        this.knownRunes.clear();
        this.knownRunes.addAll(other.knownRunes);
    }
}