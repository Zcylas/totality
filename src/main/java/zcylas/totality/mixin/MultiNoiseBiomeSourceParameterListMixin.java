package zcylas.totality.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.worldgen.TotalityOverworldClimate;

/**
 * The one mixin this mod's Overworld biome injection needs — see
 * {@link TotalityOverworldClimate}'s class doc for why this targets the constructor rather than
 * {@code MultiNoiseBiomeSourceParameterLists.bootstrap} (a datagen-only method that's dead code at
 * real runtime). Every route that builds an Overworld {@code MultiNoiseBiomeSourceParameterList}
 * — datapack decode of {@code {"preset": "minecraft:overworld"}}, or datagen — goes through this
 * constructor, so substituting the resolved {@code parameters} field here after construction
 * (rather than trying to fabricate/hijack a {@code Preset} beforehand) covers all of them. The
 * Nether entry is untouched (the {@code preset == Preset.OVERWORLD} check only matches once).
 */
@Mixin(MultiNoiseBiomeSourceParameterList.class)
public abstract class MultiNoiseBiomeSourceParameterListMixin {

    @Mutable
    @Shadow
    @Final
    private Climate.ParameterList<Holder<Biome>> parameters;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void totality$injectOverworldPoints(
            MultiNoiseBiomeSourceParameterList.Preset preset, HolderGetter<Biome> biomeGetter, CallbackInfo ci) {
        if (preset == MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD) {
            this.parameters = TotalityOverworldClimate.buildParameterList(biomeGetter);
        }
    }
}
