package zcylas.totality.server.operator;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.level.GameType;
import org.jetbrains.annotations.Nullable;
import zcylas.totality.Totality;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.operator.OperatorResult;
import zcylas.totality.networking.operator.OperatorActionPayload;
import zcylas.totality.networking.operator.OperatorActionResultPayload;
import zcylas.totality.networking.operator.OperatorAuthorizationPayload;
import zcylas.totality.networking.operator.OperatorAuthorizationQueryPayload;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server authority for Operator Mode (voice API demonstration). The client is untrusted: it can only
 * name one {@link OperatorAction} by ordinal. For EVERY request the server
 * <ul>
 *   <li>refuses unknown actions, repeated or older request ids (at most one action per utterance,
 *       replays and stale requests do nothing) and requests closer together than
 *       {@value #MIN_INTERVAL_MILLIS} ms;</li>
 *   <li>re-checks, at that moment, that the player holds the server's own game-master command
 *       permission ({@link Permissions#COMMANDS_GAMEMASTER}, the permission {@code /gamemode} and
 *       {@code /weather} require), looked up live from the server's operator list — so a revoked
 *       operator is refused on the very next request; no client flag counts;</li>
 *   <li>performs the action on its own terms: the target is always the requesting player (game mode)
 *       or the server's weather; no command string is ever built or run;</li>
 *   <li>logs every decision (audit) and answers with the result.</li>
 * </ul>
 */
public final class OperatorModeServer {

    static final long MIN_INTERVAL_MILLIS = 500;

    private record Last(int requestId, long millis) {}

    private static final Map<UUID, Last> LAST = new ConcurrentHashMap<>();

    private OperatorModeServer() {}

    public static void register() {
        ServerPlayNetworking.registerGlobalReceiver(OperatorAuthorizationQueryPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    boolean ok = isOperator(player);
                    reply(player, new OperatorAuthorizationPayload(payload.requestId(), ok));
                }));
        ServerPlayNetworking.registerGlobalReceiver(OperatorActionPayload.TYPE, (payload, context) ->
                context.server().execute(() -> {
                    ServerPlayer player = context.player();
                    OperatorResult result = handle(player, payload.requestId(), payload.action(), System.currentTimeMillis());
                    reply(player, new OperatorActionResultPayload(payload.requestId(), result));
                }));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> LAST.remove(handler.getPlayer().getUUID()));
    }

    /** The live permission check (server operator list), never a client claim. */
    public static boolean isOperator(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    /** One request, decided and performed on the server thread. Package-visible for the verification suite. */
    static OperatorResult handle(ServerPlayer player, int requestId, @Nullable OperatorAction action, long nowMillis) {
        String who = player.getGameProfile().name();
        if (action == null) return audit(who, requestId, null, OperatorResult.REJECTED, "unknown action");
        Last last = LAST.get(player.getUUID());
        if (last != null && requestId <= last.requestId()) {
            return audit(who, requestId, action, OperatorResult.REJECTED, "repeated or stale request (last " + last.requestId() + ")");
        }
        if (last != null && nowMillis - last.millis() < MIN_INTERVAL_MILLIS) {
            LAST.put(player.getUUID(), new Last(requestId, last.millis()));
            return audit(who, requestId, action, OperatorResult.REJECTED, "too frequent");
        }
        LAST.put(player.getUUID(), new Last(requestId, nowMillis));
        if (!isOperator(player)) return audit(who, requestId, action, OperatorResult.DENIED, "not an operator");
        boolean changed = switch (action) {
            case CLEAR_RAIN -> clearWeather(player.level().getServer(), player.level());
            case SURVIVAL -> player.setGameMode(GameType.SURVIVAL);
            case CREATIVE -> player.setGameMode(GameType.CREATIVE);
        };
        return audit(who, requestId, action, changed ? OperatorResult.EXECUTED : OperatorResult.UNCHANGED, "operator");
    }

    /** Exactly {@code /weather clear} with its default duration; false when it was already clear. */
    private static boolean clearWeather(MinecraftServer server, ServerLevel level) {
        boolean wasWet = server.getWeatherData().isRaining() || server.getWeatherData().isThundering();
        server.setWeatherParameters(ServerLevel.RAIN_DELAY.sample(level.getRandom()), 0, false, false);
        return wasWet;
    }

    private static OperatorResult audit(String who, int requestId, @Nullable OperatorAction action, OperatorResult result, String why) {
        Totality.LOGGER.info("[Totality Operator Mode] {} request #{} {} -> {} ({})", who, requestId,
                action == null ? "<invalid>" : action, result, why);
        return result;
    }

    private static void reply(ServerPlayer player, net.minecraft.network.protocol.common.custom.CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    /** Verification suite: forget a (fake) player's request history. */
    static void forget(UUID player) {
        LAST.remove(player);
    }
}
