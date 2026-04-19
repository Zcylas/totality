package zcylas.totality.init;

import zcylas.totality.api.energy.UEComponents;
import zcylas.totality.api.fluid.FluidComponents;

public class ModComponents {

    public static void register(){
        UEComponents.register();
        FluidComponents.register();
    }

    private ModComponents() {}

}
