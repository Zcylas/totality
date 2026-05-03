package zcylas.totality.init;

import zcylas.totality.api.alchemy.AlchemyComponents;
import zcylas.totality.api.currency.CurrencyComponents;
import zcylas.totality.api.energy.UEComponents;
import zcylas.totality.api.fluid.FluidComponents;
import zcylas.totality.api.magic.MagicComponents;

public class ModComponents {

    public static void register(){
        UEComponents.register();
        FluidComponents.register();
        MagicComponents.register();
        CurrencyComponents.register();
        AlchemyComponents.register();
    }

    private ModComponents() {}

}
