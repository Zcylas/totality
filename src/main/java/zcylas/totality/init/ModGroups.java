package zcylas.totality.init;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.Totality;
import zcylas.totality.init.items.FoodItems;

/**
 * Totality's Food creative-inventory tab. Purely a creative-browsing/testing aid — registers no
 * gameplay behavior, changes no recipes, loot, or item stats.
 */
public class ModGroups {

    private static ResourceKey<CreativeModeTab> key(String path) {
        return ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(Totality.MOD_ID, path));
    }

    private static final ResourceKey<CreativeModeTab> FOOD_KEY = key("food");

    public static CreativeModeTab FOOD;

    public static void register() {
        // Icon: the whole Pizza is the mod's first real food item and instantly reads as "food."
        // One deliberately unsplit Food tab (2026-09-17) — see FoodItems' own class Javadoc; a
        // future Ingredients/Meals/Snacks/Drinks split is deferred until the catalog justifies it.
        FOOD = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, FOOD_KEY,
                FabricCreativeModeTab.builder()
                        .title(Component.translatable("itemGroup.totality.food"))
                        .icon(() -> new ItemStack(FoodItems.PIZZA_MARGHERITA))
                        .displayItems((parameters, output) -> {
                            output.accept(FoodItems.PIZZA_MARGHERITA);
                            output.accept(FoodItems.PIZZA_MARGHERITA_SLICE);
                        })
                        .build());
    }

    private ModGroups() {}
}
