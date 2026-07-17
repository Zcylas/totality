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
import zcylas.totality.api.rpg.combat.PowerAttackManager;
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

        // Power Attack only makes sense against an actual combat target — with no valid target
        // (a block, air, or an unattackable/dead/self entity) let vanilla's own startAttack
        // proceed untouched, so mining/interaction is never interfered with (correction pass,
        // Part C). Previously this cancelled unconditionally whenever a weapon was held, which is
        // why holding LMB on a block (e.g. chopping wood with an axe) incorrectly began charging.
        if (!totality$hasValidPowerAttackTarget(client)) return;

        totality$holdingAttack = true;
        cir.setReturnValue(false);
        cir.cancel();
    }

    /** Shared client-side gate for {@link #totality$interceptAttack}/{@link #totality$tickHold} —
     *  delegates to {@link PowerAttackManager#isValidTarget}, the exact same predicate the server
     *  re-checks before ever consuming Stamina, so the two can never disagree about what counts as
     *  a valid target (correction pass, Part C). */
    @Unique
    private static boolean totality$hasValidPowerAttackTarget(Minecraft client) {
        return client.player != null && PowerAttackManager.isValidTarget(client.player, client.crosshairPickEntity);
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

        // Offhand Power Attack only makes sense against an actual combat target — with no valid
        // target (a block, air, or an unattackable/dead/self entity) let vanilla's own
        // startUseItem proceed untouched, exactly mirroring totality$interceptAttack's mainhand
        // gate (correction pass, Part C). Previously this cancelled unconditionally whenever
        // dual-wielding, which let the offhand charge begin — and later complete, spending
        // Stamina and triggering the flash/sound — while looking at a block or air; the final
        // attack PAYLOAD was already suppressed by totality$fireOffhandAttack's own LivingEntity
        // check, but nothing upstream of that stopped the charge itself from starting/completing.
        if (!totality$hasValidPowerAttackTarget(client)) return;

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
        // Re-checked every tick, not just at the moment the charge began — if the player looks
        // away from the target (or it dies/leaves range) partway through the charge, the attempt
        // must cancel instead of completing (correction pass, Part C).
        boolean targetStillValid = totality$holdingAttack && totality$hasValidPowerAttackTarget(client);

        if (totality$holdingAttack && mouseHeld && hasWeapon && targetStillValid) {
            totality$holdTicks++;

            if (totality$holdTicks == POWER_ATTACK_HOLD_TICKS) {
                // Attacker-specific legality (PvP-disabled, team friendly-fire, invulnerability) —
                // checked client-side too (correction pass, Part D), using the SAME
                // PowerAttackManager.isAttackerLegal predicate the server independently
                // re-validates before ever spending Stamina. This client-side check is optimistic
                // only (never trusted alone), but it uses already-synced authoritative state (the
                // PVP game rule, team data) specifically so an attack the server would reject never
                // shows accepted flash/sound feedback in the meantime — no speculative networking
                // added for this. The ordinary swing/attack itself still always happens below,
                // exactly as an early-release/normal attack would; only the POWER-specific payload,
                // flash, and sound are gated.
                boolean legal = client.crosshairPickEntity instanceof net.minecraft.world.entity.LivingEntity livingTarget
                        && PowerAttackManager.isAttackerLegal(client.player, livingTarget);
                if (legal) {
                    ClientPlayNetworking.send(new PowerAttackPayload(client.crosshairPickEntity.getId()));
                }
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
                if (legal) {
                    PowerAttackFlash.trigger();
                    client.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
                }
                totality$holdTicks = 0;
                totality$holdingAttack = false;
            }
        } else if (totality$holdingAttack && (!mouseHeld || !targetStillValid)) {
            // Released early, or the target became invalid mid-charge — cancel the power-attack
            // attempt safely: PowerAttackPayload is never sent, so no Stamina is spent on an
            // attack that was never valid (correction pass, Part C). Falls back to the existing
            // early-release behavior (a normal, non-power attack/swing against whatever's
            // currently under the crosshair, if anything).
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
        // Re-checked every tick, not just at the moment the charge began — same target-loss
        // safety net as the mainhand's targetStillValid above (correction pass, Part C).
        boolean offhandTargetStillValid = totality$offhandHolding && totality$hasValidPowerAttackTarget(client);

        if (totality$offhandHolding && offhandHeld && dualWielding && offhandTargetStillValid) {
            totality$offhandHoldTicks++;

            if (totality$offhandHoldTicks == POWER_ATTACK_HOLD_TICKS) {
                // Same client-side attacker-legality gate as the mainhand above (correction pass,
                // Part D) — downgrades to a normal (non-power) offhand swing instead of showing
                // accepted power-attack feedback for a target the server would reject.
                boolean legal = client.crosshairPickEntity instanceof LivingEntity livingTarget
                        && PowerAttackManager.isAttackerLegal(client.player, livingTarget);
                totality$fireOffhandAttack(client, legal);
                totality$offhandHoldTicks = 0;
                totality$offhandHolding = false;
            }
        } else if (totality$offhandHolding && (!offhandHeld || !offhandTargetStillValid)) {
            // Released early, or the target became invalid mid-charge — cancel safely: no
            // PowerAttackPayload-equivalent (powerAttack=true) is ever sent, so no Power-Attack
            // Stamina, flash, or strong-attack sound occurs for an attempt that was never or is no
            // longer valid (correction pass, Part C). Falls back to a normal (non-power) offhand
            // swing, matching the mainhand's identical early-release behavior.
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
    // The flash/strong-attack sound are gated on the SAME LivingEntity check as the payload itself
    // (correction pass, Part C) — by the time this is reached with powerAttack=true, totality$tickHold's
    // own offhandTargetStillValid gate already guarantees a valid LivingEntity target; this check
    // is kept anyway as defense-in-depth so the two conditions can never drift apart.
    @Unique
    private void totality$fireOffhandAttack(Minecraft client, boolean powerAttack) {
        DualWieldTracker.startOffhandSwing();
        DualWieldTracker.resetOffhandAttackStrength();
        boolean hasLivingTarget = client.crosshairPickEntity instanceof LivingEntity;
        if (hasLivingTarget) {
            ClientPlayNetworking.send(new OffhandAttackPayload(client.crosshairPickEntity.getId(), powerAttack));
        }
        if (powerAttack && hasLivingTarget) {
            PowerAttackFlash.trigger();
            client.player.playSound(net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_STRONG, 1.0f, 1.0f);
        }
    }
}
