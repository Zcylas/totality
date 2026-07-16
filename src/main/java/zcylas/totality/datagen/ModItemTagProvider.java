package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.client.color.item.Potion;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import zcylas.totality.init.ModTags;
import zcylas.totality.init.items.BasicWeaponItems;
import zcylas.totality.init.items.MagicItems;
import zcylas.totality.init.items.PotionItems;
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
        builder(ModTags.ONE_HANDED_WEAPONS)
                .add(key(Items.WOODEN_SWORD))
                .add(key(Items.STONE_SWORD))
                .add(key(Items.IRON_SWORD))
                .add(key(Items.GOLDEN_SWORD))
                .add(key(Items.DIAMOND_SWORD))
                .add(key(Items.NETHERITE_SWORD))
                .add(key(Items.WOODEN_SPEAR))
                .add(key(Items.STONE_SPEAR))
                .add(key(Items.IRON_SPEAR))
                .add(key(Items.GOLDEN_SPEAR))
                .add(key(Items.DIAMOND_SPEAR))
                .add(key(Items.NETHERITE_SPEAR))
                .add(key(Items.MACE))

        // Add your custom one-handed weapons here as they are registered
        ;

        // ── Two-handed weapons ────────────────────────────────────────────────
        builder(ModTags.TWO_HANDED_WEAPONS)
                .add(key(Items.WOODEN_AXE))
                .add(key(Items.STONE_AXE))
                .add(key(Items.IRON_AXE))
                .add(key(Items.GOLDEN_AXE))
                .add(key(Items.DIAMOND_AXE))
                .add(key(Items.NETHERITE_AXE))
                .add(key(Items.TRIDENT))
        // Add your custom two-handed weapons here as they are registered
        // e.g. .add(key(ModItems.GREATAXE))
        ;
        // ── Thrown weapons ────────────────────────────────────────────────────
        builder(ModTags.THROWN_WEAPONS)
                .add(key(Items.TRIDENT))
                .add(key(BasicWeaponItems.COPPER_SHURIKEN))
                .add(key(BasicWeaponItems.IRON_SHURIKEN))
                .add(key(BasicWeaponItems.GOLD_SHURIKEN))
                .add(key(BasicWeaponItems.DIAMOND_SHURIKEN))
                .add(key(BasicWeaponItems.NETHERITE_SHURIKEN))
        // Add your custom thrown weapons here as they are registered
        ;
        builder(ModTags.TOOLS)
                .add(key(Items.WOODEN_PICKAXE))
                .add(key(Items.STONE_PICKAXE))
                .add(key(Items.IRON_PICKAXE))
                .add(key(Items.GOLDEN_PICKAXE))
                .add(key(Items.DIAMOND_PICKAXE))
                .add(key(Items.NETHERITE_PICKAXE))
                .add(key(Items.WOODEN_HOE))
                .add(key(Items.STONE_HOE))
                .add(key(Items.IRON_HOE))
                .add(key(Items.GOLDEN_HOE))
                .add(key(Items.DIAMOND_HOE))
                .add(key(Items.NETHERITE_HOE))
                .add(key(Items.WOODEN_SHOVEL))
                .add(key(Items.STONE_SHOVEL))
                .add(key(Items.IRON_SHOVEL))
                .add(key(Items.GOLDEN_SHOVEL))
                .add(key(Items.DIAMOND_SHOVEL))
                .add(key(Items.NETHERITE_SHOVEL))
                .add(key(Items.WOODEN_AXE))
                .add(key(Items.STONE_AXE))
                .add(key(Items.IRON_AXE))
                .add(key(Items.GOLDEN_AXE))
                .add(key(Items.DIAMOND_AXE))
                .add(key(Items.NETHERITE_AXE))
        ;
        builder(ModTags.BOWS)
                .add(key(Items.BOW))
        // add custom bows here as you make them
        ;

        builder(ModTags.CROSSBOWS)
                .add(key(Items.CROSSBOW))
        // add custom crossbows here
        ;
        builder(ModTags.POTIONS)
                .add(key(PotionItems.BREWED_POTION))
                .add(key(PotionItems.POTION_OF_MINOR_HEALING))
                .add(key(PotionItems.POTION_OF_HEALING))
                .add(key(PotionItems.POTION_OF_VIGOROUS_HEALING))
                .add(key(PotionItems.POTION_OF_EXTREME_HEALING))
                .add(key(PotionItems.POTION_OF_ULTIMATE_HEALING))
                .add(key(PotionItems.POTION_OF_MINOR_MANA))
                .add(key(PotionItems.POTION_OF_MANA))
                .add(key(PotionItems.POTION_OF_VIGOROUS_MANA))
                .add(key(PotionItems.POTION_OF_EXTREME_MANA))
                .add(key(PotionItems.POTION_OF_ULTIMATE_MANA))
                .add(key(PotionItems.POTION_OF_MINOR_STAMINA))
                .add(key(PotionItems.POTION_OF_STAMINA))
                .add(key(PotionItems.POTION_OF_VIGOROUS_STAMINA))
                .add(key(PotionItems.POTION_OF_EXTREME_STAMINA))
                .add(key(PotionItems.POTION_OF_ULTIMATE_STAMINA))
                .add(key(PotionItems.DRAUGHT_OF_EXTRA_MANA))
                .add(key(PotionItems.DRAUGHT_OF_HEALTH))
                .add(key(PotionItems.DRAUGHT_OF_LASTING_POTENCY))
                .add(key(PotionItems.DRAUGHT_OF_REGENERATION))
                .add(key(PotionItems.ELIXIR_OF_EXTRA_MANA))
                .add(key(PotionItems.ELIXIR_OF_HEALTH))
                .add(key(PotionItems.ELIXIR_OF_LASTING_POTENCY))
                .add(key(PotionItems.ELIXIR_OF_REGENERATION))
                .add(key(PotionItems.DRAUGHT_OF_WATERBREATHING))
                .add(key(PotionItems.ELIXIR_OF_WATERBREATHING))
                .add(key(PotionItems.PHILTER_OF_EXTRA_MANA))
                .add(key(PotionItems.PHILTER_OF_HEALTH))
                .add(key(PotionItems.PHILTER_OF_LASTING_POTENCY))
                .add(key(PotionItems.PHILTER_OF_REGENERATION))
                .add(key(PotionItems.PHILTER_OF_WATERBREATHING))
                .add(key(PotionItems.SOLUTION_OF_EXTRA_MANA))
                .add(key(PotionItems.SOLUTION_OF_HEALTH))
                .add(key(PotionItems.SOLUTION_OF_LASTING_POTENCY))
                .add(key(PotionItems.SOLUTION_OF_REGENERATION))
                .add(key(PotionItems.POTION_OF_WATERBREATHING))
        // add custom potions here as you make them
        ;

        builder(ModTags.SPECIAL)
                .add(key(MagicItems.NOVICE_GRIMOIRE))
                .add(key(MagicItems.APPRENTICE_GRIMOIRE))
                .add(key(MagicItems.ARCHMAGE_GRIMOIRE))
        ;
    }

    /** MC 26.2's BlockItemTagAppender.add() takes a ResourceKey, not the Item itself. */
    private static ResourceKey<Item> key(Item item) {
        return item.builtInRegistryHolder().key();
    }
}