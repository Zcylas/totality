package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.WeatheringCopperBarsBlock;
import net.minecraft.world.level.block.WeatheringCopperBulbBlock;
import net.minecraft.world.level.block.WeatheringCopperChainBlock;
import net.minecraft.world.level.block.WeatheringCopperChestBlock;
import net.minecraft.world.level.block.WeatheringCopperDoorBlock;
import net.minecraft.world.level.block.WeatheringCopperFullBlock;
import net.minecraft.world.level.block.WeatheringCopperGolemStatueBlock;
import net.minecraft.world.level.block.WeatheringCopperGrateBlock;
import net.minecraft.world.level.block.WeatheringCopperSlabBlock;
import net.minecraft.world.level.block.WeatheringCopperStairBlock;
import net.minecraft.world.level.block.WeatheringCopperTrapDoorBlock;
import net.minecraft.world.level.block.WeatheringLanternBlock;
import net.minecraft.world.level.block.WeatheringLightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4A: random-tick copper oxidation runs inside {@link BlockDamageStorage#transaction}. Every
 * weathering copper block (all 13 classes implementing {@code WeatheringCopper}, the only {@code ChangeOverTimeBlock})
 * weathers from its own {@code randomTick} through {@code ChangeOverTimeBlock.changeOverTime} ->
 * {@code level.setBlockAndUpdate}; the tick is wrapped whole and runs unchanged. Doors weather from the lower half
 * (the Integrity owner); a double Copper Chest weathers from its non-RIGHT half and vanilla converts the partner.
 */
@Mixin({WeatheringCopperFullBlock.class, WeatheringCopperStairBlock.class, WeatheringCopperSlabBlock.class,
        WeatheringCopperDoorBlock.class, WeatheringCopperTrapDoorBlock.class, WeatheringCopperGrateBlock.class,
        WeatheringCopperBulbBlock.class, WeatheringCopperBarsBlock.class, WeatheringCopperChainBlock.class,
        WeatheringCopperChestBlock.class, WeatheringCopperGolemStatueBlock.class, WeatheringLanternBlock.class,
        WeatheringLightningRodBlock.class})
public abstract class WeatheringTransformationMixin {

    @WrapMethod(method = "randomTick")
    private void totality$weatherInPlace(BlockState state, ServerLevel level, BlockPos pos, RandomSource random, Operation<Void> original) {
        BlockDamageStorage.transaction(level, pos, () -> original.call(state, level, pos, random));
    }
}
