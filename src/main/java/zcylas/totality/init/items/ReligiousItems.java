package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;

public class ReligiousItems {
    public static final Item BLESSED_INCENSE = TotalityRegistry.registerItem(
            "blessed_incense",
            Item::new,
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.BLESSED))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A sacred incense used in rites and prayer. Its gentle smoke carries blessings beyond the mortal veil."
                    ))
    );

    public static void register() {}

    private ReligiousItems() {}
}
