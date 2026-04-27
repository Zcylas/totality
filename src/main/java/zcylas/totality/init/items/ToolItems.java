package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;

public class ToolItems {
    //Energy Tools
    public static final Item WRENCH = TotalityRegistry.registerItem("wrench", Item::new, new Item.Properties());

    public static void register() {}

    private ToolItems() {}
}
