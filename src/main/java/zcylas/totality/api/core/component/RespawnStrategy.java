package zcylas.totality.api.core.component;

import net.minecraft.core.HolderLookup;

@FunctionalInterface
public interface RespawnStrategy<C extends TotalityComponent> {

    /**
     * @param lossless true when the player's full data is transferred (e.g. End return)
     * @param keepInventory true when the keepInventory gamerule is active
     */
    void onRespawn(C from, C to, HolderLookup.Provider registries, boolean lossless, boolean keepInventory);

    RespawnStrategy<TotalityComponent> ALWAYS_COPY =
            (from, to, registries, lossless, keepInventory) -> copy(from, to, registries);

    RespawnStrategy<TotalityComponent> LOSSLESS_ONLY =
            (from, to, registries, lossless, keepInventory) -> { if (lossless) copy(from, to, registries); };

    RespawnStrategy<TotalityComponent> WITH_INVENTORY =
            (from, to, registries, lossless, keepInventory) -> { if (lossless || keepInventory) copy(from, to, registries); };

    RespawnStrategy<TotalityComponent> NEVER_COPY =
            (from, to, registries, lossless, keepInventory) -> { };

    @SuppressWarnings("unchecked")
    static <C extends TotalityComponent> void copy(C from, C to, HolderLookup.Provider registries) {
        if (to instanceof CopyableComponent copyable) {
            copyable.copyFrom(from, registries);
            return;
        }
        throw new UnsupportedOperationException(
                "Component " + from.getClass().getName() +
                        " does not implement CopyableComponent. Implement copyFrom() to support respawn copying."
        );
    }
}