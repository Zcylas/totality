package zcylas.totality.item.fluid;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.industrial.fluid.FluidComponents;
import zcylas.totality.block.fluid.FluidTankBlock;

import java.util.function.Consumer;

public class FluidTankItem extends BlockItem {

    public FluidTankItem(FluidTankBlock block, Item.Properties properties) {
        super(block, properties);
    }

    private long getCapacityMb() {
        return ((FluidTankBlock) getBlock()).getCapacityMb();
    }

    private long getCapacityDroplets() {
        return getCapacityMb() * 81L;
    }

    // --- Mode helpers ---

    public static boolean isInsertMode(ItemStack stack) {
        return stack.getOrDefault(FluidComponents.FLUID_TANK_MODE, true);
    }

    public static void setInsertMode(ItemStack stack, boolean insert) {
        stack.set(FluidComponents.FLUID_TANK_MODE, insert);
    }

    public static void toggleMode(ItemStack stack) {
        setInsertMode(stack, !isInsertMode(stack));
    }

    // --- Fluid data helpers ---

    private static long getStoredDroplets(ItemStack stack) {
        var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData == null) return 0L;
        CompoundTag tag = beData.copyTagWithoutId();
        return tag.getLongOr("Amount", 0L);
    }

    private static FluidVariant getStoredVariant(ItemStack stack) {
        var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData == null) return FluidVariant.blank();
        CompoundTag tag = beData.copyTagWithoutId();
        return tag.read("Fluid", FluidVariant.CODEC).orElse(FluidVariant.blank());
    }

    private static void setFluidData(ItemStack stack, FluidVariant variant, long amount) {
        var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData == null) return;
        CompoundTag tag = beData.copyTagWithoutId();
        tag.putLong("Amount", amount);
        tag.store("Fluid", FluidVariant.CODEC, variant);
        stack.set(DataComponents.BLOCK_ENTITY_DATA,
                TypedEntityData.of(beData.type(), tag));
    }

    // --- Right click interaction ---

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return super.useOn(context);

        if (!player.isSecondaryUseActive()) {
            return super.useOn(context);
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        boolean insertMode = isInsertMode(stack);

        if (insertMode) {
            return handleInsert(level, pos, player, stack);
        } else {
            return handleExtract(level, pos, context.getClickedFace(), player, stack);
        }
    }

    private InteractionResult handleInsert(Level level, BlockPos pos,
                                           Player player, ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        BlockPos fluidPos = findAdjacentFluid(level, pos);

        if (fluidPos == null) {
            return tryInsertFromContainer(level, pos, player, stack);
        }

        BlockState state = level.getBlockState(fluidPos);
        Fluid fluid = state.getFluidState().getType();
        if (fluid == Fluids.EMPTY) return InteractionResult.PASS;

        FluidVariant variant = FluidVariant.of(fluid);
        long stored = getStoredDroplets(stack);
        FluidVariant storedVariant = getStoredVariant(stack);
        long capacity = getCapacityDroplets();

        if (!storedVariant.isBlank() && !storedVariant.equals(variant)) {
            player.sendOverlayMessage(
                    Component.literal("Different fluid already stored!")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        if (stored + FluidConstants.BUCKET > capacity) {
            player.sendOverlayMessage(
                    Component.literal("Not enough space!")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        level.setBlock(fluidPos, Blocks.AIR.defaultBlockState(), 3);
        setFluidData(stack, variant, stored + FluidConstants.BUCKET);
        showStatusMessage(player, stack);
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private BlockPos findAdjacentFluid(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof LiquidBlock && state.getFluidState().isSource()) {
            return pos;
        }

        for (Direction dir : Direction.values()) {
            BlockPos adjacent = pos.relative(dir);
            BlockState adjacentState = level.getBlockState(adjacent);
            if (adjacentState.getBlock() instanceof LiquidBlock
                    && adjacentState.getFluidState().isSource()) {
                return adjacent;
            }
        }

        return null;
    }

    private InteractionResult tryInsertFromContainer(Level level, BlockPos pos,
                                                     Player player, ItemStack stack) {
        if (!(level instanceof ServerLevel)) {
            return InteractionResult.PASS;
        }

        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, Direction.UP);
        if (storage == null || !storage.supportsExtraction()) {
            return InteractionResult.PASS;
        }

        long stored = getStoredDroplets(stack);
        FluidVariant storedVariant = getStoredVariant(stack);
        long space = getCapacityDroplets() - stored;

        if (space < FluidConstants.BUCKET) {
            player.sendOverlayMessage(
                    Component.literal("Not enough space!")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        try (Transaction transaction = Transaction.openOuter()) {
            for (var view : storage) {
                if (view.isResourceBlank()) continue;
                FluidVariant variant = view.getResource();

                if (!storedVariant.isBlank() && !storedVariant.equals(variant)) continue;

                long extracted = storage.extract(variant, FluidConstants.BUCKET, transaction);
                if (extracted == FluidConstants.BUCKET) {
                    setFluidData(stack, variant, stored + extracted);
                    transaction.commit();
                    showStatusMessage(player, stack);
                    return InteractionResult.SUCCESS;
                }
                break;
            }
        }

        return InteractionResult.PASS;
    }

    private InteractionResult handleExtract(Level level, BlockPos pos,
                                            Direction face, Player player,
                                            ItemStack stack) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        long stored = getStoredDroplets(stack);
        FluidVariant storedVariant = getStoredVariant(stack);

        if (stored < FluidConstants.BUCKET || storedVariant.isBlank()) {
            player.sendOverlayMessage(
                    Component.literal("Not enough fluid!")
                            .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        BlockState state = level.getBlockState(pos);

        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, pos, face.getOpposite());
        if (storage != null && storage.supportsInsertion()) {
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(storedVariant, FluidConstants.BUCKET, transaction);
                if (inserted == FluidConstants.BUCKET) {
                    long newAmount = stored - inserted;
                    setFluidData(stack,
                            newAmount <= 0 ? FluidVariant.blank() : storedVariant,
                            Math.max(0, newAmount));
                    transaction.commit();
                    showStatusMessage(player, stack);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        BlockPos placePos = state.canBeReplaced() ? pos : pos.relative(face);
        BlockState placeState = level.getBlockState(placePos);

        if (!placeState.canBeReplaced()) {
            return InteractionResult.PASS;
        }

        Fluid fluid = storedVariant.getFluid();
        if (fluid instanceof FlowingFluid flowingFluid) {
            level.setBlock(placePos,
                    flowingFluid.getSource().defaultFluidState().createLegacyBlock(), 3);
        } else {
            level.setBlock(placePos, fluid.defaultFluidState().createLegacyBlock(), 3);
        }

        long newAmount = stored - FluidConstants.BUCKET;
        setFluidData(stack,
                newAmount <= 0 ? FluidVariant.blank() : storedVariant,
                Math.max(0, newAmount));

        showStatusMessage(player, stack);
        return InteractionResult.SUCCESS;
    }

    private void showStatusMessage(Player player, ItemStack stack) {
        boolean insert = isInsertMode(stack);
        long amountMb = getStoredDroplets(stack) / 81L;
        long capacityMb = getCapacityMb();
        FluidVariant variant = getStoredVariant(stack);

        String modeName = insert ? "INSERT" : "EXTRACT";
        ChatFormatting modeColor = insert ? ChatFormatting.GREEN : ChatFormatting.GOLD;

        Component modeText = Component.literal("[" + modeName + "] ").withStyle(modeColor);

        Component fluidText;
        if (variant.isBlank() || amountMb <= 0) {
            fluidText = Component.literal("Empty").withStyle(ChatFormatting.GRAY);
        } else {
            String fluidName = Component.translatable(
                    variant.getFluid().defaultFluidState()
                            .createLegacyBlock()
                            .getBlock()
                            .getDescriptionId()
            ).getString().toUpperCase();

            fluidText = Component.literal(fluidName + " " + amountMb + "/" + capacityMb + " mB")
                    .withStyle(ChatFormatting.AQUA);
        }

        player.sendOverlayMessage(modeText.copy().append(fluidText));
    }

    // --- Tooltip ---

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                TooltipDisplay display, Consumer<Component> builder,
                                TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);

        boolean insert = isInsertMode(stack);
        builder.accept(Component.literal(insert ? "[INSERT]" : "[EXTRACT]")
                .withStyle(insert ? ChatFormatting.GREEN : ChatFormatting.GOLD));

        var beData = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (beData == null) return;

        CompoundTag tag = beData.copyTagWithoutId();
        long amount = tag.getLongOr("Amount", 0L);
        if (amount <= 0) return;

        long amountMb = amount / 81L;
        long capacityMb = getCapacityMb();

        FluidVariant variant = tag.read("Fluid", FluidVariant.CODEC)
                .orElse(FluidVariant.blank());

        if (!variant.isBlank()) {
            String fluidName = Component.translatable(
                    variant.getFluid().defaultFluidState()
                            .createLegacyBlock()
                            .getBlock()
                            .getDescriptionId()
            ).getString().toUpperCase();

            builder.accept(Component.literal(fluidName).withStyle(ChatFormatting.WHITE));
            builder.accept(Component.literal(amountMb + "/" + capacityMb + " mB")
                    .withStyle(ChatFormatting.AQUA));
        }
    }
}