package zcylas.totality.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import zcylas.totality.Totality;

public class ModSounds {

    public static final SoundEvent SHURIKEN_THROW = register("item.shuriken.throw");

    private static SoundEvent register(String name) {
        Identifier id = Identifier.fromNamespaceAndPath(Totality.MOD_ID, name);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    public static void register() {}

}
