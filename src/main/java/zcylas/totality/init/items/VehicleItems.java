package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.vehicle.SkateboardItem;

/** Rideable Totality vehicles (listed in the Equipment creative tab; no recipe yet). */
public final class VehicleItems {

    /** Creative Test D: the default skateboard (development-accessible: creative tab and /give only). */
    public static final SkateboardItem SKATEBOARD = TotalityRegistry.registerItem(
            "skateboard", SkateboardItem::new, new Item.Properties().stacksTo(1)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON)));

    private VehicleItems() {}

    public static void register() {}
}
