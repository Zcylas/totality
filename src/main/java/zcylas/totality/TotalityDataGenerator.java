package zcylas.totality;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import net.minecraft.core.RegistrySetBuilder;
import net.minecraft.core.registries.Registries;
import org.jspecify.annotations.NonNull;
import zcylas.totality.datagen.*;
import zcylas.totality.worldgen.ModConfiguredFeatures;
import zcylas.totality.worldgen.ModPlacedFeatures;

public class TotalityDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();

		pack.addProvider(ModEnglishLangProvider::new);
		pack.addProvider(ModModelProvider::new);
		pack.addProvider(ModRecipeProvider::new);
		pack.addProvider(ModWorldGenProvider::new);
		pack.addProvider(ModBlockTagProvider::new);
		pack.addProvider(ModBlockLootTableProvider::new);
		pack.addProvider(ModItemTagProvider::new);
	}

	@Override
	public void buildRegistry(@NonNull RegistrySetBuilder registryBuilder) {
		registryBuilder
				.add(Registries.CONFIGURED_FEATURE, ModConfiguredFeatures::bootstrap)
				.add(Registries.PLACED_FEATURE, ModPlacedFeatures::bootstrap);
	}
}
