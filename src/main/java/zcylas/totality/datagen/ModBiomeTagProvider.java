package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.NonNull;
import zcylas.totality.init.ModTags;
import zcylas.totality.worldgen.ModBiomes;

import java.util.concurrent.CompletableFuture;

public class ModBiomeTagProvider extends FabricTagsProvider<Biome> {
    public ModBiomeTagProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, Registries.BIOME, registriesFuture);
    }

    @Override
    protected void addTags(HolderLookup.@NonNull Provider provider) {
        builder(BiomeTags.IS_OVERWORLD).add(ModBiomes.FLOODED_FOREST);
        builder(BiomeTags.IS_FOREST).add(ModBiomes.FLOODED_FOREST);
        // Slime spawning needs this tag AND an explicit spawner entry AND the SURFACE_SLIME_SPAWN_CHANCE
        // attribute together — the attribute alone does nothing without this tag (see ModBiomes).
        builder(BiomeTags.ALLOWS_SURFACE_SLIME_SPAWNS).add(ModBiomes.FLOODED_FOREST);
        builder(BiomeTags.SPAWNS_WARM_VARIANT_FROGS).add(ModBiomes.FLOODED_FOREST);
        builder(ModTags.IS_FLOODED_BIOME).add(ModBiomes.FLOODED_FOREST);
    }

    @Override
    public @NonNull String getName() {
        return "Totality Biome Tags";
    }
}
