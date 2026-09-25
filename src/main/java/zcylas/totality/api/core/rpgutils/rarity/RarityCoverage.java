package zcylas.totality.api.core.rpgutils.rarity;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import zcylas.totality.Totality;

import java.util.ArrayList;
import java.util.List;

/**
 * Every registered Totality item must author an {@link ItemRarity} (Tooltip V2 Pass 1) — no custom item may silently
 * fall through without one. Default item components are only bound once server data has loaded (MC 26.2), so the
 * check runs when a server starts: in a development environment a missing rarity fails startup with the offending
 * registry ids; in production it is logged and never crashes players.
 */
public final class RarityCoverage {

    /** Registry ids of Totality items whose default components carry no authored rarity. Components must be bound. */
    public static List<Identifier> missingAuthoredRarity() {
        List<Identifier> missing = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!Totality.MOD_ID.equals(id.getNamespace())) continue;
            if (!item.components().has(ItemComponents.RARITY)) missing.add(id);
        }
        return missing;
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            List<Identifier> missing = missingAuthoredRarity();
            if (missing.isEmpty()) return;
            String message = "Totality items registered without an authored ItemRarity (add"
                    + " .component(ItemComponents.RARITY, new RarityComponent(...)) to their Item.Properties): " + missing;
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) throw new IllegalStateException(message);
            Totality.LOGGER.error(message);
        });
    }

    private RarityCoverage() {}
}
