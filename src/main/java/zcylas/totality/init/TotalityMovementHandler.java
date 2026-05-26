package zcylas.totality.init;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.api.core.movement.MovementStaminaCosts;
import zcylas.totality.networking.movement.ClientMovementManager;
import zcylas.totality.networking.movement.MovementStaminaPayload;
import zcylas.totality.networking.movement.PowerSprintStatePayload;
import zcylas.totality.networking.movement.ToggleFlightPayload;
import zcylas.totality.networking.stamina.ClientStaminaManager;

public final class TotalityMovementHandler {

    // ── State ─────────────────────────────────────────────────────────────────
    private static boolean lastSpaceHeld  = false;
    private static boolean lastForwardHeld = false;
    private static int     spacePressCount = 0;
    private static long    lastSpacePressTime = 0L;
    private static int powerSprintPacketCooldown = 0;
    private static boolean lastPowerSprinting = false;

    private static final long DOUBLE_SPACE_WINDOW_MS = 400L;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null) {
                lastPowerSprinting = false;
                return;
            }

            if (client.screen != null) {
                updatePowerSprintState(false);
                return;
            }

            LocalPlayer player  = client.player;
            boolean spaceHeld   = client.options.keyJump.isDown();
            boolean forwardHeld = client.options.keyUp.isDown();
            boolean powerHeld   = isMovementKeyHeld();

            if (powerSprintPacketCooldown > 0) {
                powerSprintPacketCooldown--;
            }

            // Rising edges
            boolean spacePressed   = spaceHeld   && !lastSpaceHeld;
            boolean forwardPressed = forwardHeld  && !lastForwardHeld;

            // ── Auto-disable flight after landing ─────────────────────────────
            if (ClientMovementManager.isActivelyFlying()) {
                boolean jumpHeld = client.options.keyJump.isDown();

                if (player.onGround() && !jumpHeld) {
                    ClientPlayNetworking.send(new ToggleFlightPayload(false));

                    lastSpaceHeld = spaceHeld;
                    lastForwardHeld = forwardHeld;
                    return;
                }
            }

            // ── Power key required for everything ─────────────────────────────
            if (!powerHeld) {
                updatePowerSprintState(false);
                lastSpaceHeld   = spaceHeld;
                lastForwardHeld = forwardHeld;
                return;
            }

            boolean powerSprintingNow = player.isSprinting()
                    && !ClientMovementManager.isActivelyFlying()
                    && ClientMovementManager.hasMode(MovementMode.POWER_SPRINT)
                    && ClientStaminaManager.getStamina() > 0;

            updatePowerSprintState(powerSprintingNow);


            // ── Flight toggle — ` + double space ──────────────────────────────
            handleFlightToggle(player, spacePressed);

            // ── Power sprint — ` + sprint (continuous) ────────────────────────
            handlePowerSprint(player);

            // ── Super leap — ` held + tap W, not sprinting ────────────────────
            handleSuperLeap(player, forwardPressed);

            lastSpaceHeld   = spaceHeld;
            lastForwardHeld = forwardHeld;

        });
    }

    // ── Flight toggle ─────────────────────────────────────────────────────────

    private static void handleFlightToggle(LocalPlayer player, boolean spacePressed) {
        if (!ClientMovementManager.hasMode(MovementMode.FLIGHT)) return;

        if (spacePressed) {
            long now = System.currentTimeMillis();
            if (now - lastSpacePressTime < DOUBLE_SPACE_WINDOW_MS) {
                spacePressCount++;
                if (spacePressCount >= 2) {
                    boolean newState = !ClientMovementManager.isActivelyFlying();
                    ClientPlayNetworking.send(new ToggleFlightPayload(newState));
                    spacePressCount = 0;
                }
            } else {
                spacePressCount = 1;
            }
            lastSpacePressTime = now;
        }
    }

    // ── Power sprint ──────────────────────────────────────────────────────────

    private static void handlePowerSprint(LocalPlayer player) {
        if (!ClientMovementManager.hasMode(MovementMode.POWER_SPRINT)) return;
        if (!player.isSprinting()) return;
        if (ClientMovementManager.isActivelyFlying()) return;
        if (ClientStaminaManager.getStamina() <= 0) return;

        Vec3 look           = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() <= 1.0E-6D) return;

        if (powerSprintPacketCooldown <= 0) {
            ClientPlayNetworking.send(new MovementStaminaPayload(MovementMode.POWER_SPRINT));
            powerSprintPacketCooldown = MovementStaminaCosts.POWER_SPRINT_DRAIN_INTERVAL_TICKS;
        }

        Vec3 horizontal     = horizontalLook.normalize();
        Vec3 current        = player.getDeltaMovement();
        double targetSpeed  = 1.35D;
        double acceleration = 0.25D;
        Vec3 target         = horizontal.scale(targetSpeed);

        player.setDeltaMovement(
                current.x + (target.x - current.x) * acceleration,
                current.y,
                current.z + (target.z - current.z) * acceleration
        );
    }

    // ── Super leap ────────────────────────────────────────────────────────────

    private static void handleSuperLeap(LocalPlayer player, boolean forwardPressed) {
        if (!ClientMovementManager.hasMode(MovementMode.SUPER_LEAP)) return;
        if (!forwardPressed) return;
        if (!player.onGround()) return;
        if (player.isSprinting()) return;
        if (ClientMovementManager.isActivelyFlying()) return;
        if (ClientStaminaManager.getStamina() < MovementStaminaCosts.SUPER_LEAP_COST) return;

        Vec3 look       = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) return;

        ClientPlayNetworking.send(new MovementStaminaPayload(MovementMode.SUPER_LEAP));

        Vec3 forward = horizontal.normalize();
        player.setDeltaMovement(
                forward.x * 2.2D,
                0.9D,
                forward.z * 2.2D
        );
    }

    private static void updatePowerSprintState(boolean active) {
        if (active == lastPowerSprinting) return;

        ClientPlayNetworking.send(new PowerSprintStatePayload(active));
        lastPowerSprinting = active;
    }
    // ── Keybind ───────────────────────────────────────────────────────────────

    private static boolean isMovementKeyHeld() {
        return ModKeybinds.MOVEMENT_POWER.isDown();
    }

    private TotalityMovementHandler() {}
}