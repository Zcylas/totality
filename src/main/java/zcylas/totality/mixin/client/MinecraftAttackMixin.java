package zcylas.totality.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import zcylas.totality.client.renderer.hud.PowerAttackFlash;
import zcylas.totality.api.rpg.combat.weapon.TotalityMeleeWeaponItem;
import zcylas.totality.init.ModTags;
import zcylas.totality.client.combat.DualWieldTracker;
import zcylas.totality.networking.combat.OffhandAttackPayload;
import zcylas.totality.networking.combat.PowerAttackPayload;

@Mixin(Minecraft.class)
public class MinecraftAttackMixin {

    private static final int POWER_ATTACK_HOLD_TICKS = 12;

    @Unique private int totality$holdTicks = 0;
    @Unique private boolean totality$holdingAttack = false;

    @Unique private int totality$offhandHoldTicks = 0;
    @Unique private boolean totality$offhandHolding = false;

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

    // Skyrim-style independent offhand attack: RMB on a target while dual-wielding.
    // Mainhand (LMB) is untouched and attacks at its own normal speed — no auto-mirroring,
    // no shared cooldown penalty; the offhand is its own weapon triggered by its own input.
    //
    // Mirrors totality$interceptAttack/totality$tickHold's hold-charge-release state machine
    // exactly, but with its own independent counter: holding RMB charges toward the offhand's
    // own power attack, releasing early fires a normal offhand swing — same semantics as LMB,
    // just not sharing LMB's holdTicks (each hand charges on its own).
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void totality$interceptOffhandAttack(CallbackInfo ci) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.player == null) return;
        if (!totality$isDualWielding(client.player.getMainHandItem(), client.player.getOffhandItem())) return;

        // Always cancel vanilla's own use-item handling (block/eat/Grimoire-cast/etc.) while
        // dual-wielding — the actual offhand attack fires from totality$tickHold instead, once
        // RMB is released (or the hold threshold is reached), just like the mainhand.
        ci.cancel();
        totality$offhandHolding = true;
    }

    @Unique
    private static boolean totality$isDualWielding(ItemStack main, ItemStack off) {
        boolean mainIsMelee = main.is(ItemTags.SWORDS) || main.getItem() instanceof TotalityMeleeWeaponItem;
        boolean offIsMelee = off.is(ItemTags.SWORDS) || off.getItem() instanceof TotalityMeleeWeaponItem;
        return mainIsMelee && offIsMelee;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void totality$tickHold(CallbackInfo ci) {
        Minecraft client = (Minecraft)(Object)this;
        if (client.player == null) return;

        DualWieldTracker.tick(client.player.getOffhandItem().getSwingAnimation().duration());
        DualWieldTracker.tickOffhandAttackStrength();

        ItemStack held = client.player.getMainHandItem();
        boolean hasWeapon = held.is(ModTags.ONE_HANDED_WEAPONS)
                || held.is(ModTags.TWO_HANDED_WEAPONS);
        boolean mouseHeld = client.options.keyAttack.isDown();

        if (totality$holdingAttack && mouseHeld && hasWeapon) {
            totality$holdTicks++;

            if (totality$holdTicks == POWER_ATTACK_HOLD_TICKS) {
                ClientPlayNetworking.send(new PowerAttackPayload());
                client.player.swing(InteractionHand.MAIN_HAND, true);
                // Dual-wield power attack: both weapons strike together as one finisher, driven
                // entirely by our own DualWieldTracker animation (not vanilla's swing(), which
                // shares a single animation slot and would stack/conflict with it — see
                // project_dual_wield memory).
                if (totality$isDualWielding(client.player.getMainHandItem(), client.player.getOffhandItem())) {
                    DualWieldTracker.startOffhandSwing();
                }
                if (client.gameMode != null && client.crosshairPickEntity != null) {
                    client.gameMode.attack(client.player, client.crosshairPickEntity);
                    if (client.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity living) {
                        zcylas.totality.client.renderer.hud.MobHealthBarHud.onPlayerHitMob(living);
                    }
                }
                PowerAttackFlash.trigger();
                client.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
                totality$holdTicks = 0;
                totality$holdingAttack = false;
            }
        } else if (totality$holdingAttack && !mouseHeld) {
            if (totality$holdTicks < POWER_ATTACK_HOLD_TICKS) {
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

        // Offhand (RMB) — independent hold/charge/release counter, same shape as the mainhand's
        // above but never shares totality$holdTicks: each hand charges toward its own finisher.
        boolean offhandHeld = client.options.keyUse.isDown();
        boolean dualWielding = totality$isDualWielding(client.player.getMainHandItem(), client.player.getOffhandItem());

        if (totality$offhandHolding && offhandHeld && dualWielding) {
            totality$offhandHoldTicks++;

            if (totality$offhandHoldTicks == POWER_ATTACK_HOLD_TICKS) {
                totality$fireOffhandAttack(client, true);
                totality$offhandHoldTicks = 0;
                totality$offhandHolding = false;
            }
        } else if (totality$offhandHolding && !offhandHeld) {
            if (totality$offhandHoldTicks < POWER_ATTACK_HOLD_TICKS) {
                totality$fireOffhandAttack(client, false);
            }
            totality$offhandHoldTicks = 0;
            totality$offhandHolding = false;
        } else if (!offhandHeld) {
            totality$offhandHoldTicks = 0;
        }
    }

    // Always swings (matching the mainhand's "always swing regardless of target" rule) — only
    // sends the network payload when there's a target, since the server has nothing to resolve
    // otherwise. powerAttack=true rolls with advantage server-side (see OffhandAttackHandler).
    @Unique
    private void totality$fireOffhandAttack(Minecraft client, boolean powerAttack) {
        DualWieldTracker.startOffhandSwing();
        DualWieldTracker.resetOffhandAttackStrength();
        if (client.crosshairPickEntity instanceof LivingEntity) {
            ClientPlayNetworking.send(new OffhandAttackPayload(client.crosshairPickEntity.getId(), powerAttack));
        }
        if (powerAttack) {
            PowerAttackFlash.trigger();
            client.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
        }
    }
}
