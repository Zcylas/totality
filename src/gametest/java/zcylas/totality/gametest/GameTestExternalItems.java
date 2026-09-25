package zcylas.totality.gametest;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemLore;

import java.util.List;

/**
 * Test-only stand-in for a third-party mod's item (no real third-party mod is installed): one plain item in an
 * external namespace, registered only by the game-test mod, so routing can be verified to leave other mods' tooltips
 * alone. It reuses the vanilla stick model and carries a vanilla lore line so its original tooltip is recognisable.
 */
public final class GameTestExternalItems implements ModInitializer {

    public static final Identifier SAMPLE_RELIC_ID = Identifier.fromNamespaceAndPath("gametest_external", "sample_relic");
    public static final String LORE_LINE = "Original third-party lore line";

    @Override
    public void onInitialize() {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, SAMPLE_RELIC_ID);
        Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)
                .component(DataComponents.ITEM_MODEL, Identifier.withDefaultNamespace("stick"))
                .component(DataComponents.LORE, new ItemLore(List.of(Component.literal(LORE_LINE))))));
    }
}
