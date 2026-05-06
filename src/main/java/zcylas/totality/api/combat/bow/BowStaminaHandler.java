package zcylas.totality.api.combat.bow;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BowItem;
import zcylas.totality.api.stamina.PlayerStaminaManager;
import zcylas.totality.networking.stamina.StaminaServerTick;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class BowStaminaHandler {

    // Tracks players who are currently drawing a bow
    // Used to detect when drawing starts (for crossbow load cost)
    private static final Set<UUID> drawingPlayers = new HashSet<>();

    /**
     * Called every tick from StaminaServerTick for each player.
     * Handles bow stamina drain and draw cancellation.
     *
     * @param tickCounter the current tick counter (for per-second drain timing)
     */
    public static void tick(ServerPlayer player, int tickCounter) {
        ItemStack using = player.getUseItem();

        if (using.isEmpty()) {
            drawingPlayers.remove(player.getUUID());
            return;
        }

        // ── Vanilla/custom Bow ──
        if (using.getItem() instanceof BowItem || isTotalityBow(using)) {
            handleBowDraw(player, using, tickCounter);
            return;
        }

        // ── Vanilla/custom Crossbow ──
        if (using.getItem() instanceof CrossbowItem || isTotalityCrossbow(using)) {
            handleCrossbowLoad(player, using);
            return;
        }

        // Not a bow — remove from drawing set
        drawingPlayers.remove(player.getUUID());
    }

    private static void handleBowDraw(ServerPlayer player, ItemStack stack, int tickCounter) {
        // Can't draw at 0 stamina — cancel immediately
        if (!PlayerStaminaManager.hasStamina(player, 1)) {
            player.stopUsingItem();
            drawingPlayers.remove(player.getUUID());
            return;
        }

        // Halt stamina regen while drawing — handled in StaminaServerTick
        // by checking isBowDrawn() before regen

        // Drain stamina every 20 ticks (1 second) while drawn
        if (tickCounter % 20 == 0) {
            int costPerSecond = getCostPerSecond(stack);
            PlayerStaminaManager.removeStamina(player, costPerSecond);
            StaminaServerTick.syncStamina(player);

            // If stamina just hit 0, cancel the draw
            if (!PlayerStaminaManager.hasStamina(player, 1)) {
                player.stopUsingItem();
                drawingPlayers.remove(player.getUUID());
            }
        }

        drawingPlayers.add(player.getUUID());
    }

    private static void handleCrossbowLoad(ServerPlayer player, ItemStack stack) {
        UUID uuid = player.getUUID();

        if (!drawingPlayers.contains(uuid)) {
            // First tick of loading — apply one-time stamina cost
            if (!PlayerStaminaManager.hasStamina(player, 1)) {
                // Can't load at 0 stamina — cancel
                player.stopUsingItem();
                return;
            }

            int loadCost = getLoadCost(stack);
            PlayerStaminaManager.removeStamina(player, loadCost);
            StaminaServerTick.syncStamina(player);
            drawingPlayers.add(uuid);
        }
        // No further drain while holding loaded crossbow
    }

    /**
     * Returns true if the player is currently drawing a bow.
     * Used by StaminaServerTick to halt stamina regen while drawing.
     */
    public static boolean isBowDrawn(ServerPlayer player) {
        return drawingPlayers.contains(player.getUUID());
    }

    /**
     * Clean up when a player leaves.
     */
    public static void onPlayerLeave(ServerPlayer player) {
        drawingPlayers.remove(player.getUUID());
    }

    // ── Helpers ──

    private static boolean isTotalityBow(ItemStack stack) {
        return stack.getItem() instanceof TotalityBowItem bow
                && bow.getBowType() == BowType.BOW;
    }

    private static boolean isTotalityCrossbow(ItemStack stack) {
        return stack.getItem() instanceof TotalityBowItem bow
                && bow.getBowType() == BowType.CROSSBOW;
    }

    private static int getCostPerSecond(ItemStack stack) {
        if (stack.getItem() instanceof TotalityBowItem bow) {
            return bow.getDrawStaminaCostPerSecond();
        }
        return BowType.BOW.drawCostPerSecond(); // vanilla bow default
    }

    private static int getLoadCost(ItemStack stack) {
        if (stack.getItem() instanceof TotalityBowItem bow) {
            return bow.getLoadStaminaCost();
        }
        return BowType.CROSSBOW.loadCost(); // vanilla crossbow default
    }
}