package zcylas.totality.api.combat.exhaustion;

import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.api.stamina.PlayerStaminaManager;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ExhaustionManager {

    private static final Map<UUID, ExhaustionState> previousStates = new HashMap<>();
    private static final Set<UUID> penalizedPlayers = new HashSet<>();

    public static void tick(ServerPlayer player) {
        int stamina    = PlayerStaminaManager.getStamina(player);
        int maxStamina = PlayerStaminaManager.getMaxStamina(player);

        ExhaustionState current  = ExhaustionState.fromStamina(stamina, maxStamina);
        ExhaustionState previous = previousStates.getOrDefault(
                player.getUUID(), ExhaustionState.NORMAL);

        if (current != previous) {
            switch (current) {
                case NORMAL    -> onRecovered(player, previous);
                case WARNING   -> onWarning(player, previous);
                case EXHAUSTED -> onExhausted(player);
            }
            previousStates.put(player.getUUID(), current);
        }
    }

    private static void onWarning(ServerPlayer player, ExhaustionState previous) {
        if (previous == ExhaustionState.NORMAL) {
            player.level().playSound(
                    null,
                    player.blockPosition(),
                    net.minecraft.sounds.SoundEvents.PLAYER_BREATH,
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0f, 1.0f
            );
        }
    }

    private static void onExhausted(ServerPlayer player) {
        penalizedPlayers.add(player.getUUID());
        SendNotificationPayload.send(player,
                "⚠ You are exhausted!",
                SendNotificationPayload.RED);
    }

    private static void onRecovered(ServerPlayer player, ExhaustionState previous) {
        penalizedPlayers.remove(player.getUUID());
        if (previous == ExhaustionState.EXHAUSTED || previous == ExhaustionState.WARNING) {
            SendNotificationPayload.send(player,
                    "✔ You have recovered.",
                    SendNotificationPayload.GREEN);
        }
    }

    public static boolean isExhausted(ServerPlayer player) {
        return previousStates.getOrDefault(player.getUUID(), ExhaustionState.NORMAL)
                == ExhaustionState.EXHAUSTED;
    }

    public static boolean isWarning(ServerPlayer player) {
        ExhaustionState state = previousStates.getOrDefault(
                player.getUUID(), ExhaustionState.NORMAL);
        return state == ExhaustionState.WARNING || state == ExhaustionState.EXHAUSTED;
    }

    public static boolean isPenalized(ServerPlayer player) {
        return penalizedPlayers.contains(player.getUUID());
    }

    public static float getRegenMultiplier(ServerPlayer player) {
        if (isPenalized(player)) return 0.5f;
        ExhaustionState state = previousStates.getOrDefault(
                player.getUUID(), ExhaustionState.NORMAL);
        return state == ExhaustionState.WARNING ? 0.75f : 1.0f;
    }

    public static void onPlayerLeave(ServerPlayer player) {
        previousStates.remove(player.getUUID());
        penalizedPlayers.remove(player.getUUID());
    }
}