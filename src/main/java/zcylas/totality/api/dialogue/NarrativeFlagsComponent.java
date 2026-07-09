package zcylas.totality.api.dialogue;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.ComponentProvider;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.SyncedComponent;

import java.util.HashMap;
import java.util.Map;

public class NarrativeFlagsComponent implements SyncedComponent, CopyableComponent<NarrativeFlagsComponent> {

    private final Map<String, Integer> flags = new HashMap<>();
    private final ServerPlayer player;

    public NarrativeFlagsComponent(ServerPlayer player) {
        this.player = player;
    }

    public int getFlag(String key) {
        return flags.getOrDefault(key, 0);
    }

    public void setFlag(String key, int value) {
        if (value == 0) flags.remove(key);
        else flags.put(key, value);
        sync();
    }

    public void incrementFlag(String key, int amount) {
        int next = flags.getOrDefault(key, 0) + amount;
        if (next == 0) flags.remove(key);
        else flags.put(key, next);
        sync();
    }

    public boolean hasFlag(String key) {
        return flags.getOrDefault(key, 0) >= 1;
    }

    public void clearFlag(String key) {
        flags.remove(key);
        sync();
    }

    private void sync() {
        if (player != null && !player.level().isClientSide()) {
            DialogueComponents.FLAGS.sync((ComponentProvider) player);
        }
    }

    @Override
    public void writeData(ValueOutput output) {
        output.putInt("nf_count", flags.size());
        int i = 0;
        for (Map.Entry<String, Integer> e : flags.entrySet()) {
            output.putString("nf_k" + i, e.getKey());
            output.putInt("nf_v" + i, e.getValue());
            i++;
        }
    }

    @Override
    public void readData(ValueInput input) {
        flags.clear();
        int count = input.getIntOr("nf_count", 0);
        for (int i = 0; i < count; i++) {
            String key = input.getStringOr("nf_k" + i, "");
            int value = input.getIntOr("nf_v" + i, 0);
            if (!key.isEmpty()) flags.put(key, value);
        }
    }

    @Override
    public void writeSyncPacket(RegistryFriendlyByteBuf buf, ServerPlayer recipient) {
        buf.writeVarInt(flags.size());
        for (Map.Entry<String, Integer> e : flags.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeInt(e.getValue());
        }
    }

    @Override
    public void applySyncPacket(RegistryFriendlyByteBuf buf) {
        flags.clear();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            String key = buf.readUtf();
            int value = buf.readInt();
            flags.put(key, value);
        }
    }

    @Override
    public void copyFrom(NarrativeFlagsComponent other, HolderLookup.Provider registries) {
        flags.clear();
        flags.putAll(other.flags);
    }
}
