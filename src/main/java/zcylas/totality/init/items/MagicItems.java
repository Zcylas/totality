package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.magic.GrimoireItem;

public class MagicItems {

    public static final GrimoireItem NOVICE_GRIMOIRE = TotalityRegistry.registerItem(
            "novice_grimoire",
            properties -> new GrimoireItem(properties, 1),
            new Item.Properties()
    );

    public static final GrimoireItem APPRENTICE_GRIMOIRE = TotalityRegistry.registerItem(
            "apprentice_grimoire",
            properties -> new GrimoireItem(properties, 2),
            new Item.Properties()
    );

    public static final GrimoireItem ARCHMAGE_GRIMOIRE = TotalityRegistry.registerItem(
            "archmage_grimoire",
            properties -> new GrimoireItem(properties, 3),
            new Item.Properties()
    );

    public static void register() {}

    private MagicItems() {}

}
