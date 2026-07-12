package zcylas.totality.mixin;

import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import zcylas.totality.api.rpg.rest.RestSessionManager;

@Mixin(Player.class)
public abstract class PlayerRestSleepMixin {

    // Player#tick() force-wakes anyone whose isSleeping() is true the instant the BedRule check
    // goes false (e.g. the moment it's no longer night) — correct for a real spontaneous bed sleep,
    // but wrong for a Rest session that's deliberately still in progress (a Nap, an outdoor Long
    // Rest, a Long Rest at a bed during the day — see RestSessionManager's class doc, all of which
    // now ride real vanilla sleep). Redirecting only this one call site — rather than cancelling
    // the whole tick() branch — leaves every other path to stopSleepInBed (the sneak-to-get-up
    // action, damage, disconnect) completely untouched.
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;stopSleepInBed(ZZ)V"))
    private void totality$suppressAutoWakeDuringRest(Player player, boolean wakeImmediately, boolean updateLevelList) {
        if (RestSessionManager.isSuppressingAutoWake(player.getUUID())) return;
        player.stopSleepInBed(wakeImmediately, updateLevelList);
    }
}
