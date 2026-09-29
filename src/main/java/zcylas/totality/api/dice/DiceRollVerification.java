package zcylas.totality.api.dice;

import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import zcylas.totality.Totality;
import zcylas.totality.api.core.util.ServerScheduler;
import zcylas.totality.api.core.util.VerificationReporter;
import zcylas.totality.networking.dice.DiceCheckRequestPayload;
import zcylas.totality.networking.dice.DiceRollResultPayload;
import zcylas.totality.server.TotalityFakePlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Dev-environment-gated, opt-in (live-world) self-test of the server side of the player-facing dice checks, which
 * Dice Roll UI V2 (Creative Test J) only visualises: a pending check resolves once (a repeated or foreign click, an
 * unknown id or a cancelled check never rolls or fires its callback again), the authoritative roll follows the
 * existing rules (normal / advantage / disadvantage, modifiers, DC, natural 20 and natural 1), and both payloads
 * carry every field through the real codecs with the server's registry access.
 */
public final class DiceRollVerification {

    private static final int SAMPLES = 20_000;

    private DiceRollVerification() {}

    public static void register() {
        if (!VerificationReporter.liveWorldVerificationEnabled()) return;
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            if (!VerificationReporter.isDevEnvironment()) return;
            ServerScheduler.getInstance().queue(DiceRollVerification::run, 20);
        });
    }

    private static void run(MinecraftServer server) {
        VerificationReporter r = new VerificationReporter(Totality.LOGGER, "DiceRollVerification");
        ServerLevel level = server.overworld();
        ServerPlayer a = TotalityFakePlayer.create(level, "DiceRollerA");
        ServerPlayer b = TotalityFakePlayer.create(level, "DiceRollerB");
        try {
            pending(r, a, b);
            rules(r, a);
            codecs(r, server, a);
        } finally {
            PendingDiceRollManager.cancelAll(a);
            PendingDiceRollManager.cancelAll(b);
            a.discard();
            b.discard();
            r.summarize();
        }
    }

    private static DiceRollContext context(RollType type, int dc, int... bonuses) {
        List<DiceBonus> list = new ArrayList<>();
        for (int i = 0; i < bonuses.length; i++) list.add(new DiceBonus("Bonus " + i, bonuses[i]));
        return new DiceRollContext("Persuasion", "Charisma Check", Dice.D20, dc, type, list);
    }

    private static void pending(VerificationReporter r, ServerPlayer a, ServerPlayer b) {
        List<DiceRollResult> fired = new ArrayList<>();
        DiceRollContext ctx = context(RollType.NORMAL, 12, 3, 2);
        UUID id = PendingDiceRollManager.request(a, ctx, fired::add);
        check(r, "a requested check waits for the player's click (no roll, no callback yet)", () -> fired.isEmpty());
        PendingDiceRollManager.resolve(a, UUID.randomUUID());
        check(r, "an unknown session id resolves nothing", () -> fired.isEmpty());
        PendingDiceRollManager.resolve(b, id);
        check(r, "another player's click cannot resolve this player's check", () -> fired.isEmpty());
        PendingDiceRollManager.resolve(a, id);
        check(r, "the player's click rolls once and fires the callback once, with this context", () ->
                fired.size() == 1 && fired.getFirst().context() == ctx);
        for (int i = 0; i < 5; i++) PendingDiceRollManager.resolve(a, id);
        check(r, "repeated clicks (skip, replay, duplicate packets) never roll again or repeat the callback", () -> fired.size() == 1);

        List<DiceRollResult> second = new ArrayList<>();
        UUID first = PendingDiceRollManager.request(a, ctx, second::add);
        UUID other = PendingDiceRollManager.request(a, context(RollType.ADVANTAGE, 10), second::add);
        PendingDiceRollManager.resolve(a, other);
        PendingDiceRollManager.resolve(a, first);
        check(r, "two pending checks resolve independently, each once, each with its own context", () ->
                second.size() == 2 && second.get(0).context().rollType() == RollType.ADVANTAGE
                        && second.get(1).context().rollType() == RollType.NORMAL && !first.equals(other));

        List<DiceRollResult> cancelled = new ArrayList<>();
        UUID gone = PendingDiceRollManager.request(a, ctx, cancelled::add);
        PendingDiceRollManager.cancelAll(a);
        PendingDiceRollManager.resolve(a, gone);
        check(r, "a cancelled check (the disconnect path) never rolls or fires its callback", () -> cancelled.isEmpty());
    }

    private static void rules(VerificationReporter r, ServerPlayer p) {
        for (RollType type : RollType.values()) {
            DiceRollContext ctx = context(type, 14, 4, -1);
            int[] seen = new int[21];
            long sum = 0;
            boolean consistent = true;
            String bad = "";
            for (int i = 0; i < SAMPLES; i++) {
                DiceRollResult res = DiceRoller.roll(p, ctx);
                int used = switch (type) {
                    case NORMAL -> res.roll1();
                    case ADVANTAGE -> Math.max(res.roll1(), res.roll2());
                    case DISADVANTAGE -> Math.min(res.roll1(), res.roll2());
                };
                RollOutcome expected = used == 20 ? RollOutcome.CRITICAL_SUCCESS : used == 1 ? RollOutcome.CRITICAL_FAILURE
                        : used + 3 >= 14 ? RollOutcome.SUCCESS : RollOutcome.FAILURE;
                boolean ok = res.usedRoll() == used && res.totalBonus() == 3 && res.total() == used + 3 && res.outcome() == expected
                        && res.roll1() >= 1 && res.roll1() <= 20
                        && (type == RollType.NORMAL ? res.roll2() == -1 && !res.hasSecondDie() : res.roll2() >= 1 && res.roll2() <= 20);
                if (!ok && consistent) bad = res.toString();
                consistent &= ok;
                seen[used]++;
                sum += used;
            }
            double mean = sum / (double) SAMPLES;
            double expectedMean = switch (type) {
                case NORMAL -> 10.5;
                case ADVANTAGE -> 13.825;
                case DISADVANTAGE -> 7.175;
            };
            boolean all = true;
            for (int v = 1; v <= 20; v++) all &= seen[v] > 0;
            boolean fits = consistent && all && Math.abs(mean - expectedMean) < 0.25;
            String detail = bad;
            check(r, type + ": " + SAMPLES + " real DiceRoller rolls — kept die (" + (type == RollType.NORMAL ? "the one die"
                    : type == RollType.ADVANTAGE ? "higher" : "lower") + "), total = kept + modifiers, nat 20 critical success, nat 1 critical "
                    + "failure, else total vs DC; mean kept " + String.format("%.3f", mean) + " (expected " + expectedMean + "), every value 1-20 seen "
                    + (detail.isEmpty() ? "" : "FIRST BAD " + detail), () -> fits);
        }
    }

    private static void codecs(VerificationReporter r, MinecraftServer server, ServerPlayer p) {
        DiceRollContext ctx = new DiceRollContext("Ancient Curse", "WIS Saving Throw", Dice.D20, 15, RollType.DISADVANTAGE,
                List.of(new DiceBonus("WIS Modifier", 2, "✦"), new DiceBonus("Save Proficiency", 3), new DiceBonus("Bane", -2)));
        UUID id = UUID.randomUUID();
        DiceCheckRequestPayload request = new DiceCheckRequestPayload(id, ctx);
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        DiceCheckRequestPayload.STREAM_CODEC.encode(buf, request);
        DiceCheckRequestPayload requestBack = DiceCheckRequestPayload.STREAM_CODEC.decode(buf);
        check(r, "the request payload carries the session, check name, subtype, die, DC, roll type and every modifier", () ->
                requestBack.equals(request) && buf.readableBytes() == 0);
        DiceRollResult res = DiceRoller.roll(p, ctx);
        DiceRollResultPayload result = new DiceRollResultPayload(id, res);
        RegistryFriendlyByteBuf buf2 = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        DiceRollResultPayload.STREAM_CODEC.encode(buf2, result);
        DiceRollResultPayload resultBack = DiceRollResultPayload.STREAM_CODEC.decode(buf2);
        check(r, "the result payload carries both natural rolls, the kept roll, the modifiers, the total and the outcome", () ->
                resultBack.equals(result) && buf2.readableBytes() == 0 && resultBack.result().roll2() == res.roll2()
                        && resultBack.result().outcome() == res.outcome());
    }

    private static void check(VerificationReporter r, String label, Supplier<Boolean> body) {
        try {
            r.check(label, body.get(), "");
        } catch (RuntimeException e) {
            r.check(label, false, "threw " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
