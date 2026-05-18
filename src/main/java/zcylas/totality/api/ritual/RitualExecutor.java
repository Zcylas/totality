package zcylas.totality.api.ritual;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import zcylas.totality.blockentity.ritual.RitualAltarBlockEntity;
import zcylas.totality.blockentity.ritual.RitualDaisBlockEntity;
import zcylas.totality.init.blocks.RitualBlocks;

public class RitualExecutor {

    public static void execute(ServerLevel level, BlockPos altarPos,
                               RitualAltarBlockEntity altar, RitualRecipe recipe) {
        // Consume chalk blocks
        for (RitualRecipe.ChalkEntry entry : recipe.pattern()) {
            BlockPos chalkPos = entry.resolve(altarPos);
            if (level.getBlockState(chalkPos).is(RitualBlocks.CHALK)) {
                level.removeBlock(chalkPos, false);
                spawnParticles(level, chalkPos);
            }
        }

        // Consume dais items
        for (RitualRecipe.DaisEntry entry : recipe.dais()) {
            BlockPos daisPos = entry.resolve(altarPos);
            var be = level.getBlockEntity(daisPos);
            if (be instanceof RitualDaisBlockEntity dais) {
                dais.setHeldItem(ItemStack.EMPTY);
                spawnParticles(level, daisPos);
            }
        }

        // Replace altar item with result
        altar.setHeldItem(recipe.result().copy());

        // Particles and sound at altar
        spawnParticles(level, altarPos);
        level.playSound(null, altarPos,
                SoundEvents.ENCHANTMENT_TABLE_USE,
                SoundSource.BLOCKS, 1.0f, 1.0f);

        // Deactivate altar
        level.setBlock(altarPos,
                level.getBlockState(altarPos).setValue(
                        zcylas.totality.block.ritual.RitualAltarBlock.LIT, false), 3);
    }

    private static void spawnParticles(ServerLevel level, BlockPos pos) {
        level.sendParticles(
                ParticleTypes.ENCHANT,
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                20, 0.3, 0.3, 0.3, 0.05
        );
    }
}