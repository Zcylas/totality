package zcylas.totality.init;

import zcylas.totality.api.ability.harvest.HarvestRegistry;
import zcylas.totality.api.ability.harvest.handler.*;

/**
 * Registers all harvest handlers in priority order.
 * More specific handlers must come before generic ones.
 */
public final class TotalityHarvestHandlers {

    public static void register() {
        HarvestRegistry.register(new CropHarvestHandler());
        HarvestRegistry.register(new SweetBerryHarvestHandler());
        HarvestRegistry.register(new NetherWartHarvestHandler());
        HarvestRegistry.register(new CocoaHarvestHandler());
        HarvestRegistry.register(new CaveVinesHarvestHandler());
        HarvestRegistry.register(new MountainFlowerHarvestHandler());
        HarvestRegistry.register(new GenericTaggedHarvestHandler()); // must be last
    }

    private TotalityHarvestHandlers() {}
}