package zcylas.totality.api.component;

import net.minecraft.core.HolderLookup;

/**
 * Implement this for efficient direct copying instead of NBT round-trip.
 * Used by RespawnStrategy when copying components across player death.
 */
public interface CopyableComponent<C extends TotalityComponent> extends TotalityComponent {
    void copyFrom(C other, HolderLookup.Provider registries);
}