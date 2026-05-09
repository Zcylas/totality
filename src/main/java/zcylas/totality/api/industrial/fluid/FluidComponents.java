package zcylas.totality.api.industrial.fluid;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;

public class FluidComponents {

    /**
     * Stores the current mode of a handheld fluid tank item.
     * true = INSERT, false = EXTRACT
     */
    public static final DataComponentType<Boolean> FLUID_TANK_MODE =
            DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build();

    public static void register() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath("totality", "fluid_tank_mode"),
                FLUID_TANK_MODE
        );
    }

    private FluidComponents() {}
}