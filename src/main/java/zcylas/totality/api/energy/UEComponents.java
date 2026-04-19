package zcylas.totality.api.energy;


import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;

public class UEComponents {

    public static final DataComponentType<Long> ENERGY = DataComponentType.<Long>builder()
            .persistent(nonNegativeLong())
            .networkSynchronized(ByteBufCodecs.VAR_LONG)
            .build();

    public static final DataComponentType<Boolean> BATTERY_ACTIVE = DataComponentType.<Boolean>builder()
            .persistent(Codec.BOOL)
            .networkSynchronized(ByteBufCodecs.BOOL)
            .build();

    public static final BlockApiLookup<UEStorage, Direction> SIDED_STORAGE =
            BlockApiLookup.get(
                    Identifier.fromNamespaceAndPath(Totality.MOD_ID, "ue_storage"),
                    UEStorage.class,
                    Direction.class
            );

    public static void register() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "energy"),
                ENERGY
        );

        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "battery_active"),
                BATTERY_ACTIVE
        );
    }

    private static Codec<Long> nonNegativeLong() {
        return Codec.LONG.validate(value -> value >= 0
                ? DataResult.success(value)
                : DataResult.error(() -> "Energy value must be non-negative: " + value)
        );
    }

    private UEComponents() {}

}
