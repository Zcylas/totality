package zcylas.totality.client.combat;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import zcylas.totality.api.rpg.combat.weapon.WeaponDataResolver;

public final class DualWieldTracker {
    public static boolean isDualBlocking = false;

    // Offhand's own attack-strength ticker — mirrors LivingEntity.attackStrengthTicker/
    // getAttackStrengthScale, but tracked independently since vanilla only exposes one such
    // ticker per player (tied to the mainhand item). Starts "ready" so the indicator doesn't
    // show a fake cooldown on world join.
    private static int offhandAttackStrengthTicker = Integer.MAX_VALUE / 2;

    public static void resetOffhandAttackStrength() {
        offhandAttackStrengthTicker = 0;
    }

    public static void tickOffhandAttackStrength() {
        offhandAttackStrengthTicker++;
    }

    /** Mirrors LivingEntity.getCurrentItemAttackStrengthDelay, timed off DualWieldCooldownTracker's
     *  doubled-cooldown formula (ceil(40/attackSpeed)) so it matches the server-side gate exactly
     *  instead of vanilla's normal (halved) 20/attackSpeed mainhand cadence. Resolves speed from
     *  the offhand item itself (see WeaponDataResolver.resolveAttackSpeed) rather than the shared
     *  player attribute — that attribute only ever reflects the mainhand's modifiers, which made
     *  this indicator show the wrong pace for both hands even after the real cooldown was fixed. */
    public static double getOffhandAttackStrengthDelay(LivingEntity player) {
        double attackSpeed = WeaponDataResolver.resolveAttackSpeed(player, player.getOffhandItem());
        return attackSpeed > 0 ? Math.ceil(40.0 / attackSpeed) : 40.0;
    }

    /** Mirrors LivingEntity.getAttackStrengthScale — see getOffhandAttackStrengthDelay. */
    public static float getOffhandAttackStrengthScale(LivingEntity player) {
        double delay = getOffhandAttackStrengthDelay(player);
        return Mth.clamp(offhandAttackStrengthTicker / (float) delay, 0f, 1f);
    }

    // Custom offhand swing progress — decoupled from vanilla's single-slot swingTime/swingingArm
    // so both arms can animate simultaneously. Own-view only (see project_dual_wield memory for
    // the multiplayer-sync gap: other players still see vanilla's normal single-slot behavior).
    public static float offhandAttackAnim = 0f;
    private static float offhandOAttackAnim = 0f;
    private static boolean offhandSwinging = false;
    private static int offhandSwingTime = 0;

    public static void startOffhandSwing() {
        offhandSwinging = true;
        offhandSwingTime = 0;
    }

    /** Call once per client tick with the offhand item's current swing duration. */
    public static void tick(int swingDuration) {
        offhandOAttackAnim = offhandAttackAnim;
        if (offhandSwinging) {
            offhandSwingTime++;
            if (offhandSwingTime >= swingDuration) {
                offhandSwingTime = 0;
                offhandSwinging = false;
            }
        } else {
            offhandSwingTime = 0;
        }
        offhandAttackAnim = swingDuration > 0 ? (float) offhandSwingTime / swingDuration : 0f;
    }

    /** Smoothly interpolated progress for sub-tick rendering (mirrors LivingEntity.getAttackAnim). */
    public static float getOffhandAttackAnim(float partialTick) {
        float diff = offhandAttackAnim - offhandOAttackAnim;
        if (diff < 0f) diff += 1.0f;
        return offhandOAttackAnim + diff * partialTick;
    }

    // Cached once per frame in AvatarRendererMixin (which has partialTick) so HumanoidModelMixin's
    // setupAnim (which does NOT receive partialTick — it's baked into render state earlier in the
    // frame, same as vanilla's own attackTime) can read a smoothly-interpolated value too.
    public static float renderOffhandAttackAnim = 0f;

    private DualWieldTracker() {}
}
