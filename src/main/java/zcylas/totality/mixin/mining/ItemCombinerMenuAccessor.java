package zcylas.totality.mixin.mining;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The Anvil menu's block position (via its {@code ContainerLevelAccess}), for the degradation transaction. */
@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {
    @Accessor("access")
    ContainerLevelAccess totality$getAccess();
}
