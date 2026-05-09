package zcylas.totality.init;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.Consumables;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import zcylas.totality.Totality;
import zcylas.totality.block.energy.CableBlock;
import zcylas.totality.block.energy.EnergyCellBlock;
import zcylas.totality.block.fluid.FluidTankBlock;
import zcylas.totality.init.items.SKIngredientItems;
import zcylas.totality.item.energy.EnergyCellItem;
import zcylas.totality.item.fluid.FluidTankItem;

import java.util.function.BiFunction;
import java.util.function.Function;

public class TotalityRegistry {

    public static <T extends Item> T registerItem(String name, Function<Item.Properties, T> itemFactory, Item.Properties properties){
        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(Totality.MOD_ID, name));
        T item = itemFactory.apply(properties.setId(itemKey));
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        assert translationKeyMatches("item", Totality.MOD_ID, name, item.getDescriptionId())
                : "Item translation key mismatch for: " + name;

        return item;
    }

    public static <T extends Block>T registerBlock(String name, Function<BlockBehaviour.Properties, T> blockFactory, BlockBehaviour.Properties properties, boolean withItem) {
        ResourceKey<Block> blockKey = keyOfBlock(name);

        T block = Registry.register(
                BuiltInRegistries.BLOCK,
                blockKey,
                blockFactory.apply(properties.setId(blockKey))
        );

        if (withItem) {
            ResourceKey<Item> itemKey = keyOfItem(name);

            Registry.register(
                    BuiltInRegistries.ITEM,
                    itemKey,
                    new BlockItem(block, new Item.Properties()
                            .setId(itemKey)
                            .useBlockDescriptionPrefix())
            );
        }
        return block;
    }

    /**
     * Registers a flower block + a custom BlockItem that also implements AlchemyIngredient.
     * The itemFactory receives (block, properties) so it can pass the block to BlockItem's constructor.
     * Use this for all alchemy flower ingredients so Red/Purple Mountain Flower follow the same pattern.
     *
     * Example:
     *   registerFlowerIngredient("blue_mountain_flower",
     *       props -> new BlueMountainFlowerBlock(() -> MobEffects.NIGHT_VISION, 8, props),
     *       BlueMountainFlowerItem::new)
     */
    public static <B extends FlowerBlock, I extends BlockItem> B registerFlowerIngredient(
            String name,
            Function<BlockBehaviour.Properties, B> blockFactory,
            BiFunction<B, Item.Properties, I> itemFactory
    ) {
        ResourceKey<Block> blockKey = keyOfBlock(name);
        ResourceKey<Item> itemKey   = keyOfItem(name);

        B block = Registry.register(
                BuiltInRegistries.BLOCK,
                blockKey,
                blockFactory.apply(
                        BlockBehaviour.Properties.of()
                                .mapColor(MapColor.PLANT)
                                .sound(SoundType.GRASS)
                                .noCollision()
                                .instabreak()
                                .noOcclusion()
                                .pushReaction(PushReaction.DESTROY)
                                .setId(blockKey)
                )
        );

        I item = itemFactory.apply(block,
                new Item.Properties()
                        .setId(itemKey)
                        .useBlockDescriptionPrefix()
                        .stacksTo(64)
                        .food(SKIngredientItems.INGREDIENT_FOOD)
        );
        Registry.register(BuiltInRegistries.ITEM, itemKey, item);

        return block;
    }

    private static ResourceKey<Block> keyOfBlock(String name) {
        return ResourceKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, name));
    }

    private static ResourceKey<Item> keyOfItem(String name) {
        return ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(Totality.MOD_ID, name));
    }

    private static boolean translationKeyMatches(String type, String modId, String name, String actual) {
        String expected = Util.makeDescriptionId(type, Identifier.fromNamespaceAndPath(modId, name));
        return expected.equals(actual);
    }

    public static FluidTankBlock registerFluidTank(String name, long capacityMb) {
        FluidTankBlock block = registerBlock(
                name,
                properties -> new FluidTankBlock(properties, capacityMb),
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(2.0f)
                        .noOcclusion(),
                false
        );

        ResourceKey<Item> itemKey = keyOfItem(name);
        Registry.register(
                BuiltInRegistries.ITEM,
                itemKey,
                new FluidTankItem(block, new Item.Properties()
                        .setId(itemKey)
                        .useBlockDescriptionPrefix())
        );

        return block;
    }

    public static EnergyCellBlock registerEnergyCell(String name, long capacity,
                                                     long maxInput, long maxOutput) {
        ResourceKey<Item> itemKey = keyOfItem(name);
        EnergyCellBlock block = registerBlock(
                name,
                properties -> new EnergyCellBlock(properties, capacity, maxInput, maxOutput),
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(3.0f)
                        .sound(SoundType.METAL)
                        .requiresCorrectToolForDrops(),
                false
        );

        Registry.register(
                BuiltInRegistries.ITEM,
                itemKey,
                new EnergyCellItem(block, new Item.Properties()
                        .setId(itemKey)
                        .useBlockDescriptionPrefix())
        );

        return block;
    }

    public static CableBlock registerCable(String name, long transferPerTick) {
        return registerBlock(
                name,
                properties -> new CableBlock(properties, transferPerTick),
                BlockBehaviour.Properties.of()
                        .mapColor(MapColor.METAL)
                        .strength(1.0f)
                        .noOcclusion(),
                true
        );
    }

    // ── Potions ───────────────────────────────────────────────────────────────

    /**
     * Registers a standard tiered potion with a custom effect name for the title.
     * Example: registerPotion("potion_of_minor_healing", MagnitudeTier.MINOR,
     *              AlchemyEffects.RESTORE_HEALTH, COLOR_RED, "Healing")
     *          → "Potion of Minor Healing"
     */
    public static zcylas.totality.item.potion.AlchemyPotionItem registerPotion(
            String name,
            zcylas.totality.api.rpg.skills.alchemy.potions.PotionTier tier,
            zcylas.totality.api.rpg.skills.alchemy.AlchemyEffect effect,
            int color,
            String effectName
    ) {
        String displayName = tier.buildName(effectName, false);
        float magnitude = 0f;
        int durationTicks = 0;
        if (tier instanceof zcylas.totality.api.rpg.skills.alchemy.potions.MagnitudeTier mt) {
            magnitude = mt.getPoints();
        } else if (tier instanceof zcylas.totality.api.rpg.skills.alchemy.potions.DurationTier dt) {
            durationTicks = dt.getDurationTicks();
        }
        zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry entry =
                zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry.of(effect, magnitude, durationTicks);
        zcylas.totality.api.rpg.skills.alchemy.potions.PotionData data =
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        displayName, color, java.util.List.of(entry), false);
        return registerItem(name,
                props -> new zcylas.totality.item.potion.AlchemyPotionItem(data, props),
                new net.minecraft.world.item.Item.Properties()
                        .stacksTo(64)
                        .food(new net.minecraft.world.food.FoodProperties.Builder().alwaysEdible().build(),
                                Consumables.DEFAULT_DRINK));
    }

    /**
     * Registers a standard tiered potion — name auto-generated from tier + effect display name.
     * Example: registerPotion("potion_of_minor_mana", MagnitudeTier.MINOR, AlchemyEffects.RESTORE_MANA)
     *          → "Potion of Minor Restore Mana"
     */
    public static zcylas.totality.item.potion.AlchemyPotionItem registerPotion(
            String name,
            zcylas.totality.api.rpg.skills.alchemy.potions.PotionTier tier,
            zcylas.totality.api.rpg.skills.alchemy.AlchemyEffect effect,
            int color
    ) {
        String displayName = tier.buildName(effect.getDisplayName(), false);
        float magnitude = 0f;
        int durationTicks = 0;
        if (tier instanceof zcylas.totality.api.rpg.skills.alchemy.potions.MagnitudeTier mt) {
            magnitude = mt.getPoints();
        } else if (tier instanceof zcylas.totality.api.rpg.skills.alchemy.potions.DurationTier dt) {
            durationTicks = dt.getDurationTicks();
        }
        zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry entry =
                zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry.of(effect, magnitude, durationTicks);
        zcylas.totality.api.rpg.skills.alchemy.potions.PotionData data =
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        displayName, color, java.util.List.of(entry), false);
        return registerItem(name,
                props -> new zcylas.totality.item.potion.AlchemyPotionItem(data, props),
                new net.minecraft.world.item.Item.Properties()
                        .stacksTo(64)
                        .food(new FoodProperties.Builder().alwaysEdible().build(),
                                Consumables.DEFAULT_DRINK));
    }

    /**
     * Registers a standard tiered poison — name auto-generated from tier + effect name.
     */
    public static zcylas.totality.item.potion.AlchemyPotionItem registerPoison(
            String name,
            zcylas.totality.api.rpg.skills.alchemy.potions.PotionTier tier,
            zcylas.totality.api.rpg.skills.alchemy.AlchemyEffect effect,
            int color
    ) {
        String displayName = tier.buildName(effect.getDisplayName(), true);
        float magnitude = 0f;
        int durationTicks = 0;
        if (tier instanceof zcylas.totality.api.rpg.skills.alchemy.potions.MagnitudeTier mt) {
            magnitude = mt.getPoints();
        } else if (tier instanceof zcylas.totality.api.rpg.skills.alchemy.potions.DurationTier dt) {
            durationTicks = dt.getDurationTicks();
        }
        zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry entry =
                zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry.of(effect, magnitude, durationTicks);
        zcylas.totality.api.rpg.skills.alchemy.potions.PotionData data =
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        displayName, color, java.util.List.of(entry), true);
        return registerItem(name,
                props -> new zcylas.totality.item.potion.AlchemyPotionItem(data, props),
                new net.minecraft.world.item.Item.Properties()
                        .stacksTo(64)
                        .food(new FoodProperties.Builder().alwaysEdible().build(),
                                Consumables.DEFAULT_DRINK));
    }

    /**
     * Registers a regeneration tier potion.
     * e.g. registerRegenPotion("potion_of_regeneration", "Regeneration",
     *          RegenerateTier.POTION, AlchemyEffects.REGENERATE_HEALTH, COLOR_RED)
     */
    public static zcylas.totality.item.potion.AlchemyPotionItem registerRegenPotion(
            String name,
            String effectName,
            zcylas.totality.api.rpg.skills.alchemy.potions.RegenerateTier tier,
            zcylas.totality.api.rpg.skills.alchemy.AlchemyEffect effect,
            int color
    ) {
        String displayName = tier.buildName(effectName, false);
        zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry entry =
                zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry.of(
                        effect, tier.getRegenBoost(), tier.getDurationTicks());
        zcylas.totality.api.rpg.skills.alchemy.potions.PotionData data =
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        displayName, color, java.util.List.of(entry), false);
        return registerItem(name,
                props -> new zcylas.totality.item.potion.AlchemyPotionItem(data, props),
                new net.minecraft.world.item.Item.Properties()
                        .stacksTo(64)
                        .food(new net.minecraft.world.food.FoodProperties.Builder().alwaysEdible().build(),
                                net.minecraft.world.item.component.Consumables.DEFAULT_DRINK));
    }

    /**
     * Registers a fortify tier potion.
     * e.g. registerFortifyPotion("potion_of_health", "Health",
     *          FortifyTier.POTION, AlchemyEffects.FORTIFY_HEALTH, COLOR_RED)
     */
    public static zcylas.totality.item.potion.AlchemyPotionItem registerFortifyPotion(
            String name,
            String effectName,
            zcylas.totality.api.rpg.skills.alchemy.potions.FortifyTier tier,
            zcylas.totality.api.rpg.skills.alchemy.AlchemyEffect effect,
            int color
    ) {
        String displayName = tier.buildName(effectName, false);
        zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry entry =
                zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry.of(
                        effect, tier.getPoints(), tier.getDurationTicks());
        zcylas.totality.api.rpg.skills.alchemy.potions.PotionData data =
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        displayName, color, java.util.List.of(entry), false);
        return registerItem(name,
                props -> new zcylas.totality.item.potion.AlchemyPotionItem(data, props),
                new net.minecraft.world.item.Item.Properties()
                        .stacksTo(64)
                        .food(new net.minecraft.world.food.FoodProperties.Builder().alwaysEdible().build(),
                                net.minecraft.world.item.component.Consumables.DEFAULT_DRINK));
    }

    /**
     * Registers a special potion with a custom name and multiple effects.
     * Example: registerSpecialPotion("elixir_of_the_mage", "Elixir of the Mage",
     *              PotionData.COLOR_GOLD, false,
     *              EffectEntry.instant(AlchemyEffects.RESTORE_MANA, 0.5f),
     *              EffectEntry.timed(AlchemyEffects.FORTIFY_MANA, 1200))
     */
    public static zcylas.totality.item.potion.AlchemyPotionItem registerSpecialPotion(
            String name,
            String displayName,
            int color,
            boolean isPoison,
            zcylas.totality.api.rpg.skills.alchemy.potions.EffectEntry... effects
    ) {
        zcylas.totality.api.rpg.skills.alchemy.potions.PotionData data =
                zcylas.totality.api.rpg.skills.alchemy.potions.PotionData.of(
                        displayName, color, java.util.List.of(effects), isPoison);
        return registerItem(name,
                props -> new zcylas.totality.item.potion.AlchemyPotionItem(data, props),
                new net.minecraft.world.item.Item.Properties()
                        .stacksTo(64)
                        .food(new FoodProperties.Builder().alwaysEdible().build(),
                                Consumables.DEFAULT_DRINK));
    }
}