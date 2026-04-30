package zcylas.totality.init;

import net.minecraft.core.Direction;
import zcylas.totality.api.config.FaceConfig;
import zcylas.totality.api.energy.UEComponents;
import zcylas.totality.api.energy.UEStorage;
import zcylas.totality.blockentity.energy.EnergyCellBlockEntity;
import zcylas.totality.blockentity.generator.GeneratorBlockEntity;

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