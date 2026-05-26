package zcylas.totality.init;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import zcylas.totality.api.core.movement.MovementMode;
import zcylas.totality.networking.movement.ClientMovementManager;
import zcylas.totality.networking.movement.ToggleFlightPayload;

public final class TotalityMovementHandler {

    // ── State ─────────────────────────────────────────────────────────────────
    private static boolean lastSpaceHeld  = false;
    private static boolean lastForwardHeld = false;
    private static int     spacePressCount = 0;
    private static long    lastSpacePressTime = 0L;

    private static final long DOUBLE_SPACE_WINDOW_MS = 400L;

    public static void register() {
        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player == null || client.screen != null) return;

            LocalPlayer player  = client.player;
            boolean spaceHeld   = client.options.keyJump.isDown();
            boolean forwardHeld = client.options.keyUp.isDown();
            boolean powerHeld   = isMovementKeyHeld();

            // Rising edges
            boolean spacePressed   = spaceHeld   && !lastSpaceHeld;
            boolean forwardPressed = forwardHeld  && !lastForwardHeld;

            // ── Power key required for everything ─────────────────────────────
            if (!powerHeld) {
                lastSpaceHeld   = spaceHeld;
                lastForwardHeld = forwardHeld;
                return;
            }

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

        Vec3 look           = player.getLookAngle();
        Vec3 horizontalLook = new Vec3(look.x, 0.0D, look.z);
        if (horizontalLook.lengthSqr() <= 1.0E-6D) return;

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

        Vec3 look       = player.getLookAngle();
        Vec3 horizontal = new Vec3(look.x, 0.0D, look.z);
        if (horizontal.lengthSqr() <= 1.0E-6D) return;

        Vec3 forward = horizontal.normalize();
        player.setDeltaMovement(
                forward.x * 2.2D,
                0.9D,
                forward.z * 2.2D
        );
    }

    // ── Keybind ───────────────────────────────────────────────────────────────

    private static boolean isMovementKeyHeld() {
        return ModKeybinds.MOVEMENT_POWER.isDown();
    }

    private TotalityMovementHandler() {}
}