package zcylas.totality.mixin.client;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import zcylas.totality.api.rpg.race.ClientRaceManager;
import zcylas.totality.api.rpg.race.Race;
import zcylas.totality.api.rpg.race.RaceComponents;

@Mixin(LivingEntity.class)
public class PlayerScaleMixin {

    @Inject(method = "getScale", at = @At("RETURN"), cancellable = true)
    private void totality$modifyScaleForRace(CallbackInfoReturnable<Float> cir) {
        if (!((LivingEntity)(Object)this instanceof Player player)) return;

        // Get race from component on server, ClientRaceManager on client
        Race race = null;
        if (player.level().isClientSide()) {
            race = ClientRaceManager.getRace();
        } else if (player instanceof ServerPlayer serverPlayer) {
            race = RaceComponents.getRace(serverPlayer);
        }

        if (race == null) return;

        float scale = switch (race) {
            case DWARF -> 0.75f;
            default    -> 1.0f;
        };

        if (scale != 1.0f) {
            cir.setReturnValue(cir.getReturnValue() * scale);
        }
    }
}