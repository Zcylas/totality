package zcylas.totality.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gamerules.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.api.equipment.EquipmentComponents;

/**
 * Drops Totality's custom equipment on actual death, right after the vanilla inventory, under the same rule
 * ({@code keepInventory} off). {@code Player#dropEquipment} runs only from death loot, never on dimension travel or
 * respawn; a spectator's death never reaches it (as for the vanilla inventory).
 */
@Mixin(Player.class)
public abstract class PlayerEquipmentDeathDropMixin {

    @Inject(method = "dropEquipment", at = @At("TAIL"))
    private void totality$dropCustomEquipment(ServerLevel level, CallbackInfo ci) {
        if ((Object) this instanceof ServerPlayer player && !level.getGameRules().get(GameRules.KEEP_INVENTORY)) {
            EquipmentComponents.get(player).dropOnDeath();
        }
    }
}
