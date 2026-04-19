package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider;
import net.minecraft.core.HolderLookup;

import java.util.concurrent.CompletableFuture;

public class ModEnglishLangProvider extends FabricLanguageProvider {
    public ModEnglishLangProvider(FabricPackOutput packOutput, CompletableFuture<HolderLookup.Provider> registryLookup) {
        super(packOutput, registryLookup);
    }

    @Override
    public void generateTranslations(HolderLookup.Provider provider, TranslationBuilder translationBuilder) {
        translationBuilder.add("block.totality.copper_tank", "Copper Tank");
        translationBuilder.add("block.totality.generator","Generator");
        translationBuilder.add("block.totality.copper_energy_cell","Copper Energy Cell");
        translationBuilder.add("item.totality.copper_battery","Copper Battery");
        translationBuilder.add("block.totality.copper_cable","Copper Cable");
        translationBuilder.add("item.totality.iron_battery", "Iron Battery");
        translationBuilder.add("item.totality.gold_battery", "Gold Battery");
        translationBuilder.add("item.totality.diamond_battery", "Diamond Battery");
        translationBuilder.add("item.totality.netherite_battery", "Netherite Battery");
        translationBuilder.add("item.totality.umbra_visor", "Umbra Visor");

    }
}
