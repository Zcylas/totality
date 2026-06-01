// HarvestAbility.java
package zcylas.totality.api.ability.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.api.ability.Ability;
import zcylas.totality.api.ability.AbilityContext;
import zcylas.totality.api.ability.ClientAbilityContext;
import zcylas.totality.api.ability.harvest.HarvestHandler;
import zcylas.totality.api.ability.harvest.HarvestRegistry;


public class HarvestAbility extends Ability implements ClientAbilityContext {

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
            return null;

        BlockPos pos = ((BlockHitResult) mc.hitResult).getBlockPos();
        assert mc.level != null;
        BlockState state = mc.level.getBlockState(pos);

        if (!isHarvestable(state))
            return null;

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

        for (HarvestHandler handler : HarvestRegistry.handlers()) {
            if (handler.canHarvest(state)) {
                handler.harvest(player, level, pos, state);
                return;
            }
        }
    }
    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private boolean isHarvestable(BlockState state) {
        for (HarvestHandler handler : HarvestRegistry.handlers()) {
            if (handler.canHarvest(state)) return true;
        }
        return false;
    }
}