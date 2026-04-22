package zcylas.totality.init;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import zcylas.totality.Totality;
import zcylas.totality.effect.GlideEffect;

public class ModEffects {
    public static Holder<MobEffect> GLIDE;

    public static void register() {
        GLIDE = net.minecraft.core.Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "glide"),
                GlideEffect.INSTANCE);
    }
}