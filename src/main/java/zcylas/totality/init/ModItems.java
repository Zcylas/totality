package zcylas.totality.init;

import zcylas.totality.init.items.*;

public class ModItems {


    public static void register() {
        EnergyItems.register();
        MagicItems.register();
        ToolItems.register();
        BasicWeaponItems.register();
        IngredientItems.register();
    }


    private ModItems() {}
}
