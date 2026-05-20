package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.api.ritual.ChalkColor;
import zcylas.totality.block.ritual.ChalkBlock;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.ritual.ChalkItem;

public class RitualItems {
    public static final Item INCENSE = TotalityRegistry.registerItem(
            "incense",
            Item::new,
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "A bundle of fragrant incense used to prepare ritual spaces. Its smoke carries intent through the air."
                    ))
    );
    public static final Item WHITE_CHALK = TotalityRegistry.registerItem(
            "white_chalk",
            props -> new ChalkItem(props, ChalkColor.WHITE),
            new Item.Properties()
                    .durability(32)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.COMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "White chalk used to draw ritual glyphs. Right-click the ground to place, right-click a glyph to cycle its symbol."
                    ))
    );

    public static final Item GOLD_CHALK = TotalityRegistry.registerItem(
            "gold_chalk",
            props -> new ChalkItem(props, ChalkColor.GOLD),
            new Item.Properties()
                    .durability(32)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Gold chalk for intermediate ritual glyphs."
                    ))
    );

    public static final Item BLUE_CHALK = TotalityRegistry.registerItem(
            "blue_chalk",
            props -> new ChalkItem(props, ChalkColor.BLUE),
            new Item.Properties()
                    .durability(32)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.UNCOMMON))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Blue chalk for arcane ritual glyphs."
                    ))
    );

    public static final Item PURPLE_CHALK = TotalityRegistry.registerItem(
            "purple_chalk",
            props -> new ChalkItem(props, ChalkColor.PURPLE),
            new Item.Properties()
                    .durability(32)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Purple chalk for sacrifice rituals. Handle with care."
                    ))
    );

    public static final Item RED_CHALK = TotalityRegistry.registerItem(
            "red_chalk",
            props -> new ChalkItem(props, ChalkColor.RED),
            new Item.Properties()
                    .durability(32)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Red chalk for blood rituals. Costs more than just chalk."
                    ))
    );
    public static final Item RESIDUUM_CHALK = TotalityRegistry.registerItem(
            "residuum_chalk",
            props -> new ChalkItem(props, ChalkColor.RESIDUUM),
            new Item.Properties()
                    .durability(32)
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.RARE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.RITUAL))
                    .component(ItemComponents.getLore(), new LoreComponent(
                            "Chalk infused with residuum. Marks the tier of a ritual."
                    ))
    );

    public static void register() {}

    private RitualItems() {}
}
