package zcylas.totality.client.dice;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.Dice;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.DiceRollResult;
import zcylas.totality.api.dice.RollOutcome;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.client.dice.DiceRollPresentation.Cue;
import zcylas.totality.client.dice.DiceRollPresentation.Phase;
import zcylas.totality.networking.dice.DiceRollResultPayload;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Creative Test J, Dice Roll UI V2: the d20 is a real numbered icosahedron; every die lands on its authoritative
 * natural value for every seed and arrival time; the shown natural, modifiers, total, DC and outcome are the server's;
 * advantage/disadvantage keep the right die; skipping, repeated or foreign results and repeated clicks change nothing;
 * the payload carries every field. (The server's roll, callbacks and disconnect handling are DiceRollVerification.)
 */
class DiceRollUiV2Test {

    private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-00000000d020");

    private static DiceRollContext context(RollType type, int dc, int... bonuses) {
        List<DiceBonus> list = new ArrayList<>();
        for (int i = 0; i < bonuses.length; i++) list.add(new DiceBonus("Bonus " + i, bonuses[i]));
        return new DiceRollContext("Persuasion", "Charisma Check", Dice.D20, dc, type, list);
    }

    /** A result exactly as DiceRoller builds it. */
    private static DiceRollResult result(DiceRollContext ctx, int roll1, int roll2) {
        int used = switch (ctx.rollType()) {
            case NORMAL -> roll1;
            case ADVANTAGE -> Math.max(roll1, roll2);
            case DISADVANTAGE -> Math.min(roll1, roll2);
        };
        int total = used + ctx.totalBonus();
        RollOutcome outcome = used == 20 ? RollOutcome.CRITICAL_SUCCESS : used == 1 ? RollOutcome.CRITICAL_FAILURE
                : total >= ctx.dc() ? RollOutcome.SUCCESS : RollOutcome.FAILURE;
        return new DiceRollResult(ctx, roll1, ctx.rollType() == RollType.NORMAL ? -1 : roll2, used, ctx.totalBonus(), total, outcome);
    }

    // ── the die ─────────────────────────────────────────────────────────────

    @Test
    void theDieIsANumberedIcosahedronWithOppositeFacesSummingTo21() {
        assertEquals(20, D20Geometry.FACES.size());
        Set<Integer> numbers = new HashSet<>();
        Set<Vector3f> corners = new HashSet<>();
        for (D20Geometry.Face f : D20Geometry.FACES) {
            numbers.add(f.number());
            corners.add(f.a());
            corners.add(f.b());
            corners.add(f.c());
            assertEquals(1f, f.a().length(), 1e-4f, "circumradius 1");
            assertTrue(f.normal().dot(f.centroid()) > 0, "outward normal");
            assertEquals(0f, f.up().dot(f.normal()), 1e-4f, "the number's up lies in the face");
            D20Geometry.Face opposite = D20Geometry.face(21 - f.number());
            assertEquals(-1f, f.normal().dot(opposite.normal()), 1e-4f, f.number() + " is opposite " + (21 - f.number()));
            float edge = f.a().distance(f.b());
            assertEquals(edge, f.b().distance(f.c()), 1e-4f, "equilateral");
            assertEquals(edge, f.c().distance(f.a()), 1e-4f);
        }
        assertEquals(20, numbers.size());
        assertEquals(12, corners.size(), "twelve shared corners");
    }

    @Test
    void settlingShowsEachNumberFrontOnAndUpright() {
        for (int n = 1; n <= 20; n++) {
            Quaternionf q = D20Geometry.settle(n);
            D20Geometry.Face f = D20Geometry.face(n);
            Vector3f normal = q.transform(new Vector3f(f.normal()));
            Vector3f up = q.transform(new Vector3f(f.up()));
            assertEquals(1f, normal.z, 1e-4f, n + " faces the viewer");
            assertEquals(1f, up.y, 1e-4f, n + " reads upright");
            assertEquals(n, D20Geometry.frontNumber(q));
        }
    }

    // ── landing ─────────────────────────────────────────────────────────────

    @Test
    void everyDieLandsOnItsAuthoritativeValueForAnySeedAndArrivalTime() {
        for (int n = 1; n <= 20; n++) {
            for (long seed = 0; seed < 12; seed++) {
                for (long arrival : new long[]{0, 30, 150, 400, 2500}) {
                    DiceTumble d = new DiceTumble(seed * 977 + n, 0);
                    long t0 = 10_000;
                    d.roll(t0);
                    d.land(n, t0 + arrival);
                    long rest = d.restAt();
                    assertTrue(rest >= t0 + DiceTumble.MIN_FREE_MS + DiceTumble.SETTLE_MS);
                    assertTrue(d.atRest(rest) && !d.atRest(rest - 1));
                    assertEquals(n, D20Geometry.frontNumber(d.orientation(rest)), "seed " + seed + " arrival " + arrival);
                    assertEquals(n, D20Geometry.frontNumber(d.orientation(rest + 60_000)));
                    assertEquals(0f, d.offsetX(rest), 1e-6f);
                    assertEquals(0f, d.height(rest), 1e-6f);
                }
            }
        }
    }

    @Test
    void theTumbleIsContinuousAndReallyTumbles() {
        DiceTumble d = new DiceTumble(42, 0);
        d.roll(0);
        d.land(17, 90);
        Set<Integer> seenFaces = new HashSet<>();
        Quaternionf prev = d.orientation(0);
        float maxStep = 0;
        for (long t = 5; t <= d.restAt(); t += 5) {
            Quaternionf q = d.orientation(t);
            seenFaces.add(D20Geometry.frontNumber(q));
            float dot = Math.min(1f, Math.abs(prev.dot(q)));
            maxStep = Math.max(maxStep, (float) (2 * Math.acos(dot)));
            prev = q;
        }
        assertTrue(seenFaces.size() >= 6, "many faces pass the front while it tumbles: " + seenFaces.size());
        assertTrue(maxStep < 0.35f, "no jumps between 5 ms frames (max " + maxStep + " rad)");
        assertTrue(d.bounces(0, d.restAt()) >= 2, "it bounces");
    }

    @Test
    void skippingLandsImmediatelyOnTheSameValue() {
        DiceTumble d = new DiceTumble(7, 0);
        d.roll(0);
        d.land(3, 40);
        d.skip();
        assertTrue(d.atRest(41));
        assertEquals(3, D20Geometry.frontNumber(d.orientation(41)));
        DiceTumble other = new DiceTumble(7, 0);
        other.roll(0);
        other.land(3, 40);
        other.land(19, 60);
        assertEquals(3, other.number(), "only the first landing counts");
    }

    // ── the presentation ────────────────────────────────────────────────────

    @Test
    void shownNaturalModifiersTotalDcAndOutcomeAreTheServers() {
        DiceRollContext ctx = context(RollType.NORMAL, 15, 3, 2);
        DiceRollResult r = result(ctx, 12, -1);
        DiceRollPresentation p = new DiceRollPresentation(SESSION, ctx, 0, 1);
        assertEquals(Phase.OPENING, p.phase(0));
        assertEquals(Phase.IDLE, p.phase(DiceRollPresentation.OPEN_MS));
        assertTrue(p.requestRoll(500));
        assertEquals(Phase.ROLLING, p.phase(520));
        assertEquals(Integer.MIN_VALUE, p.totalShown(520), "nothing is shown before the server answers");
        assertTrue(p.acceptResult(SESSION, r, 560));
        long land = p.landedAt();
        assertEquals(Phase.ROLLING, p.phase(land));
        long reveal = land + DiceRollPresentation.LAND_PAUSE_MS;
        assertEquals(Phase.NATURAL, p.phase(reveal));
        assertEquals(12, p.naturalShown(reveal));
        assertEquals(12, D20Geometry.frontNumber(p.dice()[0].orientation(reveal)), "the die shows the natural roll");
        assertEquals(12, p.totalShown(reveal));
        long mods = reveal + DiceRollPresentation.NATURAL_MS;
        assertEquals(Phase.MODIFIERS, p.phase(mods));
        assertEquals(15, p.totalShown(mods), "12 + 3");
        assertEquals(17, p.totalShown(mods + DiceRollPresentation.MODIFIER_MS), "12 + 3 + 2");
        long verdict = mods + 2 * DiceRollPresentation.MODIFIER_MS;
        assertEquals(verdict, p.outcomeStart());
        assertEquals(Phase.OUTCOME, p.phase(verdict));
        assertTrue(p.outcomeShown(verdict) && !p.canContinue(verdict));
        assertEquals(r.total(), p.totalShown(verdict));
        assertEquals(RollOutcome.SUCCESS, p.result().outcome());
        assertEquals(15, p.result().context().dc());
        assertTrue(p.canContinue(verdict + DiceRollPresentation.OUTCOME_MS));
    }

    @Test
    void successFailureAndNaturalExtremesAreShownAsTheServerDecided() {
        DiceRollContext ctx = context(RollType.NORMAL, 15, 2);
        Object[][] cases = {{13, RollOutcome.SUCCESS}, {12, RollOutcome.FAILURE}, {20, RollOutcome.CRITICAL_SUCCESS},
                {1, RollOutcome.CRITICAL_FAILURE}};
        for (Object[] c : cases) {
            DiceRollResult r = result(ctx, (int) c[0], -1);
            DiceRollPresentation p = new DiceRollPresentation(SESSION, ctx, 0, (int) c[0]);
            p.requestRoll(0);
            p.acceptResult(SESSION, r, 50);
            long end = p.outcomeStart() + DiceRollPresentation.OUTCOME_MS;
            assertEquals(c[1], p.result().outcome());
            assertEquals((int) c[0] + 2, p.totalShown(end));
            assertEquals((int) c[0], D20Geometry.frontNumber(p.dice()[0].orientation(end)));
        }
        // the presentation never recomputes: it shows whatever outcome the server sent (here a nat 20 marked FAILURE)
        DiceRollResult odd = new DiceRollResult(ctx, 20, -1, 20, 2, 22, RollOutcome.FAILURE);
        DiceRollPresentation p = new DiceRollPresentation(SESSION, ctx, 0, 3);
        p.requestRoll(0);
        p.acceptResult(SESSION, odd, 10);
        p.skip(20);
        assertEquals(RollOutcome.FAILURE, p.result().outcome());
        assertEquals(22, p.totalShown(20));
    }

    @Test
    void advantageAndDisadvantageShowBothRealDiceAndKeepTheRightOne() {
        for (RollType type : new RollType[]{RollType.ADVANTAGE, RollType.DISADVANTAGE}) {
            DiceRollContext ctx = context(type, 12, 1);
            DiceRollResult r = result(ctx, 7, 15);
            DiceRollPresentation p = new DiceRollPresentation(SESSION, ctx, 0, 9);
            assertEquals(2, p.dice().length);
            p.requestRoll(0);
            p.acceptResult(SESSION, r, 40);
            long end = p.outcomeStart() + DiceRollPresentation.OUTCOME_MS;
            assertEquals(7, D20Geometry.frontNumber(p.dice()[0].orientation(end)), "first die shows roll1");
            assertEquals(15, D20Geometry.frontNumber(p.dice()[1].orientation(end)), "second die shows roll2");
            int kept = type == RollType.ADVANTAGE ? 15 : 7;
            assertEquals(kept, p.naturalShown(end));
            assertEquals(type == RollType.ADVANTAGE ? 1 : 0, p.keptDie());
            assertEquals(kept + 1, p.totalShown(end));
            assertTrue(p.dice()[1].restAt() > p.dice()[0].restAt(), "the second die lands a moment later");
        }
        DiceRollContext ctx = context(RollType.ADVANTAGE, 12);
        DiceRollPresentation tie = new DiceRollPresentation(SESSION, ctx, 0, 2);
        tie.requestRoll(0);
        tie.acceptResult(SESSION, result(ctx, 9, 9), 10);
        assertEquals(0, tie.keptDie(), "a tie keeps the first die");
        DiceRollPresentation mismatch = new DiceRollPresentation(SESSION, context(RollType.NORMAL, 10), 0, 2);
        mismatch.requestRoll(0);
        assertFalse(mismatch.acceptResult(SESSION, result(ctx, 3, 4), 10), "a two-dice result never feeds a one-die screen");
    }

    @Test
    void skippingNeverChangesTheResultOrRepeatsAnything() {
        DiceRollContext ctx = context(RollType.NORMAL, 10, 4, -1);
        DiceRollResult r = result(ctx, 6, -1);
        // skip while waiting for the server: lands at once when the result arrives
        DiceRollPresentation p = new DiceRollPresentation(SESSION, ctx, 0, 5);
        assertTrue(p.requestRoll(100));
        assertFalse(p.skip(150), "already rolled: skipping sends nothing");
        assertEquals(Phase.ROLLING, p.phase(150));
        assertTrue(p.acceptResult(SESSION, r, 180));
        assertEquals(Phase.FINAL, p.phase(180));
        assertEquals(9, p.totalShown(180));
        assertEquals(6, D20Geometry.frontNumber(p.dice()[0].orientation(180)));
        // skip before rolling rolls (one click) — a check can be hurried, not avoided
        DiceRollPresentation early = new DiceRollPresentation(SESSION, ctx, 0, 5);
        assertTrue(early.skip(400), "Esc before the roll still rolls");
        assertFalse(early.requestRoll(410) || early.skip(420), "never a second click");
        early.acceptResult(SESSION, r, 450);
        assertTrue(early.canContinue(450));
        // skip mid-way and repeated skips: same values, and the verdict cue exactly once
        DiceRollPresentation mid = new DiceRollPresentation(SESSION, ctx, 0, 5);
        mid.requestRoll(0);
        mid.acceptResult(SESSION, r, 30);
        List<Cue> cues = new ArrayList<>(mid.cues(600));
        mid.skip(700);
        mid.skip(800);
        cues.addAll(mid.cues(900));
        cues.addAll(mid.cues(5000));
        assertEquals(1, cues.stream().filter(c -> c == Cue.OUTCOME).count());
        assertEquals(1, cues.stream().filter(c -> c == Cue.ROLL).count());
        assertEquals(9, mid.totalShown(5000));
        assertEquals(r, mid.result());
    }

    @Test
    void repeatedForeignOrEarlyResultsAreIgnored() {
        DiceRollContext ctx = context(RollType.NORMAL, 10, 2);
        DiceRollPresentation p = new DiceRollPresentation(SESSION, ctx, 0, 5);
        assertFalse(p.acceptResult(SESSION, result(ctx, 8, -1), 10), "no result before the roll was requested");
        p.requestRoll(20);
        assertFalse(p.acceptResult(UUID.randomUUID(), result(ctx, 20, -1), 30), "another session's result");
        assertNull(p.result());
        assertTrue(p.acceptResult(SESSION, result(ctx, 8, -1), 40));
        assertFalse(p.acceptResult(SESSION, result(ctx, 20, -1), 50), "a duplicate delivery");
        assertEquals(8, p.result().usedRoll());
        long end = p.outcomeStart() + DiceRollPresentation.OUTCOME_MS;
        assertEquals(8, D20Geometry.frontNumber(p.dice()[0].orientation(end)));
        List<Cue> all = new ArrayList<>(p.cues(0));
        for (long t = 0; t <= end + 1000; t += 16) all.addAll(p.cues(t));
        assertEquals(1, all.stream().filter(c -> c == Cue.LAND).count());
        assertEquals(1, all.stream().filter(c -> c == Cue.MODIFIER).count());
        assertEquals(1, all.stream().filter(c -> c == Cue.OUTCOME).count());
    }

    @Test
    void aSilentServerCanBeWalkedAwayFromOnlyAfterTheTimeout() {
        DiceRollPresentation p = new DiceRollPresentation(SESSION, context(RollType.NORMAL, 10), 0, 5);
        p.requestRoll(1000);
        assertFalse(p.timedOut(1000 + DiceRollPresentation.RESPONSE_TIMEOUT_MS));
        assertTrue(p.timedOut(1001 + DiceRollPresentation.RESPONSE_TIMEOUT_MS));
    }

    // ── synchronisation ─────────────────────────────────────────────────────

    @Test
    void theResultPayloadCarriesEveryField() {
        DiceRollContext ctx = new DiceRollContext("Wisdom Save", "WIS Saving Throw", Dice.D20, 14, RollType.DISADVANTAGE,
                List.of(new DiceBonus("WIS Modifier", 2, "✦"), new DiceBonus("Bane", -3)));
        DiceRollResultPayload sent = new DiceRollResultPayload(SESSION, result(ctx, 18, 4));
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        DiceRollResultPayload.STREAM_CODEC.encode(buf, sent);
        DiceRollResultPayload got = DiceRollResultPayload.STREAM_CODEC.decode(buf);
        assertEquals(sent, got);
        assertEquals(4, got.result().usedRoll());
        assertEquals(RollOutcome.FAILURE, got.result().outcome());
    }

    @Test
    void wiringUsesSessionsQueuesAndCancelsOnDisconnect() throws Exception {
        String client = Files.readString(Path.of("src/main/java/zcylas/totality/networking/dice/DiceRollResultClientHandler.java")).replace("\r\n", "\n");
        assertTrue(client.contains("screen.sessionId().equals(payload.sessionId())"));
        assertTrue(client.contains("if (SESSIONS.request(payload, Minecraft.getInstance().gui.screen() instanceof DiceRollScreen)) open(payload);"));
        String server = Files.readString(Path.of("src/main/java/zcylas/totality/networking/dice/DiceRollClickHandler.java")).replace("\r\n", "\n");
        assertTrue(server.contains("ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PendingDiceRollManager.cancelAll(handler.getPlayer()));"));
        String screen = Files.readString(Path.of("src/main/java/zcylas/totality/screen/dice/DiceRollScreen.java"));
        assertTrue(screen.contains("@Override public boolean shouldCloseOnEsc() { return false; }"));
        assertTrue(screen.contains("if (show.requestRoll(Util.getMillis())) sendClick();"), "closing before the roll still rolls");
        assertFalse(screen.contains("new Random") || screen.contains("nextInt("), "the screen never rolls anything itself");
    }
}
