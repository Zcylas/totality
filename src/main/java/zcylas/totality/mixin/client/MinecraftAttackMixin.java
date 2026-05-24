package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import zcylas.totality.client.renderer.hud.PowerAttackFlash;
import zcylas.totality.init.ModTags;
import zcylas.totality.networking.combat.PowerAttackPayload;

@Mixin(Minecraft.class)
public class MinecraftAttackMixin {

    @Unique
    private int totality$holdTicks = 0;
    @Unique
    private boolean totality$holdingAttack = false;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void totality$interceptAttack(CallbackInfoReturnable<Boolean> cir) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.player == null) return;

        ItemStack held = client.player.getMainHandItem();
        boolean hasWeapon = held.is(ModTags.ONE_HANDED_WEAPONS)
                || held.is(ModTags.TWO_HANDED_WEAPONS);

        if (!hasWeapon) return;

        totality$holdingAttack = true;
        cir.setReturnValue(false);
        cir.cancel();
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void totality$tickHold(CallbackInfo ci) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.player == null) return;

        ItemStack held = client.player.getMainHandItem();
        boolean hasWeapon = held.is(ModTags.ONE_HANDED_WEAPONS)
                || held.is(ModTags.TWO_HANDED_WEAPONS);
        boolean mouseHeld = client.options.keyAttack.isDown();

        if (totality$holdingAttack && mouseHeld && hasWeapon) {
            totality$holdTicks++;

            if (totality$holdTicks == 12) {
                ClientPlayNetworking.send(new PowerAttackPayload());
                client.player.swing(InteractionHand.MAIN_HAND, true);
                if (client.gameMode != null && client.crosshairPickEntity != null) {
                    client.gameMode.attack(client.player, client.crosshairPickEntity);
                    if (client.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity living) {
                        zcylas.totality.client.renderer.hud.MobHealthBarHud.onPlayerHitMob(living);
                    }
                }
                // Flash + sound
                PowerAttackFlash.trigger();
                client.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
                totality$holdTicks = 0;
                totality$holdingAttack = false;
            }
        } else if (totality$holdingAttack && !mouseHeld) {
            if (totality$holdTicks < 12) {
                if (client.gameMode != null && client.crosshairPickEntity != null) {
                    client.gameMode.attack(client.player, client.crosshairPickEntity);
                    if (client.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity living) {
                        zcylas.totality.client.renderer.hud.MobHealthBarHud.onPlayerHitMob(living);
                    }
                }
                client.player.swing(InteractionHand.MAIN_HAND);
            }
            totality$holdTicks = 0;
            totality$holdingAttack = false;
        } else if (!mouseHeld) {
            totality$holdTicks = 0;
        }
    }
}