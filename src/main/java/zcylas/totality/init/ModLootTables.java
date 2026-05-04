package zcylas.totality.init;

import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;
import zcylas.totality.init.items.SKIngredientItems;

public final class ModLootTables {

    public static void register() {
        LootTableEvents.MODIFY.register((key, tableBuilder, source, registries) -> {

            // ── Mob Drops ─────────────────────────────────────────────────────

            // Salmon → Salmon Roe (75% chance, 1-2)
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
        });
    }

    private ModLootTables() {}
}