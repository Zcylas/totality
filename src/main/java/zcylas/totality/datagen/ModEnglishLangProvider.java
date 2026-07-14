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
        //Energy Blocks
            //Generators
        translationBuilder.add("block.totality.generator","Generator");
            //Cells
        translationBuilder.add("block.totality.copper_energy_cell","Copper Energy Cell");
            //Machines
        translationBuilder.add("block.totality.electric_furnace", "Electric Furnace");
            //Cables
        translationBuilder.add("block.totality.copper_cable","Copper Cable");
        //Functional Blocks
            //Skill Blocks
        translationBuilder.add("block.totality.apothecary_table", "Apothecary Table");
        //Energy Items
            //Batteries
        translationBuilder.add("item.totality.copper_battery","Copper Battery");
        translationBuilder.add("item.totality.iron_battery", "Iron Battery");
        translationBuilder.add("item.totality.gold_battery", "Gold Battery");
        translationBuilder.add("item.totality.diamond_battery", "Diamond Battery");
        translationBuilder.add("item.totality.netherite_battery", "Netherite Battery");
            //Gadgets
        translationBuilder.add("item.totality.umbra_visor", "Umbra Visor");
        //Magic Items
            //Grimoires
        translationBuilder.add("item.totality.novice_grimoire", "Novice Grimoire");
        translationBuilder.add("item.totality.apprentice_grimoire", "Apprentice Grimoire");
        translationBuilder.add("item.totality.archmage_grimoire", "Archmage Grimoire");
            //Rings
        translationBuilder.add("item.totality.ring_of_protection", "Ring of Protection");
            //Arcane Foci
        translationBuilder.add("item.totality.arcane_orb", "Arcane Orb");
        translationBuilder.add("item.totality.bard_guitar", "Bard's Guitar");
            //Rune Items - Blanks
        translationBuilder.add("item.totality.blank_form", "Blank Form Rune");
        translationBuilder.add("item.totality.blank_effect", "Blank Effect Rune");
        translationBuilder.add("item.totality.blank_augment", "Blank Augment Rune");
            // Rune Items — Forms
        translationBuilder.add("item.totality.rune_touch", "Touch Rune");
        translationBuilder.add("item.totality.rune_projectile", "Projectile Rune");
        translationBuilder.add("item.totality.rune_self", "Self Rune");
            // Rune Items — Effects
        translationBuilder.add("item.totality.rune_break", "Break Rune");
        translationBuilder.add("item.totality.rune_pickup", "Pickup Rune");
        translationBuilder.add("item.totality.rune_launch", "Launch Rune");
        translationBuilder.add("item.totality.rune_ignite", "Ignite Rune");
        translationBuilder.add("item.totality.rune_explosion", "Explosion Rune");
        translationBuilder.add("item.totality.rune_glide", "Glide Rune");
        translationBuilder.add("item.totality.rune_smelt", "Smelt Rune");
        translationBuilder.add("item.totality.rune_orbit", "Orbit Rune");
        translationBuilder.add("item.totality.rune_harm", "Harm Rune");
        translationBuilder.add("item.totality.rune_heal", "Heal Rune");
        translationBuilder.add("item.totality.rune_hex", "Hex Rune");
        translationBuilder.add("item.totality.rune_lightning", "Lightning Rune");
        translationBuilder.add("item.totality.rune_chaining", "Chaining Rune");
        translationBuilder.add("item.totality.rune_grow", "Grow Rune");
        translationBuilder.add("item.totality.rune_linger", "Linger Rune");
        translationBuilder.add("item.totality.rune_harvest", "Harvest Rune");
        translationBuilder.add("item.totality.rune_burst", "Burst Rune");
        translationBuilder.add("item.totality.rune_summon_undead", "Summon Undead Rune");
            // Rune Items — Augments
        translationBuilder.add("item.totality.rune_amplify", "Amplify Rune");
        translationBuilder.add("item.totality.rune_aoe", "Area of Effect Rune");
        translationBuilder.add("item.totality.rune_reduce_time", "Reduce Time Rune");
        translationBuilder.add("item.totality.rune_extend_time", "Extend Time Rune");
        translationBuilder.add("item.totality.rune_dampen", "Dampen Rune");
        translationBuilder.add("item.totality.rune_sensitive", "Sensitive Rune");
        translationBuilder.add("item.totality.rune_pierce", "Pierce Rune");
        translationBuilder.add("item.totality.rune_fortune", "Fortune Rune");
        translationBuilder.add("item.totality.rune_randomize", "Randomize Rune");
        translationBuilder.add("item.totality.rune_extract", "Extract Rune");
        translationBuilder.add("item.totality.rune_accelerate", "Accelerate Rune");
        translationBuilder.add("item.totality.rune_decelerate", "Decelerate Rune");
        translationBuilder.add("item.totality.rune_split", "Split Rune");
        //Special Weapons
            //Zanpakutō
        translationBuilder.add("item.totality.zanpakuto", "Zanpakutō");
        //Basic Weapons
            //Shuriken
        translationBuilder.add("item.totality.copper_shuriken", "Copper Shuriken");
        translationBuilder.add("item.totality.iron_shuriken", "Iron Shuriken");
        translationBuilder.add("item.totality.gold_shuriken", "Gold Shuriken");
        translationBuilder.add("item.totality.diamond_shuriken", "Diamond Shuriken");
        translationBuilder.add("item.totality.netherite_shuriken", "Netherite Shuriken");
        //Blocks
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
            //Whitestone
        translationBuilder.add("block.totality.whitestone", "Whitestone");
        translationBuilder.add("block.totality.flecked_whitestone", "Flecked Whitestone");
        translationBuilder.add("block.totality.polished_whitestone", "Polished Whitestone");
        translationBuilder.add("block.totality.polished_whitestone_bricks", "Polished Whitestone Bricks");
            //Natural Blocks
        translationBuilder.add("block.totality.limestone", "Limestone");
        translationBuilder.add("item.totality.limestone_chunk","Limestone Chunk");


        //Tools
            //Useful
        translationBuilder.add("item.totality.wrench", "Wrench");
            //Phone
        translationBuilder.add("item.totality.basic_copper_phone", "Basic Copper Phone");
            //Coins
        translationBuilder.add("item.totality.copper_coin", "Copper Coin");
        translationBuilder.add("item.totality.silver_coin", "Silver Coin");
        translationBuilder.add("item.totality.gold_coin", "Gold Coin");
        translationBuilder.add("item.totality.platinum_coin", "Platinum Coin");
        translationBuilder.add("item.totality.credits", "Credits");

        //Ingredients
            //Gears
        translationBuilder.add("item.totality.copper_gear", "Copper Gear");
        translationBuilder.add("item.totality.iron_gear", "Iron Gear");
        translationBuilder.add("item.totality.gold_gear", "Gold Gear");
        translationBuilder.add("item.totality.diamond_gear", "Diamond Gear");
        translationBuilder.add("item.totality.netherite_gear", "Netherite Gear");
            //Raw Items
        translationBuilder.add("item.totality.graphite", "Graphite");
        translationBuilder.add("item.totality.whitestone_chunk", "Whitestone Chunk");
        translationBuilder.add("item.totality.residuum_flecked_chunk", "Residuum-Flecked Chunk");
            //Rough Gems
        translationBuilder.add("item.totality.rough_ruby", "Rough Ruby");

        //Alchemy Ingredients
        translationBuilder.add("item.totality.salmon_roe","Salmon Roe");
        translationBuilder.add("block.totality.red_mountain_flower_bush","Red Mountain Flower Bush");
        translationBuilder.add("block.totality.blue_mountain_flower_bush","Blue Mountain Flower Bush");
        translationBuilder.add("block.totality.purple_mountain_flower_bush","Purple Mountain Flower Bush");
        translationBuilder.add("item.totality.red_mountain_flower","Red Mountain Flower");
        translationBuilder.add("item.totality.blue_mountain_flower","Blue Mountain Flower");
        translationBuilder.add("item.totality.purple_mountain_flower","Purple Mountain Flower");
        translationBuilder.add("item.totality.true_wheat_seeds","True Wheat Seeds");
        translationBuilder.add("item.totality.true_wheat","True Wheat");
        translationBuilder.add("item.totality.rock_warbler_egg","Rock Warbler Egg");
        translationBuilder.add("item.totality.garlic","Garlic");
        //Spell Materials
        translationBuilder.add("item.totality.bat_guano", "Bat Guano");
        translationBuilder.add("item.totality.sulphur_dust", "Sulphur Dust");
        //Fuel
        translationBuilder.add("item.totality.tiny_coal", "Tiny Coal");
        //Ritual Items
        translationBuilder.add("item.totality.blessed_incense", "Blessed Incense");
        translationBuilder.add("item.totality.incense","Incense");
        translationBuilder.add("item.totality.white_chalk", "White Chalk");
        translationBuilder.add("item.totality.gold_chalk", "Golden Chalk");
        translationBuilder.add("item.totality.blue_chalk", "Blue Chalk");
        translationBuilder.add("item.totality.purple_chalk", "Purple Chalk");
        translationBuilder.add("item.totality.red_chalk", "Red Chalk");
        translationBuilder.add("item.totality.residuum_chalk", "Residuum Chalk");
        //Ritual Blocks
        translationBuilder.add("block.totality.ritual_altar", "Ritual Altar");
        translationBuilder.add("block.totality.ritual_dais", "Ritual Dais");

        //Tooltips
            //Base Weapons
                //Shuriken
        translationBuilder.add("item.totality.shuriken.tooltip", "A razor-sharp throwing star. Deadly at range.");
        translationBuilder.add("item.totality.shuriken.damage", "Throw Damage: %s ❤");

        //Keys
        translationBuilder.add("key.totality.open_grimoire", "Open Grimoire");
        translationBuilder.add("key.category.totality.totality", "Totality");
        translationBuilder.add("key.totality.open_radial", "Radial Spell Selector");
        translationBuilder.add("key.totality.open_ability_radial", "Ability Radial Menu");
        translationBuilder.add("key.totality.movement_power", "Movement Power");
        //Effects
        translationBuilder.add("effect.totality.glide", "Glide");
        //Entities
        translationBuilder.add("entity.totality.totality_npc", "NPC");
        translationBuilder.add("entity.totality.banker", "Banker");
        translationBuilder.add("entity.totality.provisioner", "Provisioner");

        //Trading Screen (Phase 4)
        translationBuilder.add("totality.trading.title", "Trading");
        translationBuilder.add("totality.trading.archetype.merchant", "Merchant");
        translationBuilder.add("totality.trading.archetype.provisioner", "Provisioner");
        translationBuilder.add("totality.trading.merchant_credits", "Merchant Credits");
        translationBuilder.add("totality.trading.your_credits", "Your Credits");
        translationBuilder.add("totality.trading.buy", "Buy");
        translationBuilder.add("totality.trading.sell", "Sell");
        translationBuilder.add("totality.trading.buyback", "Buyback");
        translationBuilder.add("totality.trading.coming_later", "Coming Later");
        translationBuilder.add("totality.trading.price_each", "Price Each");
        translationBuilder.add("totality.trading.payout_each", "Payout Each");
        translationBuilder.add("totality.trading.stock", "Stock");
        translationBuilder.add("totality.trading.quantity", "Quantity");
        translationBuilder.add("totality.trading.total_cost", "Total Cost");
        translationBuilder.add("totality.trading.total_payout", "Total Payout");
        translationBuilder.add("totality.trading.sold_out", "Sold Out");
        translationBuilder.add("totality.trading.select_an_item", "Select an Item");
        translationBuilder.add("totality.trading.quote_loading", "Checking with the merchant...");
        translationBuilder.add("totality.trading.not_accepted", "Item not accepted");
        translationBuilder.add("totality.trading.no_value", "No known value");
        translationBuilder.add("totality.trading.merchant_cannot_afford", "Merchant cannot afford this quantity");
        //Trading Screen — rejection messages (Part D/H)
        translationBuilder.add("totality.trading.reject.trade_ended", "Trade ended");
        translationBuilder.add("totality.trading.reject.invalid_quantity", "Invalid quantity");
        translationBuilder.add("totality.trading.reject.cannot_afford", "You cannot afford this purchase");
        translationBuilder.add("totality.trading.reject.stock_changed", "Stock changed");
        translationBuilder.add("totality.trading.reject.item_gone", "Item is no longer present");
        translationBuilder.add("totality.trading.reject.not_accepted", "Merchant does not buy this type of item");
        translationBuilder.add("totality.trading.reject.no_value", "This item has no known value");
        translationBuilder.add("totality.trading.reject.merchant_cannot_afford", "Merchant cannot afford that quantity");
        translationBuilder.add("totality.trading.reject.generic", "That didn't work");
    }
}
