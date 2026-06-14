package zcylas.totality.api.dialogue;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import zcylas.totality.api.core.component.CopyableComponent;
import zcylas.totality.api.core.component.TotalityComponent;

import java.util.HashMap;
import java.util.Map;

public class NarrativeFlagsComponent implements TotalityComponent, CopyableComponent<NarrativeFlagsComponent> {

    private final Map<String, Integer> flags = new HashMap<>();

    public int getFlag(String key) {
        return flags.getOrDefault(key, 0);
    }

    public void setFlag(String key, int value) {
        if (value == 0) flags.remove(key);
        else flags.put(key, value);
    }

    public void incrementFlag(String key, int amount) {
        int next = flags.getOrDefault(key, 0) + amount;
        if (next == 0) flags.remove(key);
        else flags.put(key, next);
    }

    public boolean hasFlag(String key) {
        return flags.getOrDefault(key, 0) >= 1;
    }

    public void clearFlag(String key) {
        flags.remove(key);
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
    public void copyFrom(NarrativeFlagsComponent other, HolderLookup.Provider registries) {
        flags.clear();
        flags.putAll(other.flags);
    }
}
