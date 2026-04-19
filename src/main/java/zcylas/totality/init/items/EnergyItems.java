package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.energy.BatteryItem;
import zcylas.totality.item.energy.UmbraVisorItem;

public class EnergyItems {

    public static final BatteryItem COPPER_BATTERY = TotalityRegistry.registerItem(
            "copper_battery",
            properties -> new BatteryItem(48_000, 32, 32, properties),
            new net.minecraft.world.item.Item.Properties()
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
