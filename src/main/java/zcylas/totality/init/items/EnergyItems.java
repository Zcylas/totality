package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.WeightComponent;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.energy.BatteryItem;
import zcylas.totality.item.energy.UmbraVisorItem;

public class EnergyItems {

    public static final BatteryItem COPPER_BATTERY = TotalityRegistry.registerItem(
            "copper_battery",
            properties -> new BatteryItem(48_000, 32, 32, properties),
            new net.minecraft.world.item.Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.CRUDE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.BATTERY))
                    .component(ItemComponents.WEIGHT, new WeightComponent(2))
                    .component(ItemComponents.getLore(), new LoreComponent("A rudimentary energy cell cobbled together from copper coils and raw ore. It holds a charge, barely."))
    );

    public static final BatteryItem IRON_BATTERY = TotalityRegistry.registerItem(
            "iron_battery",
            properties -> new BatteryItem(320_000, 32, 32, properties),
            new Item.Properties()
    );

    public static final BatteryItem GOLD_BATTERY = TotalityRegistry.registerItem(
            "gold_battery",
            properties -> new BatteryItem(128_000, 128, 128, properties),
            new Item.Properties()
    );

    public static final BatteryItem DIAMOND_BATTERY = TotalityRegistry.registerItem(
            "diamond_battery",
            properties -> new BatteryItem(1_000_000, 256, 256, properties),
            new Item.Properties()
    );

    public static final BatteryItem NETHERITE_BATTERY = TotalityRegistry.registerItem(
            "netherite_battery",
            properties -> new BatteryItem(5_000_000, 512, 512, properties),
            new Item.Properties()
    );

    public static final UmbraVisorItem UMBRA_VISOR = TotalityRegistry.registerItem(
            "umbra_visor",
            properties -> new UmbraVisorItem(properties, 32_000, 32, 32),
            new Item.Properties()
    );

    public static void register() {}

    private EnergyItems() {}

}
