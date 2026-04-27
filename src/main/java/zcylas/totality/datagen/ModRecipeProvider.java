package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricRecipeProvider;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.recipes.*;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.NonNull;
import zcylas.totality.init.items.IngredientItems;

import java.util.concurrent.CompletableFuture;

public class ModRecipeProvider extends FabricRecipeProvider {
    public ModRecipeProvider(FabricPackOutput output, CompletableFuture<HolderLookup.Provider> registriesFuture) {
        super(output, registriesFuture);
    }

    @Override
    protected @NonNull RecipeProvider createRecipeProvider(HolderLookup.@NonNull Provider provider, @NonNull RecipeOutput recipeOutput) {
        return new RecipeProvider(provider, recipeOutput) {
            @Override
            public void buildRecipes() {
                //Ingredients
                    //Gears
                gearRecipe(IngredientItems.COPPER_GEAR, Items.COPPER_INGOT, IngredientItems.GRAPHITE);
                gearRecipe(IngredientItems.IRON_GEAR, Items.IRON_INGOT, IngredientItems.GRAPHITE);
                gearRecipe(IngredientItems.GOLD_GEAR, Items.GOLD_INGOT, IngredientItems.GRAPHITE);
                gearRecipe(IngredientItems.DIAMOND_GEAR, Items.DIAMOND, IngredientItems.GRAPHITE);
                SmithingTransformRecipeBuilder.smithing(
                        Ingredient.of(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE),
                        Ingredient.of(IngredientItems.DIAMOND_GEAR),
                        Ingredient.of(Items.NETHERITE_INGOT),
                        RecipeCategory.MISC,
                        IngredientItems.NETHERITE_GEAR
                )
                        .unlocks(getHasName(IngredientItems.DIAMOND_GEAR),has(IngredientItems.DIAMOND_GEAR))
                        .save(output,getItemName(IngredientItems.NETHERITE_GEAR) + "_smithing");

            }

            //Helper Methods
            //Gears
            HolderGetter<Item> items = registries.lookupOrThrow(Registries.ITEM);

            private void gearRecipe(ItemLike result, ItemLike material, ItemLike graphite) {
                ShapedRecipeBuilder.shaped(items, RecipeCategory.MISC, result, 1)
                        .pattern("MDM")
                        .pattern("D D")
                        .pattern("MDM")
                        .define('M', material)
                        .define('D', graphite)
                        .unlockedBy(getHasName(material), has(material))
                        .save(output);
            }
        };
    }
    @Override
    public @NonNull String getName() {
        return "Totality Recipes";
    }
}
