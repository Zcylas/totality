// zcylas/totality/api/ability/AbilityContext.java
package zcylas.totality.api.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Contextual data produced client-side by raycasting the crosshair.
 * Sent to the server when the ability is activated so the server
 * knows exactly what the player was targeting.
 */
public record AbilityContext(
        BlockPos pos,
        BlockState state,
        /** Label shown in the HUD prompt, e.g. "Harvest" */
        String promptLabel
) {
    /** Null context = ability is not applicable right now, hide prompt. */
}