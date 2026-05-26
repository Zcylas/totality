package zcylas.totality.api.ability.harvest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Registry of harvest handlers checked in registration order.
 * First handler whose canHarvest() returns true is used.
 */
public final class HarvestRegistry {

    private static final List<HarvestHandler> HANDLERS = new ArrayList<>();

    public static void register(HarvestHandler handler) {
        HANDLERS.add(handler);
    }

    public static List<HarvestHandler> handlers() {
        return Collections.unmodifiableList(HANDLERS);
    }

    private HarvestRegistry() {}
}