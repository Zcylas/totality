package zcylas.totality.init;

import zcylas.totality.init.items.*;

public class ModItems {


    public static void register() {
        EnergyItems.register();
        MagicItems.register();
        ToolItems.register();
        BasicWeaponItems.register();
        BleachItems.register();
        IngredientItems.register();
        SpellComponentItems.init();
        CurrencyItems.register();
        SKIngredientItems.register();
        PotionItems.register();
        DndPotionItems.register();
        FuelItems.register();
        ReligiousItems.register();
        RitualItems.register();
        RuneItems.register();
    }


    private ModItems() {}
}
