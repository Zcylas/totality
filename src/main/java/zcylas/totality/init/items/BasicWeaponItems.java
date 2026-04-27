package zcylas.totality.init.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.base_weapons.ShurikenItem;

public class BasicWeaponItems {
    public static final ShurikenItem COPPER_SHURIKEN = TotalityRegistry.registerItem(
            "copper_shuriken",
            properties -> new ShurikenItem(properties, ToolMaterial.COPPER.attackDamageBonus() + 2.0f),
            new Item.Properties()
                    .durability(16)
                    .repairable(ToolMaterial.COPPER.repairItems())
                    .enchantable(ToolMaterial.COPPER.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
    );
    public static final ShurikenItem IRON_SHURIKEN = TotalityRegistry.registerItem(
            "iron_shuriken",
            properties -> new ShurikenItem(properties, ToolMaterial.IRON.attackDamageBonus() + 2.0f),
            new Item.Properties()
                    .durability(32)
                    .repairable(ToolMaterial.IRON.repairItems())
                    .enchantable(ToolMaterial.IRON.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
    );
    public static final ShurikenItem GOLD_SHURIKEN = TotalityRegistry.registerItem(
            "gold_shuriken",
            properties -> new ShurikenItem(properties, ToolMaterial.GOLD.attackDamageBonus() + 2.0f),
            new Item.Properties()
                    .durability(8)
                    .repairable(ToolMaterial.GOLD.repairItems())
                    .enchantable(ToolMaterial.GOLD.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
    );
    public static final ShurikenItem DIAMOND_SHURIKEN = TotalityRegistry.registerItem(
            "diamond_shuriken",
            properties -> new ShurikenItem(properties, ToolMaterial.DIAMOND.attackDamageBonus() + 2.0f),
            new Item.Properties()
                    .durability(64)
                    .repairable(ToolMaterial.DIAMOND.repairItems())
                    .enchantable(ToolMaterial.DIAMOND.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
    );
    public static final ShurikenItem NETHERITE_SHURIKEN = TotalityRegistry.registerItem(
            "netherite_shuriken",
            properties -> new ShurikenItem(properties, ToolMaterial.NETHERITE.attackDamageBonus() + 2.0f),
            new Item.Properties()
                    .durability(128)
                    .repairable(ToolMaterial.NETHERITE.repairItems())
                    .enchantable(ToolMaterial.NETHERITE.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
    );


    public static void register() {}

    private BasicWeaponItems() {}
}
