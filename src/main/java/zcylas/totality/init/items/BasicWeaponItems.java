package zcylas.totality.init.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.base_weapons.ShurikenItem;

public class BasicWeaponItems {

    public static final ShurikenItem COPPER_SHURIKEN = TotalityRegistry.registerItem(
            "copper_shuriken",
            properties -> new ShurikenItem(properties, Dice.D4, 1),
            new Item.Properties()
                    .durability(16)
                    .repairable(ToolMaterial.COPPER.repairItems())
                    .enchantable(ToolMaterial.COPPER.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.WEAPON))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Every shadow walker begins with copper. Light, humble, and sharp enough to teach the first lesson — distance is survival."
                    ))
    );

    public static final ShurikenItem IRON_SHURIKEN = TotalityRegistry.registerItem(
            "iron_shuriken",
            properties -> new ShurikenItem(properties, Dice.D4, 2),
            new Item.Properties()
                    .durability(32)
                    .repairable(ToolMaterial.IRON.repairItems())
                    .enchantable(ToolMaterial.IRON.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.WEAPON))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Forged for those who have survived their first lesson. It does not shine. It does not impress. It simply kills."
                    ))
    );

    public static final ShurikenItem GOLD_SHURIKEN = TotalityRegistry.registerItem(
            "gold_shuriken",
            properties -> new ShurikenItem(properties, Dice.D4, 1),
            new Item.Properties()
                    .durability(8)
                    .repairable(ToolMaterial.GOLD.repairItems())
                    .enchantable(ToolMaterial.GOLD.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.WEAPON))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A contradiction — soft metal, deadly art. Those who scoff at its weakness have never seen one thrown by a master."
                    ))
    );

    public static final ShurikenItem DIAMOND_SHURIKEN = TotalityRegistry.registerItem(
            "diamond_shuriken",
            properties -> new ShurikenItem(properties, Dice.D6, 2),
            new Item.Properties()
                    .durability(64)
                    .repairable(ToolMaterial.DIAMOND.repairItems())
                    .enchantable(ToolMaterial.DIAMOND.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.WEAPON))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Edges that never dull. A blade that remembers every target it has ever touched."
                    ))
    );

    public static final ShurikenItem NETHERITE_SHURIKEN = TotalityRegistry.registerItem(
            "netherite_shuriken",
            properties -> new ShurikenItem(properties, Dice.D8, 2),
            new Item.Properties()
                    .durability(128)
                    .repairable(ToolMaterial.NETHERITE.repairItems())
                    .enchantable(ToolMaterial.NETHERITE.enchantmentValue())
                    .component(DataComponents.BREAK_SOUND, SoundEvents.ITEM_BREAK)
                    .useCooldown(0.4f)
                    .component(ItemComponents.getRarity(),   new RarityComponent(ItemRarity.EPIC))
                    .component(ItemComponents.getItemType(), new ItemTypeComponent(ItemType.WEAPON))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Born in ancient flame, unmoved by fire or time. The last thing many have seen was its glint in the dark."
                    ))
    );

    public static void register() {}

    private BasicWeaponItems() {}
}