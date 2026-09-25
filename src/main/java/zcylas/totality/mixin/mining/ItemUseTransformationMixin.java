package zcylas.totality.mixin.mining;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.HoneycombItem;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import zcylas.totality.api.mining.BlockDamageStorage;

/**
 * Block Breaking V2 Pass 4A: the in-place item transformations run inside {@link BlockDamageStorage#transaction}.
 * Axe (stripping, copper scraping, wax removal), Honeycomb (waxing), Hoe (tilling) and Shovel (flattening) each
 * mutate only the clicked position in {@code useOn} (plus what vanilla carries along from it: a door's other half, a
 * double Copper Chest's partner), so the whole vanilla method is wrapped and runs UNCHANGED: same flags, sounds,
 * particles, advancements, item consumption, tool wear and drops. Server only.
 */
@Mixin({AxeItem.class, HoneycombItem.class, HoeItem.class, ShovelItem.class})
public abstract class ItemUseTransformationMixin {

    @WrapMethod(method = "useOn")
    private InteractionResult totality$transformInPlace(UseOnContext context, Operation<InteractionResult> original) {
        if (!(context.getLevel() instanceof ServerLevel level)) return original.call(context);
        return BlockDamageStorage.transaction(level, context.getClickedPos(), () -> original.call(context));
    }
}
