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
        translationBuilder.add("item.totality.novice_grimoire", "Novice Grimoire");
        translationBuilder.add("item.totality.apprentice_grimoire", "Apprentice Grimoire");
        translationBuilder.add("item.totality.archmage_grimoire", "Archmage Grimoire");
        //Basic Weapons
            //Shuriken
        translationBuilder.add("item.totality.copper_shuriken", "Copper Shuriken");
        translationBuilder.add("item.totality.iron_shuriken", "Iron Shuriken");
        translationBuilder.add("item.totality.gold_shuriken", "Gold Shuriken");
        translationBuilder.add("item.totality.diamond_shuriken", "Diamond Shuriken");
        translationBuilder.add("item.totality.netherite_shuriken", "Netherite Shuriken");
        //Ores
        translationBuilder.add("block.totality.tin_ore", "Tin Ore");
        translationBuilder.add("block.totality.deepslate_tin_ore", "Deepslate Tin Ore");
        translationBuilder.add("block.totality.graphite_ore", "Graphite Ore");
        translationBuilder.add("block.totality.deepslate_graphite_ore", "Deepslate Graphite Ore");
        translationBuilder.add("block.totality.lead_ore", "Lead Ore");
        translationBuilder.add("block.totality.deepslate_lead_ore", "Deepslate Lead Ore");
        translationBuilder.add("block.totality.silver_ore", "Silver Ore");
        translationBuilder.add("block.totality.deepslate_silver_ore", "Deepslate Silver Ore");
        translationBuilder.add("block.totality.vibranium_ore", "Vibranium Ore");
        translationBuilder.add("block.totality.deepslate_vibranium_ore", "Deepslate Vibranium Ore");
        translationBuilder.add("block.totality.ruby_ore", "Ruby Ore");
        translationBuilder.add("block.totality.deepslate_ruby_ore", "Deepslate Ruby Ore");



        //Energy Tools
        translationBuilder.add("item.totality.wrench", "Wrench");
        //Keys
        translationBuilder.add("key.totality.open_grimoire", "Open Grimoire");
        translationBuilder.add("key.category.totality.totality", "Totality");
        translationBuilder.add("key.totality.open_radial", "Radial Spell Selector");
        //Ingredients
            //Gears
        translationBuilder.add("item.totality.copper_gear", "Copper Gear");
        translationBuilder.add("item.totality.iron_gear", "Iron Gear");
        translationBuilder.add("item.totality.gold_gear", "Gold Gear");
        translationBuilder.add("item.totality.diamond_gear", "Diamond Gear");
        translationBuilder.add("item.totality.netherite_gear", "Netherite Gear");
            //Raw Items
        translationBuilder.add("item.totality.graphite", "Graphite");
            //Rough Gems
        translationBuilder.add("item.totality.rough_ruby", "Rough Ruby");
        //Tooltips
            //Base Weapons
                //Shuriken
        translationBuilder.add("item.totality.shuriken.tooltip", "A razor-sharp throwing star. Deadly at range.");
        translationBuilder.add("item.totality.shuriken.damage", "Throw Damage: %s ❤");
    }
}
