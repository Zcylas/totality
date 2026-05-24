// HarvestAbility.java
package zcylas.totality.api.ability.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.core.util.MountainFlowerBushBlock;
import zcylas.totality.api.rpg.skills.core.MasteriesComponents;
import zcylas.totality.block.alchemy.RedMountainFlowerBlock;
import zcylas.totality.init.ModTags;

import java.util.List;

public class HarvestAbility extends Ability {

    public HarvestAbility() {
        super(
                Identifier.fromNamespaceAndPath("totality", "harvest"),
                "Harvest",
                "Harvest plants directly into your inventory. Crops are replanted automatically.",
                Type.ACTIVE,
                0,
                Identifier.fromNamespaceAndPath("totality", "textures/ability/harvest.png"),
                Source.DEFAULT,
                "Default Ability",
                "The land provides for those who tend it."
        );
    }

    @Override
    public boolean isDefault() { return true; }

    // -------------------------------------------------------------------------
    // Client side
    // -------------------------------------------------------------------------

    @Override
    public @Nullable AbilityContext getContext(Minecraft mc, LocalPlayer player) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK)
            return AbilityContext.NONE;

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        BlockState state = mc.level.getBlockState(pos);

        if (!isHarvestable(state))
            return AbilityContext.NONE;

        return new AbilityContext(pos, state, "Harvest");
    }

    // -------------------------------------------------------------------------
    // Server side
    // -------------------------------------------------------------------------

    @Override
    public void onActivate(ServerPlayer player, @Nullable AbilityContext context) {
        if (context == null) return;

        BlockPos pos = context.pos();
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);

        if (!isHarvestable(state)) return;

        Block block = state.getBlock();
        boolean greenThumb = hasGreenThumb(player);
        boolean isAlchemyIngredient = state.is(ModTags.HARVESTABLE);

        if (isMatureCrop(state)) {
            harvestCrop(player, level, pos, state, block);
        } else if (block instanceof SweetBerryBushBlock) {
            harvestSweetBerries(player, level, pos, state);
        } else if (block instanceof NetherWartBlock) {
            harvestNetherWart(player, level, pos, state);
        } else if (block instanceof CocoaBlock) {
            harvestCocoa(player, level, pos, state);
        } else if (block instanceof CaveVinesPlantBlock || block instanceof CaveVinesBlock) {
            harvestCaveVines(player, level, pos, state);
        } else if (block instanceof MountainFlowerBushBlock flowerBush) {
            harvestMountainFlowerBush(player, level, pos, state, flowerBush.getFlowerItem());
        } else {
            harvestAndCollect(player, level, pos, state, greenThumb && isAlchemyIngredient);
        }
    }

    // -------------------------------------------------------------------------
    // Harvest helpers
    // -------------------------------------------------------------------------

    private void harvestCrop(ServerPlayer player, ServerLevel level,
                             BlockPos pos, BlockState state, Block block) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
        removeSeedFromDrops(drops, block, level, pos);
        playHarvestSound(level, pos);
        giveToPlayer(player, drops,false);
    }

    private void harvestMountainFlowerBush(ServerPlayer player, ServerLevel level,
                                           BlockPos pos, BlockState state, Item flowerItem) {
        if (state.getValue(MountainFlowerBushBlock.HARVESTED)) return;
        Block.popResource(level, pos, new ItemStack(flowerItem));
        level.setBlock(pos, state.setValue(MountainFlowerBushBlock.HARVESTED, true), Block.UPDATE_ALL);
        playHarvestSound(level, pos);
    }

    private void harvestSweetBerries(ServerPlayer player, ServerLevel level,
                                     BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        level.setBlock(pos, state.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_ALL);
        playHarvestSound(level, pos);
        giveToPlayer(player, drops,false);
    }

    private void harvestNetherWart(ServerPlayer player, ServerLevel level,
                                   BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        // Reset to age 0
        level.setBlock(pos, state.setValue(NetherWartBlock.AGE, 0), Block.UPDATE_ALL);
        // Remove one nether wart from drops for replanting
        for (ItemStack stack : drops) {
            if (stack.is(net.minecraft.world.item.Items.NETHER_WART)) {
                stack.shrink(1);
                break;
            }
        }
        playHarvestSound(level, pos);
        giveToPlayer(player, drops,false);
    }

    private void harvestCocoa(ServerPlayer player, ServerLevel level,
                              BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        // Reset to age 0, keep facing
        level.setBlock(pos, state.setValue(CocoaBlock.AGE, 0), Block.UPDATE_ALL);
        // Remove one cocoa bean for replanting
        for (ItemStack stack : drops) {
            if (stack.is(net.minecraft.world.item.Items.COCOA_BEANS)) {
                stack.shrink(1);
                break;
            }
        }
        playHarvestSound(level, pos);
        giveToPlayer(player, drops,false);
    }

    private void harvestCaveVines(ServerPlayer player, ServerLevel level,
                                  BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        // Remove berries but keep the vine — set berries to false
        if (state.getBlock() instanceof CaveVinesPlantBlock) {
            level.setBlock(pos, state.setValue(CaveVines.BERRIES, false), Block.UPDATE_ALL);
        } else if (state.getBlock() instanceof CaveVinesBlock) {
            level.setBlock(pos, state.setValue(CaveVines.BERRIES, false), Block.UPDATE_ALL);
        }
        playHarvestSound(level, pos);
        giveToPlayer(player, drops,false);
    }

    private void harvestAndCollect(ServerPlayer player, ServerLevel level,
                                   BlockPos pos, BlockState state, boolean greenThumb) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, player, ItemStack.EMPTY);
        level.removeBlock(pos, false);
        playHarvestSound(level, pos);
        giveToPlayer(player, drops, greenThumb);
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private void playHarvestSound(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos,
                SoundEvents.GRASS_BREAK,
                SoundSource.BLOCKS,
                1.0f,
                0.8f + level.getRandom().nextFloat() * 0.4f);
    }

    private boolean isHarvestable(BlockState state) {
        Block block = state.getBlock();
        if (isMatureCrop(state)) return true;
        if (block instanceof SweetBerryBushBlock) {
            return state.getValue(SweetBerryBushBlock.AGE) >= 2;
        }
        if (block instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) == 3;
        }
        if (block instanceof CocoaBlock) {
            return state.getValue(CocoaBlock.AGE) == 2;
        }
        if (block instanceof CaveVinesPlantBlock || block instanceof CaveVinesBlock) {
            return CaveVines.hasGlowBerries(state);
        }
        if (block instanceof MountainFlowerBushBlock) {
            return !state.getValue(MountainFlowerBushBlock.HARVESTED);
        }
        return state.is(BlockTags.FLOWERS)
                || state.is(BlockTags.SMALL_FLOWERS)
                || state.is(ModTags.HARVESTABLE);
    }

    private boolean isMatureCrop(BlockState state) {
        if (!(state.getBlock() instanceof CropBlock crop)) return false;
        return crop.isMaxAge(state);
    }

    private void removeSeedFromDrops(List<ItemStack> drops, Block block,
                                     ServerLevel level, BlockPos pos) {
        List<ItemStack> seedDrops = Block.getDrops(block.defaultBlockState(), level, pos, null);
        if (seedDrops.isEmpty()) return;
        ItemStack seed = seedDrops.get(0);
        if (seed.isEmpty()) return;
        for (ItemStack stack : drops) {
            if (stack.getItem() == seed.getItem()) {
                stack.shrink(1);
                return;
            }
        }
    }

    private void giveToPlayer(ServerPlayer player, List<ItemStack> drops, boolean greenThumb) {
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            if (greenThumb) drop.grow(drop.getCount());
            if (!player.getInventory().add(drop)) {
                player.drop(drop, false);
            }
        }
    }

    private boolean hasGreenThumb(ServerPlayer player) {
        return MasteriesComponents.get(player).getMasteries().getUnlockedRank("green_thumb") > 0;
    }
}