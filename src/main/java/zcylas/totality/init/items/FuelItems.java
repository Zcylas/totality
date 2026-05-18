package zcylas.totality.init.items;

import net.fabricmc.fabric.api.registry.FuelValueEvents;
import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;

public class FuelItems {
    public static final Item TINY_COAL = TotalityRegistry.registerItem("tiny_coal", Item::new, new Item.Properties()
            .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
            .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.FUEL))
            .component(ItemComponents.getLore(), new LoreComponent("A small chunk of coal, barely enough to light a forge.")));


    public static void register() {
        FuelValueEvents.BUILD.register((builder, context) ->
                builder.add(TINY_COAL, 200));
    }
}
