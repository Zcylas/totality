package zcylas.totality.api.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import zcylas.totality.Totality;
import zcylas.totality.block.ritual.ChalkBlock;
import zcylas.totality.blockentity.ritual.RitualAltarBlockEntity;
import zcylas.totality.blockentity.ritual.RitualDaisBlockEntity;
import zcylas.totality.init.blocks.RitualBlocks;
import org.jspecify.annotations.Nullable;

public class RitualMatcher {

    @Nullable
    public static RitualRecipe match(Level level, BlockPos altarPos,
                                     RitualAltarBlockEntity altar) {
        ItemStack altarItem = altar.getHeldItem();
        if (altarItem.isEmpty()) return null;

        for (RitualRecipe recipe : RitualRecipeRegistry.getAll()) {
            if (matches(level, altarPos, altarItem, recipe)) {
                return recipe;
            }
        }
        return null;
    }

    private static boolean matches(Level level, BlockPos altarPos,
                                   ItemStack altarItem, RitualRecipe recipe) {
        if (!altarItem.is(recipe.altarInput().getItem())) {
            Totality.LOGGER.info("Altar input mismatch: {} vs {}", altarItem, recipe.altarInput());
            return false;
        }

        for (RitualRecipe.ChalkEntry entry : recipe.pattern()) {
            BlockPos chalkPos = entry.resolve(altarPos);
            BlockState state = level.getBlockState(chalkPos);
            if (!state.is(RitualBlocks.CHALK)) {
                Totality.LOGGER.info("No chalk at {}", chalkPos);
                return false;
            }
            if (state.getValue(ChalkBlock.COLOR) != entry.color()) {
                Totality.LOGGER.info("Wrong color at {}: {} vs {}", chalkPos, state.getValue(ChalkBlock.COLOR), entry.color());
                return false;
            }
            if (state.getValue(ChalkBlock.GLYPH) != entry.glyph()) {
                Totality.LOGGER.info("Wrong glyph at {}: {} vs {}", chalkPos, state.getValue(ChalkBlock.GLYPH), entry.glyph());
                return false;
            }
        }

        for (RitualRecipe.DaisEntry entry : recipe.dais()) {
            BlockPos daisPos = entry.resolve(altarPos);
            var be = level.getBlockEntity(daisPos);
            if (!(be instanceof RitualDaisBlockEntity dais)) {
                Totality.LOGGER.info("No dais at {}", daisPos);
                return false;
            }
            if (dais.isEmpty()) {
                Totality.LOGGER.info("Empty dais at {}", daisPos);
                return false;
            }
            if (!dais.getHeldItem().is(BuiltInRegistries.ITEM.getValue(entry.item()))) {
                Totality.LOGGER.info("Wrong item on dais at {}: {} vs {}", daisPos, dais.getHeldItem(), entry.item());
                return false;
            }
        }

        return true;
    }
}