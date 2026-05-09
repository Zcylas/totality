package zcylas.totality.block.energy;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import zcylas.totality.api.industrial.energy.UEFormat;
import zcylas.totality.blockentity.energy.EnergyCellBlockEntity;
import zcylas.totality.init.ModBlockEntities;

import java.util.List;

public class EnergyCellBlock extends BaseEntityBlock {

    public static final MapCodec<EnergyCellBlock> CODEC = simpleCodec(
            properties -> new EnergyCellBlock(properties, 500_000, 64, 64));

    private final long capacity;
    private final long maxInput;
    private final long maxOutput;

    public EnergyCellBlock(BlockBehaviour.Properties properties, long capacity, long maxInput, long maxOutput) {
        super(properties);
        this.capacity = capacity;
        this.maxInput = maxInput;
        this.maxOutput = maxOutput;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public long getCapacity() { return capacity; }
    public long getMaxInput() { return maxInput; }
    public long getMaxOutput() { return maxOutput; }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new EnergyCellBlockEntity(pos, state, capacity, maxInput, maxOutput);
    }

    @Override
    public @Nullable <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return createTickerHelper(type, ModBlockEntities.ENERGY_CELL,
                (world, pos, blockState, be) -> be.tick());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof EnergyCellBlockEntity cellBE) {
                serverPlayer.openMenu(cellBE);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos,
                                        BlockState state, Player player) {
        if (!level.isClientSide()) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof EnergyCellBlockEntity cellBE) {
                net.minecraft.world.item.ItemStack drop = new net.minecraft.world.item.ItemStack(this);
                drop.applyComponents(cellBE.collectComponents());
                Block.popResource(level, pos, drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    public List<Component> getInfoLines() {
        return List.of(
                Component.literal("Stores UE energy."),
                Component.literal("Capacity: " + UEFormat.energy(capacity) + " UE"),
                Component.literal("Max Input: " + UEFormat.energy(maxInput) + " UE/t"),
                Component.literal("Max Output: " + UEFormat.energy(maxOutput) + " UE/t")
        );
    }
}