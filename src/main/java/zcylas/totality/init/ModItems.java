package zcylas.totality.init;

import zcylas.totality.init.items.EnergyItems;
import zcylas.totality.init.items.MagicItems;

public class ModItems {


    public static void register() {
        EnergyItems.register();
        MagicItems.register();
    }


    private ModItems() {}
}
