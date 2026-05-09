package zcylas.totality.init;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import zcylas.totality.init.items.CurrencyItems;
import zcylas.totality.init.items.SKIngredientItems;

public final class ModLootTables {

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {

            // ── Mob Drops ─────────────────────────────────────────────────────

            // Salmon → Salmon Roe (75% chance, 0-1)
            if (key.equals(ResourceKey.create(Registries.LOOT_TABLE,
                    Identifier.withDefaultNamespace("entities/salmon")))) {
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(SKIngredientItems.SALMON_ROE)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(0, 1)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.75f)))
                );
            }

            // ── Chest Loot ────────────────────────────────────────────────────

            // Village house chests → Rock Warbler Egg (35% chance, 1-3)
            if (key.equals(BuiltInLootTables.VILLAGE_PLAINS_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_TAIGA_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_SNOWY_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_FISHER)) {
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(SKIngredientItems.ROCK_WARBLER_EGG)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 3)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.35f)))
                );
            }

            // ── Coin Drops ────────────────────────────────────────────────────
            //
            // Tiers:
            //
            //  COMMON  — village chests, shipwrecks, temples, outposts
            //            Copper only: 22% chance, 1–5 coins
            //
            //  DUNGEON — dungeons, mineshafts, strongholds, ancient city, ruined portals
            //            Copper: 28% chance, 6–10 coins
            //            Silver: 9%  chance, 1–2 coins
            //
            //  RARE    — end city, buried treasure, nether bridge
            //            Copper: 30% chance, 12–20 coins
            //            Silver: 12% chance, 1–3 coins
            //            Gold:   3%  chance, 1 coin
            //
            //  BASTION — gold-obsessed piglins: boosted gold chance
            //            Copper: 30% chance, 12–20 coins
            //            Silver: 12% chance, 1–3 coins
            //            Gold:   5%  chance, 1 coin   ← higher than everywhere else
            //
            //  TRIAL CHAMBERS — active content, scales by difficulty:
            //            Normal reward:        Copper only  40% chance, 8–14 coins
            //            Rare reward:          Silver only  20% chance, 1–3 coins
            //            Ominous reward:       Copper 35%,  Silver 18%, Gold 5% (1 coin)
            //            Ominous rare reward:  Copper 35%,  Silver 22%, Gold 8% (1–2 coins)

            // ── Common overworld chests: copper only ──────────────────────────
            if (key.equals(BuiltInLootTables.VILLAGE_WEAPONSMITH)
                    || key.equals(BuiltInLootTables.VILLAGE_TOOLSMITH)
                    || key.equals(BuiltInLootTables.VILLAGE_ARMORER)
                    || key.equals(BuiltInLootTables.VILLAGE_CARTOGRAPHER)
                    || key.equals(BuiltInLootTables.VILLAGE_MASON)
                    || key.equals(BuiltInLootTables.VILLAGE_SHEPHERD)
                    || key.equals(BuiltInLootTables.VILLAGE_BUTCHER)
                    || key.equals(BuiltInLootTables.VILLAGE_FLETCHER)
                    || key.equals(BuiltInLootTables.VILLAGE_FISHER)
                    || key.equals(BuiltInLootTables.VILLAGE_TANNERY)
                    || key.equals(BuiltInLootTables.VILLAGE_TEMPLE)
                    || key.equals(BuiltInLootTables.VILLAGE_DESERT_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_PLAINS_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_TAIGA_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_SNOWY_HOUSE)
                    || key.equals(BuiltInLootTables.VILLAGE_SAVANNA_HOUSE)
                    || key.equals(BuiltInLootTables.SHIPWRECK_SUPPLY)
                    || key.equals(BuiltInLootTables.SHIPWRECK_TREASURE)
                    || key.equals(BuiltInLootTables.JUNGLE_TEMPLE)
                    || key.equals(BuiltInLootTables.DESERT_PYRAMID)
                    || key.equals(BuiltInLootTables.PILLAGER_OUTPOST)
                    || key.equals(BuiltInLootTables.IGLOO_CHEST)
                    || key.equals(BuiltInLootTables.WOODLAND_MANSION)
                    || key.equals(BuiltInLootTables.UNDERWATER_RUIN_SMALL)
                    || key.equals(BuiltInLootTables.UNDERWATER_RUIN_BIG)) {
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 5)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.22f)))
                );
            }

            // ── Dungeon / underground: copper (more) + silver (rare) ──────────
            if (key.equals(BuiltInLootTables.SIMPLE_DUNGEON)
                    || key.equals(BuiltInLootTables.ABANDONED_MINESHAFT)
                    || key.equals(BuiltInLootTables.STRONGHOLD_CORRIDOR)
                    || key.equals(BuiltInLootTables.STRONGHOLD_CROSSING)
                    || key.equals(BuiltInLootTables.STRONGHOLD_LIBRARY)
                    || key.equals(BuiltInLootTables.RUINED_PORTAL)
                    || key.equals(BuiltInLootTables.ANCIENT_CITY)
                    || key.equals(BuiltInLootTables.ANCIENT_CITY_ICE_BOX)) {
                // Copper
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(6, 10)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.28f)))
                );
                // Silver
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.SILVER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 2)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.09f)))
                );
            }

            // ── Rare chests (end city, buried treasure, nether bridge) ─────────
            if (key.equals(BuiltInLootTables.END_CITY_TREASURE)
                    || key.equals(BuiltInLootTables.BURIED_TREASURE)
                    || key.equals(BuiltInLootTables.NETHER_BRIDGE)) {
                // Copper
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(12, 20)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.30f)))
                );
                // Silver
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.SILVER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 3)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.12f)))
                );
                // Gold
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.GOLD_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                ConstantValue.exactly(1)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.03f)))
                );
            }

            // ── Bastions: same as rare but gold at 5% (piglin gold obsession) ──
            if (key.equals(BuiltInLootTables.BASTION_TREASURE)
                    || key.equals(BuiltInLootTables.BASTION_OTHER)
                    || key.equals(BuiltInLootTables.BASTION_BRIDGE)
                    || key.equals(BuiltInLootTables.BASTION_HOGLIN_STABLE)) {
                // Copper
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(12, 20)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.30f)))
                );
                // Silver
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.SILVER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 3)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.12f)))
                );
                // Gold — 5% for piglins
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.GOLD_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                ConstantValue.exactly(1)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.05f)))
                );
            }

            // ── Trial Chambers: scales by difficulty ──────────────────────────

            // Normal reward — copper only, decent amount for the fight
            if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD)
                    || key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_COMMON)) {
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(8, 14)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.40f)))
                );
            }

            // Rare reward — silver only
            if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_RARE)) {
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.SILVER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 3)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.20f)))
                );
            }

            // Ominous reward — copper + silver + gold (5%)
            if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS)) {
                // Copper
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(10, 16)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.35f)))
                );
                // Silver
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.SILVER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 3)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.18f)))
                );
                // Gold
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.GOLD_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                ConstantValue.exactly(1)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.05f)))
                );
            }

            // Ominous rare reward — the best trial chamber loot, gold up to 2
            if (key.equals(BuiltInLootTables.TRIAL_CHAMBERS_REWARD_OMINOUS_RARE)) {
                // Copper
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.COPPER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(12, 20)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.35f)))
                );
                // Silver
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.SILVER_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 4)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.22f)))
                );
                // Gold — 8%, up to 2 coins for the ultimate trial reward
                tableBuilder.withPool(
                        LootPool.lootPool()
                                .setRolls(ConstantValue.exactly(1))
                                .add(LootItem.lootTableItem(CurrencyItems.GOLD_COIN)
                                        .apply(SetItemCountFunction.setCount(
                                                UniformGenerator.between(1, 2)))
                                        .when(LootItemRandomChanceCondition.randomChance(0.08f)))
                );
            }
        });
    }

    private ModLootTables() {}
}