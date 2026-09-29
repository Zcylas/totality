package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import zcylas.totality.api.core.rpgutils.rarity.ItemComponents;
import zcylas.totality.api.core.rpgutils.rarity.ItemRarity;
import zcylas.totality.api.core.rpgutils.rarity.RarityComponent;
import zcylas.totality.init.ModEntities;
import zcylas.totality.init.TotalityRegistry;

/** Spawn eggs for Totality creatures (listed in the vanilla Spawn Eggs creative tab). */
public final class CreatureItems {

    public static final SpawnEggItem FOREST_BOAR_SPAWN_EGG = TotalityRegistry.registerItem(
            "forest_boar_spawn_egg", SpawnEggItem::new, new Item.Properties().spawnEgg(ModEntities.FOREST_BOAR)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON)));

    private CreatureItems() {}

    public static void register() {
        net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents.modifyOutputEvent(
                net.minecraft.world.item.CreativeModeTabs.SPAWN_EGGS).register(output -> output.accept(FOREST_BOAR_SPAWN_EGG));
    }
}
