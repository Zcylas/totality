package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.core.rpgutils.WeightComponent;
import zcylas.totality.api.core.rpgutils.rarity.*;
import zcylas.totality.api.economy.currency.Denomination;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.tools.CoinItem;
import zcylas.totality.item.tools.CreditsItem;

public class CurrencyItems {
    public static final CoinItem COPPER_COIN = TotalityRegistry.registerItem("copper_coin",
            properties -> new CoinItem(Denomination.COPPER, properties),
            new Item.Properties().stacksTo(99)
    );
    public static final CoinItem SILVER_COIN = TotalityRegistry.registerItem("silver_coin",
            properties -> new CoinItem(Denomination.SILVER, properties),
            new Item.Properties().stacksTo(99)
    );
    public static final CoinItem GOLD_COIN = TotalityRegistry.registerItem("gold_coin",
            properties -> new CoinItem(Denomination.GOLD, properties),
            new Item.Properties().stacksTo(99)
    );
    public static final CoinItem PLATINUM_COIN = TotalityRegistry.registerItem("platinum_coin",
            properties -> new CoinItem(Denomination.PLATINUM, properties),
            new Item.Properties().stacksTo(99)
    );

    public static final CreditsItem CREDITS = TotalityRegistry.registerItem("credits",
            CreditsItem::new,
            new Item.Properties()
                    .component(ItemComponents.RARITY, new RarityComponent(ItemRarity.CRUDE))
                    .component(ItemComponents.ITEM_TYPE, new ItemTypeComponent(ItemType.CURRENCY))
                    .component(ItemComponents.WEIGHT, new WeightComponent(0))
    );

    private CurrencyItems() {}

    public static void register() {
        // triggers static field initialization
    }
}
