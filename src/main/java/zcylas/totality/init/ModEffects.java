package zcylas.totality.init;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import zcylas.totality.Totality;
import zcylas.totality.effect.GlideEffect;
import zcylas.totality.effect.HexEffect;
import zcylas.totality.effect.LightningEffect;
import zcylas.totality.effect.SummoningSicknessEffect;

public class ModEffects {
    public static Holder<MobEffect> GLIDE;
    public static Holder<MobEffect> HEX;
    public static Holder<MobEffect> SHOCKED;
    public static Holder<MobEffect> SUMMONING_SICKNESS;

    public static void register() {
        GLIDE = net.minecraft.core.Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "glide"),
                GlideEffect.INSTANCE);

        HEX = net.minecraft.core.Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "hex"),
                HexEffect.INSTANCE);

        SHOCKED = net.minecraft.core.Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "shocked"),
                LightningEffect.INSTANCE);

        SUMMONING_SICKNESS = Registry.registerForHolder(
                BuiltInRegistries.MOB_EFFECT,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, "summoning_sickness"),
                SummoningSicknessEffect.INSTACE);

    }


}