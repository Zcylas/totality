package zcylas.totality.mixin;

import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import zcylas.totality.init.items.SpellComponentItems;

/**
 * Makes bats periodically drop Bat Guano — the same mechanic chickens use for eggs.
 * Every 3000–7000 ticks (~2.5–5.8 minutes) a roosting or flying bat leaves a guano.
 * Server-side only.
 */
@Mixin(Bat.class)
public class BatMixin {

    @Unique
    private int totality$guanoTimer = -1; // -1 = not yet initialised

    @Inject(at = @At("TAIL"),
            method = "customServerAiStep(Lnet/minecraft/server/level/ServerLevel;)V")
    private void onServerAiStep(net.minecraft.server.level.ServerLevel serverLevel,
                                CallbackInfo ci) {
        Bat bat = (Bat)(Object)this;

        if (totality$guanoTimer < 0) {
            totality$guanoTimer = bat.getRandom().nextInt(4000) + 3000;
        }

        if (--totality$guanoTimer <= 0) {
            serverLevel.addFreshEntity(new ItemEntity(
                    serverLevel,
                    bat.getX(), bat.getY(), bat.getZ(),
                    new ItemStack(SpellComponentItems.BAT_GUANO)));
            totality$guanoTimer = bat.getRandom().nextInt(4000) + 3000;
        }
    }
}