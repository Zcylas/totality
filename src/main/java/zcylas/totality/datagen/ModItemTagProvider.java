package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import zcylas.totality.init.ModTags;
import zcylas.totality.init.items.BasicWeaponItems;
import zcylas.totality.item.base_weapons.ShurikenItem;

import java.util.concurrent.CompletableFuture;

public class ModItemTagProvider extends FabricTagsProvider.ItemTagsProvider {

    public ModItemTagProvider(FabricPackOutput output,
                              CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void addTags(HolderLookup.Provider registries) {

        // ── One-handed weapons ────────────────────────────────────────────────
        valueLookupBuilder(ModTags.ONE_HANDED_WEAPONS)
                .add(Items.WOODEN_SWORD)
                .add(Items.STONE_SWORD)
                .add(Items.IRON_SWORD)
                .add(Items.GOLDEN_SWORD)
                .add(Items.DIAMOND_SWORD)
                .add(Items.NETHERITE_SWORD)
                .add(Items.WOODEN_SPEAR)
                .add(Items.STONE_SPEAR)
                .add(Items.IRON_SPEAR)
                .add(Items.GOLDEN_SPEAR)
                .add(Items.DIAMOND_SPEAR)
                .add(Items.NETHERITE_SPEAR)
                .add(Items.MACE)

        // Add your custom one-handed weapons here as they are registered
        ;

        // ── Two-handed weapons ────────────────────────────────────────────────
        valueLookupBuilder(ModTags.TWO_HANDED_WEAPONS)
                .add(Items.WOODEN_AXE)
                .add(Items.STONE_AXE)
                .add(Items.IRON_AXE)
                .add(Items.GOLDEN_AXE)
                .add(Items.DIAMOND_AXE)
                .add(Items.NETHERITE_AXE)
                .add(Items.TRIDENT)
        // Add your custom two-handed weapons here as they are registered
        // e.g. .add(ModItems.GREATAXE)
        ;
        // ── Thrown weapons ────────────────────────────────────────────────────
        valueLookupBuilder(ModTags.THROWN_WEAPONS)
                .add(Items.TRIDENT)
                .add(BasicWeaponItems.COPPER_SHURIKEN)
                .add(BasicWeaponItems.IRON_SHURIKEN)
                .add(BasicWeaponItems.GOLD_SHURIKEN)
                .add(BasicWeaponItems.DIAMOND_SHURIKEN)
                .add(BasicWeaponItems.NETHERITE_SHURIKEN)
        // Add your custom thrown weapons here as they are registered
        ;
    }
}