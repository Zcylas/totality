package zcylas.totality.init.items;

import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.magic.GrimoireItem;

public class MagicItems {

    public static final GrimoireItem GRIMOIRE = TotalityRegistry.registerItem(
            "grimoire",
            properties -> new GrimoireItem(properties),
            new net.minecraft.world.item.Item.Properties()
    );

    public static void register() {}

    private MagicItems() {}

}
