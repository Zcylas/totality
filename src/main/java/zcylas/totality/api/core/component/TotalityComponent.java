package zcylas.totality.api.core.component;

import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Base interface for all Totality components.
 * Implement this to create a component that can be attached to a provider.
 */
public interface TotalityComponent {
    void readData(ValueInput input);
    void writeData(ValueOutput output);
}