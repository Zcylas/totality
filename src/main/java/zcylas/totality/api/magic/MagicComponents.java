package zcylas.totality.api.magic;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import zcylas.totality.Totality;
import zcylas.totality.api.magic.GrimoireCaster;

public class MagicComponents {

    public static final DataComponentType<GrimoireCaster> GRIMOIRE_CASTER =
            DataComponentType.<GrimoireCaster>builder()
                    .persistent(GrimoireCaster.CODEC)
                    .networkSynchronized(GrimoireCaster.STREAM_CODEC)
                    .build();

    public static void register() {
        Registry.register(
                BuiltInRegistries.DATA_COMPONENT_TYPE,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "grimoire_caster"),
                GRIMOIRE_CASTER
        );
    }

    private MagicComponents() {}
}