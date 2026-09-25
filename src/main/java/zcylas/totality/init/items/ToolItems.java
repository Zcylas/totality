package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;

public class ToolItems {
    //Energy Tools
    public static final Item WRENCH = TotalityRegistry.registerItem("wrench", Item::new, new Item.Properties().component(ItemComponents.RARITY, new RarityComponent(ItemRarity.CRUDE)));

    public static void register() {}

    private ToolItems() {}
}
