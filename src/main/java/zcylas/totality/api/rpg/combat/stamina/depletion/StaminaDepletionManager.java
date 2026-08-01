package zcylas.totality.api.rpg.combat.stamina.depletion;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import zcylas.totality.api.rpg.stamina.PlayerStaminaManager;
import zcylas.totality.networking.notification.SendNotificationPayload;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Immediate, Stamina-derived exertion/depletion lifecycle — breathing feedback at WINDED,
 * transient movement/attack penalties and a stronger regen penalty at DEPLETED. This is
 * deliberately NOT Totality's future D&D-style persistent, multi-source Exhaustion condition;
 * that remains a separate, unimplemented system reserved for Fatigue/Rest/starvation/curses/etc.
 *
 * State is recomputed every server tick from current/max Stamina and kept only in this
 * in-memory, UUID-keyed map — never persisted, never synchronized, never a Resource/component.
 */
public class StaminaDepletionManager {

    /** What the caller should do as a result of advancing one player's state by one tick. */
    public enum TransitionEvent {
        NONE,
        ENTERED_WINDED,
        ENTERED_DEPLETED,
        RECOVERED_TO_NORMAL
    }

    private static final Map<UUID, StaminaDepletionState> previousStates = new HashMap<>();
    private static final Set<UUID> depletedPlayers = new HashSet<>();

    /**
     * Call on player join to seed the initial state without triggering transition messages.
     * Without this, a player joining at zero Stamina would receive a "You are out of stamina!"
     * notification on the very first tick.
     */
    public static void onPlayerJoin(ServerPlayer player) {
        seed(player.getUUID(),
                PlayerStaminaManager.getStamina(player),
                PlayerStaminaManager.getMaxStamina(player));
    }

    public static void tick(ServerPlayer player) {
        TransitionEvent event = advance(player.getUUID(),
                PlayerStaminaManager.getStamina(player),
                PlayerStaminaManager.getMaxStamina(player));

        switch (event) {
            case ENTERED_WINDED -> player.level().playSound(
                    null,
                    player.blockPosition(),
                    SoundEvents.PLAYER_BREATH,
                    SoundSource.PLAYERS,
                    1.0f, 1.0f
            );
            case ENTERED_DEPLETED -> SendNotificationPayload.send(player,
                    "You are out of stamina!",
                    SendNotificationPayload.RED);
            case RECOVERED_TO_NORMAL -> SendNotificationPayload.send(player,
                    "Your stamina has recovered.",
                    SendNotificationPayload.GREEN);
            case NONE -> { }
        }
    }

    public static boolean isDepleted(ServerPlayer player) {
        return previousStates.getOrDefault(player.getUUID(), StaminaDepletionState.NORMAL)
                == StaminaDepletionState.DEPLETED;
    }

    public static boolean isWinded(ServerPlayer player) {
        StaminaDepletionState state = previousStates.getOrDefault(
                player.getUUID(), StaminaDepletionState.NORMAL);
        return state == StaminaDepletionState.WINDED || state == StaminaDepletionState.DEPLETED;
    }

    /** Whether the transient movement/attack penalties are currently active for this player. */
    public static boolean isPenalized(ServerPlayer player) {
        return isPenalized(player.getUUID());
    }

    public static float getRegenMultiplier(ServerPlayer player) {
        return regenMultiplier(player.getUUID());
    }

    public static void onPlayerLeave(ServerPlayer player) {
        previousStates.remove(player.getUUID());
        depletedPlayers.remove(player.getUUID());
    }

    // ── UUID-keyed core ───────────────────────────────────────────────────────
    // The ServerPlayer-taking public API above only ever needs the player's UUID plus its
    // current/max Stamina, so the actual bookkeeping/transition-decision logic is UUID-keyed —
    // this lets the transition sequencing be exercised directly in tests without constructing a
    // live ServerPlayer (which requires a bootstrapped Minecraft registry).

    private static void seed(UUID playerId, int stamina, int maxStamina) {
        StaminaDepletionState initial = StaminaDepletionState.fromStamina(stamina, maxStamina);
        previousStates.put(playerId, initial);
        if (initial == StaminaDepletionState.DEPLETED) {
            depletedPlayers.add(playerId);
        }
    }

    private static TransitionEvent advance(UUID playerId, int stamina, int maxStamina) {
        StaminaDepletionState current = StaminaDepletionState.fromStamina(stamina, maxStamina);
        StaminaDepletionState previous = previousStates.getOrDefault(playerId, StaminaDepletionState.NORMAL);

        if (current == previous) {
            return TransitionEvent.NONE;
        }

        TransitionEvent event;
        switch (current) {
            case NORMAL -> {
                boolean wasRecovering = previous == StaminaDepletionState.WINDED
                        || previous == StaminaDepletionState.DEPLETED;
                depletedPlayers.remove(playerId);
                event = wasRecovering ? TransitionEvent.RECOVERED_TO_NORMAL : TransitionEvent.NONE;
            }
            case WINDED -> event = previous == StaminaDepletionState.NORMAL
                    ? TransitionEvent.ENTERED_WINDED
                    : TransitionEvent.NONE;
            case DEPLETED -> {
                depletedPlayers.add(playerId);
                event = TransitionEvent.ENTERED_DEPLETED;
            }
            default -> event = TransitionEvent.NONE;
        }
        previousStates.put(playerId, current);
        return event;
    }

    private static boolean isPenalized(UUID playerId) {
        return depletedPlayers.contains(playerId);
    }

    private static float regenMultiplier(UUID playerId) {
        if (isPenalized(playerId)) return 0.5f;
        StaminaDepletionState state = previousStates.getOrDefault(playerId, StaminaDepletionState.NORMAL);
        return state == StaminaDepletionState.WINDED ? 0.75f : 1.0f;
    }

    private StaminaDepletionManager() {}

    // ── Test-only hooks ──────────────────────────────────────────────────────
    // UUID-keyed, mirroring the ServerPlayer-taking public API exactly — lets the transition/
    // penalty-bookkeeping logic be exercised under plain JUnit, without a live ServerPlayer.

    static void seedForTest(UUID playerId, int stamina, int maxStamina) {
        seed(playerId, stamina, maxStamina);
    }

    static TransitionEvent advanceForTest(UUID playerId, int stamina, int maxStamina) {
        return advance(playerId, stamina, maxStamina);
    }

    static boolean isPenalizedForTest(UUID playerId) {
        return isPenalized(playerId);
    }

    static float regenMultiplierForTest(UUID playerId) {
        return regenMultiplier(playerId);
    }

    static StaminaDepletionState previousStateForTest(UUID playerId) {
        return previousStates.getOrDefault(playerId, StaminaDepletionState.NORMAL);
    }

    static void clearPlayerForTest(UUID playerId) {
        previousStates.remove(playerId);
        depletedPlayers.remove(playerId);
    }

    static void clearForTest() {
        previousStates.clear();
        depletedPlayers.clear();
    }
}
