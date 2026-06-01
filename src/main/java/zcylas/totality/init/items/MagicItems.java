package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.magic.GrimoireItem;

public class MagicItems {

    public static final GrimoireItem NOVICE_GRIMOIRE = TotalityRegistry.registerItem(
            "novice_grimoire",
            properties -> new GrimoireItem(properties, 1),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A simple tome given to those who have just begun to hear the whisper of the arcane. Most never open a second one."
                    ))
    );

    public static final GrimoireItem APPRENTICE_GRIMOIRE = TotalityRegistry.registerItem(
            "apprentice_grimoire",
            properties -> new GrimoireItem(properties, 2),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.EPIC))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "The runes within grow more complex, more demanding. Only those who survived the first grimoire deserve to hold this one."
                    ))
    );

    public static final GrimoireItem ARCHMAGE_GRIMOIRE = TotalityRegistry.registerItem(
            "archmage_grimoire",
            properties -> new GrimoireItem(properties, 3),
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.LEGENDARY))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.MAGICAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Reserved for those who have mastered the arcane arts. This grimoire unlocks the most powerful runes known to mankind, capable of reshaping reality itself."
                    ))
    );

    public static void register() {}

    private MagicItems() {}

}
