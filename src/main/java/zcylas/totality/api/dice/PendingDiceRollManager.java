// api/dice/PendingDiceRollManager.java
package zcylas.totality.api.dice;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.networking.dice.DiceCheckRequestPayload;
import zcylas.totality.networking.dice.DiceRollResultPayload;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Tracks pending dice rolls per player.
 * Call {@link #request} to show the dice roll screen on a player's client.
 * The server waits for the player to click, then rolls and fires the callback.
 *
 * Usage:
 *   PendingDiceRollManager.request(player, context, result -> {
 *       if (result.outcome().isSuccess()) grantDialogueOption(player);
 *   });
 */
public final class PendingDiceRollManager {

    // playerId → (rollId → pending roll)
    private static final Map<UUID, Map<UUID, PendingRoll>> PENDING = new HashMap<>();

    private PendingDiceRollManager() {}

    /**
     * Open the dice roll screen on the client and register a callback.
     *
     * @param player   the player who needs to roll
     * @param context  what they are rolling for
     * @param callback fired server-side once the player clicks and the roll resolves
     * @return         the session UUID (rarely needed externally)
     */
    public static UUID request(ServerPlayer player, DiceRollContext context,
                               Consumer<DiceRollResult> callback) {
        UUID rollId = UUID.randomUUID();
        PENDING.computeIfAbsent(player.getUUID(), k -> new HashMap<>())
                .put(rollId, new PendingRoll(context, callback));
        ServerPlayNetworking.send(player, new DiceCheckRequestPayload(rollId, context));
        return rollId;
    }

    /**
     * Called when the player clicks "Roll Dice" on their screen.
     * Rolls the dice, fires the callback, sends the result back to client.
     */
    public static void resolve(ServerPlayer player, UUID rollId) {
        Map<UUID, PendingRoll> map = PENDING.get(player.getUUID());
        if (map == null) return;
        PendingRoll pending = map.remove(rollId);
        if (pending == null) return;

        DiceRollResult result = DiceRoller.roll(player, pending.context());
        pending.callback().accept(result);
        ServerPlayNetworking.send(player, new DiceRollResultPayload(rollId, result));
    }

    /** Cancel all pending rolls for a player (e.g. on disconnect). */
    public static void cancelAll(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private record PendingRoll(DiceRollContext context, Consumer<DiceRollResult> callback) {}
}