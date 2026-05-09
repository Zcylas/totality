package zcylas.totality.datagen;

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput;
import net.fabricmc.fabric.api.datagen.v1.provider.FabricTagsProvider;
import net.minecraft.client.color.item.Potion;
import net.minecraft.core.HolderLookup;
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
        valueLookupBuilder(ModTags.TOOLS)
                .add(MagicItems.NOVICE_GRIMOIRE)
                .add(MagicItems.APPRENTICE_GRIMOIRE)
                .add(MagicItems.ARCHMAGE_GRIMOIRE)
                .add(Items.WOODEN_PICKAXE)
                .add(Items.STONE_PICKAXE)
                .add(Items.IRON_PICKAXE)
                .add(Items.GOLDEN_PICKAXE)
                .add(Items.DIAMOND_PICKAXE)
                .add(Items.NETHERITE_PICKAXE)
                .add(Items.WOODEN_HOE)
                .add(Items.STONE_HOE)
                .add(Items.IRON_HOE)
                .add(Items.GOLDEN_HOE)
                .add(Items.DIAMOND_HOE)
                .add(Items.NETHERITE_HOE)
                .add(Items.WOODEN_SHOVEL)
                .add(Items.STONE_SHOVEL)
                .add(Items.IRON_SHOVEL)
                .add(Items.GOLDEN_SHOVEL)
                .add(Items.DIAMOND_SHOVEL)
                .add(Items.NETHERITE_SHOVEL)
                .add(Items.WOODEN_AXE)
                .add(Items.STONE_AXE)
                .add(Items.IRON_AXE)
                .add(Items.GOLDEN_AXE)
                .add(Items.DIAMOND_AXE)
                .add(Items.NETHERITE_AXE)
        ;
        valueLookupBuilder(ModTags.BOWS)
                .add(Items.BOW)
        // add custom bows here as you make them
        ;

        valueLookupBuilder(ModTags.CROSSBOWS)
                .add(Items.CROSSBOW)
        // add custom crossbows here
        ;
        valueLookupBuilder(ModTags.POTIONS)
                .add(PotionItems.BREWED_POTION)
                .add(PotionItems.POTION_OF_MINOR_HEALING)
                .add(PotionItems.POTION_OF_HEALING)
                .add(PotionItems.POTION_OF_VIGOROUS_HEALING)
                .add(PotionItems.POTION_OF_EXTREME_HEALING)
                .add(PotionItems.POTION_OF_ULTIMATE_HEALING)
                .add(PotionItems.POTION_OF_MINOR_MANA)
                .add(PotionItems.POTION_OF_MANA)
                .add(PotionItems.POTION_OF_VIGOROUS_MANA)
                .add(PotionItems.POTION_OF_EXTREME_MANA)
                .add(PotionItems.POTION_OF_ULTIMATE_MANA)
                .add(PotionItems.POTION_OF_MINOR_STAMINA)
                .add(PotionItems.POTION_OF_STAMINA)
                .add(PotionItems.POTION_OF_VIGOROUS_STAMINA)
                .add(PotionItems.POTION_OF_EXTREME_STAMINA)
                .add(PotionItems.POTION_OF_ULTIMATE_STAMINA)
                .add(PotionItems.DRAUGHT_OF_EXTRA_MANA)
                .add(PotionItems.DRAUGHT_OF_HEALTH)
                .add(PotionItems.DRAUGHT_OF_LASTING_POTENCY)
                .add(PotionItems.DRAUGHT_OF_REGENERATION)
                .add(PotionItems.ELIXIR_OF_EXTRA_MANA)
                .add(PotionItems.ELIXIR_OF_HEALTH)
                .add(PotionItems.ELIXIR_OF_LASTING_POTENCY)
                .add(PotionItems.ELIXIR_OF_REGENERATION)
                .add(PotionItems.DRAUGHT_OF_WATERBREATHING)
                .add(PotionItems.ELIXIR_OF_WATERBREATHING)
                .add(PotionItems.PHILTER_OF_EXTRA_MANA)
                .add(PotionItems.PHILTER_OF_HEALTH)
                .add(PotionItems.PHILTER_OF_LASTING_POTENCY)
                .add(PotionItems.PHILTER_OF_REGENERATION)
                .add(PotionItems.PHILTER_OF_WATERBREATHING)
                .add(PotionItems.SOLUTION_OF_EXTRA_MANA)
                .add(PotionItems.SOLUTION_OF_HEALTH)
                .add(PotionItems.SOLUTION_OF_LASTING_POTENCY)
                .add(PotionItems.SOLUTION_OF_REGENERATION)
                .add(PotionItems.POTION_OF_WATERBREATHING)
        // add custom potions here as you make them
        ;

        valueLookupBuilder(ModTags.SPECIAL)
                .add(MagicItems.NOVICE_GRIMOIRE)
                .add(MagicItems.APPRENTICE_GRIMOIRE)
                .add(MagicItems.ARCHMAGE_GRIMOIRE)
        ;
    }
}