package zcylas.totality.server.operator;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.api.operator.OperatorAction;
import zcylas.totality.api.operator.OperatorResult;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of Operator Mode's server authority: drives the
 * REAL {@link OperatorModeServer#handle} (the method the action payload's receiver calls) with fake
 * players against the REAL operator list of the running server — on {@code runVerificationServer} a
 * dedicated server, so nothing here relies on single-player owner rules. Covers: non-operators denied,
 * operators executed, permission revoked between requests, repeated/stale/too-frequent requests,
 * unknown actions, and that one player's request never changes another player.
 */
public final class OperatorModeVerification {

    private OperatorModeVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(OperatorModeVerification::run);
    }

    private static void run(MinecraftServer server) {
        if (!VerificationReporter.isDevEnvironment()) return;
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "OperatorModeVerification");
        r.check("running on a " + (server.isDedicatedServer() ? "dedicated" : "integrated") + " server", true, "");
        ServerPlayer a = TotalityFakePlayer.create(server.overworld(), "OpModeA");
        ServerPlayer b = TotalityFakePlayer.create(server.overworld(), "OpModeB");
        long t = 1_000_000;
        try {
            a.setGameMode(GameType.SURVIVAL);
            b.setGameMode(GameType.SURVIVAL);

            long t0 = t;
            check(r, "non-operator: Creative denied, game mode unchanged", () ->
                    OperatorModeServer.handle(a, 1, OperatorAction.CREATIVE, t0) == OperatorResult.DENIED
                            && a.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);
            long t1 = t += 1000;
            check(r, "non-operator: Survival denied", () -> OperatorModeServer.handle(a, 2, OperatorAction.SURVIVAL, t1) == OperatorResult.DENIED);
            server.setWeatherParameters(0, 6000, true, false);
            long t2 = t += 1000;
            check(r, "non-operator: Clear Rain denied, still raining", () ->
                    OperatorModeServer.handle(a, 3, OperatorAction.CLEAR_RAIN, t2) == OperatorResult.DENIED
                            && server.getWeatherData().isRaining());

            server.getPlayerList().op(a.nameAndId());
            long t3 = t += 1000;
            check(r, "operator: Creative executed on the requesting player only", () ->
                    OperatorModeServer.handle(a, 4, OperatorAction.CREATIVE, t3) == OperatorResult.EXECUTED
                            && a.gameMode.getGameModeForPlayer() == GameType.CREATIVE && b.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);
            long t4 = t += 1000;
            check(r, "operator: Creative again is UNCHANGED", () ->
                    OperatorModeServer.handle(a, 5, OperatorAction.CREATIVE, t4) == OperatorResult.UNCHANGED);
            long t5 = t += 1000;
            check(r, "operator: Clear Rain executed (weather cleared)", () ->
                    OperatorModeServer.handle(a, 6, OperatorAction.CLEAR_RAIN, t5) == OperatorResult.EXECUTED
                            && !server.getWeatherData().isRaining() && !server.getWeatherData().isThundering());
            long t6 = t += 1000;
            check(r, "repeated request id is rejected and changes nothing", () ->
                    OperatorModeServer.handle(a, 6, OperatorAction.SURVIVAL, t6) == OperatorResult.REJECTED
                            && a.gameMode.getGameModeForPlayer() == GameType.CREATIVE);
            long t7 = t += 1000;
            check(r, "older (stale) request id is rejected", () ->
                    OperatorModeServer.handle(a, 2, OperatorAction.SURVIVAL, t7) == OperatorResult.REJECTED
                            && a.gameMode.getGameModeForPlayer() == GameType.CREATIVE);
            long t8 = t += 1000;
            check(r, "unknown action (bad ordinal) is rejected", () ->
                    OperatorModeServer.handle(a, 7, OperatorAction.byOrdinal(99), t8) == OperatorResult.REJECTED);
            long t9 = t += 1000;
            check(r, "operator: Survival executed", () ->
                    OperatorModeServer.handle(a, 8, OperatorAction.SURVIVAL, t9) == OperatorResult.EXECUTED
                            && a.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);
            long t10 = t + 100;
            check(r, "a second request within " + OperatorModeServer.MIN_INTERVAL_MILLIS + " ms is rejected", () ->
                    OperatorModeServer.handle(a, 9, OperatorAction.CREATIVE, t10) == OperatorResult.REJECTED
                            && a.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);

            server.getPlayerList().deop(a.nameAndId());
            long t11 = t += 1000;
            check(r, "permission revoked: the very next request is denied", () ->
                    OperatorModeServer.handle(a, 10, OperatorAction.CREATIVE, t11) == OperatorResult.DENIED
                            && a.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && !OperatorModeServer.isOperator(a));
            long t12 = t += 1000;
            check(r, "another non-operator cannot use it either", () ->
                    OperatorModeServer.handle(b, 1, OperatorAction.CREATIVE, t12) == OperatorResult.DENIED
                            && b.gameMode.getGameModeForPlayer() == GameType.SURVIVAL);
        } finally {
            server.getPlayerList().deop(a.nameAndId());
            OperatorModeServer.forget(a.getUUID());
            OperatorModeServer.forget(b.getUUID());
            a.discard();
            b.discard();
            server.setWeatherParameters(RESTORE_CLEAR_TICKS, 0, false, false);
        }
        r.summarize();
    }

    /** Clear-weather duration used when restoring the disposable world after the suite. */
    private static final int RESTORE_CLEAR_TICKS = 12000;

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
