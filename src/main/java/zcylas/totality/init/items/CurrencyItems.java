package zcylas.totality.init.items;

import net.minecraft.world.item.Item;
import zcylas.totality.api.currency.Denomination;
import zcylas.totality.init.TotalityRegistry;
import zcylas.totality.item.tools.CoinItem;

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

    private CurrencyItems() {}

    public static void register() {
        // triggers static field initialization
    }
}
