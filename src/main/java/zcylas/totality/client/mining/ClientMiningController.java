package zcylas.totality.client.mining;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import zcylas.totality.api.mining.MiningOwnership;
import zcylas.totality.api.mining.MiningSourceIdentity;
import zcylas.totality.api.mining.MiningTier;
import zcylas.totality.api.mining.MiningTuning;
import zcylas.totality.init.ModKeybinds;
import zcylas.totality.networking.mining.MiningIntentPayload;
import zcylas.totality.networking.mining.MiningRecoveryPayload;
import zcylas.totality.networking.mining.MiningSwingPayload;
import zcylas.totality.networking.mining.PowerStrikeResultPayload;

/**
 * Client half of Totality mining. It only expresses INTENT (hold / stop / power-swing force) and
 * draws the optional Power Mining meter; it never decides that anything was hit.
 *
 * <p>Normal: hold LMB on a mineable block -> one HOLD_START, one HOLD_STOP on release.
 * Power:  LMB pressed while the Radial Modifier (Left Alt by default) is held and a block is
 * targeted -> POWER_START and the meter sweeps; releasing LMB sends POWER_RELEASE (the server derives the force).
 * Releasing Alt first cancels the attempt. Alt alone does nothing.
 */
public final class ClientMiningController {

    /** The one first-person mining presentation timeline (read by the render mixin). */
    public static final MiningHandAnimation ANIM = new MiningHandAnimation();
    /** The source that began the current presentation; compared with the SAME rule the server uses at contact. */
    private static ItemStack animSource = ItemStack.EMPTY;

    private static boolean prevDown;
    private static boolean pressedWithAlt;
    private static boolean cancelledUntilRelease;
    private static boolean holdSent;
    private static boolean meterActive;
    private static int meterTicks;
    /** Presentation only (Power Mining HUD): Alt held long enough, nothing pressed, Power available. */
    private static boolean idleArmed;
    private static long altHeldSince = -1;
    private static long releaseNanos = -1, cancelNanos = -1, strikeNanos = -1;
    private static float releaseForce;
    private static PowerStrikeResultPayload.Outcome strikeOutcome = PowerStrikeResultPayload.Outcome.MISS;
    /** Alt must be held this long before the idle reticle appears, so the Alt+B chord never flashes it. */
    static final long IDLE_DELAY_NANOS = 150_000_000L;

    private ClientMiningController() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientMiningController::tick);
        // The server tells us the real schedule of every normal swing (cadence included); we animate exactly that.
        ClientPlayNetworking.registerGlobalReceiver(MiningSwingPayload.TYPE, (payload, context) ->
                context.client().execute(() -> onSwing(context.client(), payload)));
        // ...and, when the actual target at contact changed the swing's cadence, its corrected recovery.
        ClientPlayNetworking.registerGlobalReceiver(MiningRecoveryPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ANIM.correctRecovery(payload.recoveryTicks())));
        // What a released Power swing actually did (impact feedback only for a real hit).
        ClientPlayNetworking.registerGlobalReceiver(PowerStrikeResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    strikeOutcome = payload.outcome();
                    strikeNanos = System.nanoTime();
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            reset();
            ANIM.reset();
            idleArmed = false;
            releaseNanos = cancelNanos = strikeNanos = -1;
        });
        PowerMiningMeterHud.register();
        PowerMiningCapture.registerIfRequested();
    }

    /** True when Totality mining, not vanilla, handles the block under the crosshair right now. */
    public static boolean ownsCrosshairBlock(Minecraft mc) {
        if (!survivalMining(mc)) return false;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockPos pos = hit.getBlockPos();
        // The exact rule the server applies when refusing vanilla START/STOP (MiningOwnership).
        return MiningOwnership.owns(mc.gameMode.getPlayerMode(), mc.player.getMainHandItem(), mc.level, pos, mc.level.getBlockState(pos));
    }

    private static boolean survivalMining(Minecraft mc) {
        return mc.player != null && mc.level != null && mc.gameMode != null
                && !mc.player.isUsingItem()
                && MiningOwnership.ownsItemAndMode(mc.gameMode.getPlayerMode(), mc.player.getMainHandItem());
    }

    /** Drawn meter value (presentation only; the server derives the real force from its own timing). */
    public static float meterValue() {
        return MiningTuning.meterValue(meterTicks);
    }

    public static boolean isMeterActive() { return meterActive; }

    /** Ticks of the current power hold (the value {@link #meterValue()} is derived from). */
    public static int meterTicks() { return meterTicks; }

    /** Alt held (and Power Mining available), no mining input yet: the HUD's compact idle reticle. */
    public static boolean isIdleArmed() { return idleArmed; }

    /** {@link System#nanoTime()} of the last POWER_RELEASE sent, or -1. */
    public static long lastReleaseNanos() { return releaseNanos; }

    /** The meter value at that release (the server derives the real force from its own clock). */
    public static float lastReleaseForce() { return releaseForce; }

    /** {@link System#nanoTime()} of the last cancelled power hold, or -1. */
    public static long lastCancelNanos() { return cancelNanos; }

    /** {@link System#nanoTime()} of the last strike result from the server, or -1. */
    public static long lastStrikeNanos() { return strikeNanos; }

    public static PowerStrikeResultPayload.Outcome lastStrikeOutcome() { return strikeOutcome; }

    /**
     * The Alt+B voice chord (push-to-talk held with the modifier) belongs to Voice Dictation: while it is
     * down Power Mining never starts, shows no idle reticle, and an ongoing charge is cancelled.
     */
    static boolean voiceChordDown() {
        return ModKeybinds.isPhysicallyDown(ModKeybinds.VOICE_PUSH_TO_TALK);
    }

    /** Broad visual class from the item's own tags; anything that is not a tool source animates the arm itself. */
    static MiningHandAnimation.Style styleOf(ItemStack stack) {
        if (!MiningTier.isToolSource(stack)) return MiningHandAnimation.Style.HAND;
        if (stack.is(ItemTags.AXES)) return MiningHandAnimation.Style.AXE;
        if (stack.is(ItemTags.SHOVELS)) return MiningHandAnimation.Style.SHOVEL;
        return MiningHandAnimation.Style.PICK;
    }

    private static void onSwing(Minecraft mc, MiningSwingPayload swing) {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) return;
        ItemStack held = mc.player.getMainHandItem();
        if (!MiningOwnership.ownsItemAndMode(mc.gameMode.getPlayerMode(), held)) return;
        MiningHandAnimation.Mode m = ANIM.mode();
        if (m == MiningHandAnimation.Mode.POWER_CHARGE || m == MiningHandAnimation.Mode.POWER_STRIKE) return;
        animSource = held.copy();
        ANIM.startNormal(styleOf(held), swing.windUpTicks(), swing.recoveryTicks());
    }

    /** Anything that invalidates the current presentation eases it back to neutral (never a snap, never a leak). */
    private static boolean animationInvalid(Minecraft mc) {
        if (mc.player == null || mc.gameMode == null || mc.gui.screen() != null) return true;
        ItemStack held = mc.player.getMainHandItem();
        return !MiningOwnership.ownsItemAndMode(mc.gameMode.getPlayerMode(), held)
                || mc.player.isUsingItem() || !MiningSourceIdentity.same(animSource, held);
    }

    private static void tick(Minecraft mc) {
        ANIM.tick();
        updateIdle(mc);
        if (ANIM.isActive() && animationInvalid(mc)) {
            ANIM.cancel();
            if (meterActive) {                       // an in-progress power hold is void: tell the server, stay cancelled
                send(MiningIntentPayload.Action.POWER_CANCEL);
                meterActive = false;
                cancelledUntilRelease = true;
                cancelNanos = System.nanoTime();
            }
        }
        boolean down = mc.options.keyAttack.isDown() && mc.gui.screen() == null && survivalMining(mc);
        if (!down) {
            if (meterActive && !cancelledUntilRelease) {
                send(MiningIntentPayload.Action.POWER_RELEASE);
                releaseNanos = System.nanoTime();
                releaseForce = meterValue();
                if (mc.player != null) {
                    int duration = mc.player.getMainHandItem().getSwingAnimation().duration();
                    // Power timings are deterministic (cadence never applies), so the strike is predicted locally at once.
                    ANIM.releasePower(MiningTuning.windUpTicks(duration, true, 1f), MiningTuning.RECOVERY_TICKS);
                }
            }
            if (holdSent) send(MiningIntentPayload.Action.HOLD_STOP);
            reset();
            return;
        }

        // Swords/Shears are excluded from Power Mining: Alt does nothing special with them (ordinary hold instead).
        boolean alt = ModKeybinds.isPhysicallyDown(ModKeybinds.RADIAL_MODIFIER)
                && !MiningTier.excludedFromPowerMining(mc.player.getMainHandItem());
        boolean chord = alt && voiceChordDown();
        if (!prevDown) {
            pressedWithAlt = alt;
            // A click during the Alt+B dictation chord is not a mining input at all.
            cancelledUntilRelease = chord;
        }
        prevDown = true;
        if (cancelledUntilRelease) return;
        if (pressedWithAlt && chord) {        // Alt+B pressed during a charge: the chord wins, the charge is cancelled
            if (meterActive) {
                send(MiningIntentPayload.Action.POWER_CANCEL);
                ANIM.cancel();
                cancelNanos = System.nanoTime();
            }
            meterActive = false;
            cancelledUntilRelease = true;
            return;
        }

        boolean onBlock = ownsCrosshairBlock(mc);
        if (pressedWithAlt) {
            if (!alt) {                       // Alt released first: cancel, no swing
                if (meterActive) {
                    send(MiningIntentPayload.Action.POWER_CANCEL);
                    ANIM.cancel();
                    cancelNanos = System.nanoTime();
                }
                meterActive = false;
                cancelledUntilRelease = true;
                return;
            }
            if (!meterActive && onBlock) {
                meterActive = true;
                meterTicks = 0;
                send(MiningIntentPayload.Action.POWER_START);
                animSource = mc.player.getMainHandItem().copy();
                ANIM.startPowerCharge(styleOf(mc.player.getMainHandItem()));
            }
            if (meterActive) meterTicks++;
        } else if (!holdSent && onBlock) {
            send(MiningIntentPayload.Action.HOLD_START);
            holdSent = true;
        }
    }

    /** Presentation state for the idle reticle; changes no mining input. */
    private static void updateIdle(Minecraft mc) {
        boolean alt = mc.gui.screen() == null && ModKeybinds.isPhysicallyDown(ModKeybinds.RADIAL_MODIFIER);
        long now = System.nanoTime();
        if (!alt) altHeldSince = -1;
        else if (altHeldSince < 0) altHeldSince = now;
        idleArmed = alt && now - altHeldSince >= IDLE_DELAY_NANOS && !voiceChordDown() && !meterActive
                && !mc.options.keyAttack.isDown() && survivalMining(mc)
                && !MiningTier.excludedFromPowerMining(mc.player.getMainHandItem());
    }

    private static void send(MiningIntentPayload.Action action) {
        ClientPlayNetworking.send(new MiningIntentPayload(action));
    }

    private static void reset() {
        prevDown = false;
        pressedWithAlt = false;
        cancelledUntilRelease = false;
        holdSent = false;
        meterActive = false;
        meterTicks = 0;
    }
}
