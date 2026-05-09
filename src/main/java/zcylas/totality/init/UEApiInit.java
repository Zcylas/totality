package zcylas.totality.init;

import zcylas.totality.api.industrial.energy.UEComponents;

public class UEApiInit {

    public static void register() {
        UEComponents.SIDED_STORAGE.registerForBlockEntity(
                (be, direction) -> be.energy.getSideStorage(direction),
                ModBlockEntities.GENERATOR
        );

        UEComponents.SIDED_STORAGE.registerForBlockEntity(
                (be, direction) -> be.energy.getSideStorage(direction),
                ModBlockEntities.ENERGY_CELL
        );

        UEComponents.SIDED_STORAGE.registerForBlockEntity(
                (be, direction) -> be.energy.getSideStorage(direction),
                ModBlockEntities.ELECTRIC_FURNACE
        );
    }

    private UEApiInit() {}
}