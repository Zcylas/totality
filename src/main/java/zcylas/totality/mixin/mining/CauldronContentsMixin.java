package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractCauldronBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4B: every player/item Cauldron interaction (buckets, bottles, dyeing, banners, powder snow...)
 * runs inside {@link BlockDamageStorage#transaction}. All of them dispatch from {@code AbstractCauldronBlock.useItemOn}
 * to {@code CauldronInteraction} lambdas that change only this Cauldron; the whole method is wrapped and runs unchanged.
 * Environmental changes are wrapped in {@link CauldronFillingMixin} and {@link LayeredCauldronEmptyingMixin}.
 */
@Mixin(AbstractCauldronBlock.class)
public abstract class CauldronContentsMixin {

    @WrapMethod(method = "useItemOn")
    private InteractionResult totality$interactInPlace(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                       InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
        if (!(level instanceof ServerLevel serverLevel)) return original.call(stack, state, level, pos, player, hand, hit);
        return BlockDamageStorage.transaction(serverLevel, pos, () -> original.call(stack, state, level, pos, player, hand, hit));
    }
}
