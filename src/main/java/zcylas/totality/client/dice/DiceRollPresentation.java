package zcylas.totality.client.dice;

import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.DiceRollContext;
import zcylas.totality.api.dice.DiceRollResult;
import zcylas.totality.api.dice.RollType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The client-side timeline of one dice-check presentation (Dice Roll UI V2), as a pure function of time. It never
 * rolls anything: the natural values, modifiers, total and outcome it shows all come from the server's
 * {@link DiceRollResult}, accepted once for this screen's session. The player can roll once ({@link #requestRoll}
 * says whether a click must be sent) and skip at any moment ({@link #skip}); skipping only jumps the presentation to
 * its final frame — the result is the same one either way.
 *
 * <p>Sequence: opening, idle (click or E to roll), rolling (the dice tumble; they land when the result is known),
 * natural (the kept natural value), modifiers (each bonus joins the total in turn), outcome (the verdict), final
 * (Continue).
 */
public final class DiceRollPresentation {

    public enum Phase { OPENING, IDLE, ROLLING, NATURAL, MODIFIERS, OUTCOME, FINAL }

    /** Sound/effect cues, each emitted once when its moment passes. */
    public enum Cue { OPEN, ROLL, BOUNCE, LAND, MODIFIER, OUTCOME }

    public static final long OPEN_MS = 280;
    public static final long LAND_PAUSE_MS = 120;
    public static final long NATURAL_MS = 700;
    public static final long MODIFIER_MS = 380;
    public static final long OUTCOME_MS = 650;
    /** How long to wait for the server's answer before the player may close the screen anyway. */
    public static final long RESPONSE_TIMEOUT_MS = 10_000;

    private final UUID sessionId;
    private final DiceRollContext context;
    private final DiceTumble[] dice;
    private final long openedAt;

    private long rollStart = -1;
    private DiceRollResult result;
    private boolean skipRequested;
    private long skippedAt = -1;

    private long cuesUntil;
    private boolean openCue = true, rollCue, landCue, outcomeCue;
    private int modifierCues;

    public DiceRollPresentation(UUID sessionId, DiceRollContext context, long now, long seed) {
        this.sessionId = sessionId;
        this.context = context;
        this.openedAt = now;
        this.cuesUntil = now;
        boolean two = context.rollType() != RollType.NORMAL;
        this.dice = two
                ? new DiceTumble[]{new DiceTumble(seed, 0), new DiceTumble(seed * 31 + 7, 160)}
                : new DiceTumble[]{new DiceTumble(seed, 0)};
    }

    public UUID sessionId() {
        return sessionId;
    }

    public DiceRollContext context() {
        return context;
    }

    public DiceTumble[] dice() {
        return dice;
    }

    public DiceRollResult result() {
        return result;
    }

    public long openedAt() {
        return openedAt;
    }

    /** Starts the roll. True only the first time: exactly one click is ever sent to the server. */
    public boolean requestRoll(long now) {
        if (rollStart >= 0) return false;
        rollStart = now;
        for (DiceTumble d : dice) d.roll(now);
        rollCue = true;
        return true;
    }

    public boolean rollRequested() {
        return rollStart >= 0;
    }

    /**
     * Accepts the server's result for this session, once. A result for another session, a repeated delivery, or a
     * result before the roll was requested is ignored (false).
     */
    public boolean acceptResult(UUID session, DiceRollResult r, long now) {
        if (!sessionId.equals(session) || result != null || rollStart < 0 || r == null) return false;
        if ((dice.length == 2) != r.hasSecondDie()) return false;
        result = r;
        dice[0].land(r.roll1(), now);
        if (dice.length == 2) dice[1].land(r.roll2(), now);
        if (skipRequested) applySkip(now);
        return true;
    }

    /**
     * Skips to the final frame (after the result arrives, if it has not yet). Before the roll this also rolls — a
     * check can be hurried, never avoided. Returns true when that means a click must be sent.
     */
    public boolean skip(long now) {
        boolean click = requestRoll(now);
        if (result == null) skipRequested = true;
        else applySkip(now);
        return click;
    }

    private void applySkip(long now) {
        if (skippedAt >= 0) return;
        skippedAt = now;
        for (DiceTumble d : dice) d.skip();
    }

    public boolean skipped() {
        return skippedAt >= 0;
    }

    public boolean awaitingServer() {
        return rollStart >= 0 && result == null;
    }

    public boolean timedOut(long now) {
        return awaitingServer() && now - rollStart > RESPONSE_TIMEOUT_MS;
    }

    /** When every die is at rest, or -1 while the result is unknown. */
    public long landedAt() {
        if (result == null) return -1;
        long at = 0;
        for (DiceTumble d : dice) at = Math.max(at, d.restAt());
        return at;
    }

    private long revealStart() {
        return landedAt() + LAND_PAUSE_MS;
    }

    public long outcomeStart() {
        if (skippedAt >= 0) return skippedAt;
        return revealStart() + NATURAL_MS + MODIFIER_MS * bonuses().size();
    }

    public Phase phase(long now) {
        if (rollStart < 0) return now - openedAt < OPEN_MS ? Phase.OPENING : Phase.IDLE;
        if (result == null) return Phase.ROLLING;
        if (skippedAt >= 0) return Phase.FINAL;
        long t = now - revealStart();
        if (t < 0) return Phase.ROLLING;
        if (t < NATURAL_MS) return Phase.NATURAL;
        t -= NATURAL_MS;
        if (t < MODIFIER_MS * bonuses().size()) return Phase.MODIFIERS;
        t -= MODIFIER_MS * bonuses().size();
        return t < OUTCOME_MS ? Phase.OUTCOME : Phase.FINAL;
    }

    /** The modifiers as the server resolved them (the request's copy until the result arrives). */
    public List<DiceBonus> bonuses() {
        return result != null ? result.context().bonuses() : context.bonuses();
    }

    /** How many modifiers have joined the total so far. */
    public int modifiersApplied(long now) {
        Phase p = phase(now);
        if (p == Phase.OUTCOME || p == Phase.FINAL) return bonuses().size();
        if (p != Phase.MODIFIERS) return 0;
        long t = now - revealStart() - NATURAL_MS;
        return (int) Math.min(bonuses().size(), t / MODIFIER_MS + 1);
    }

    /** Progress (0-1) of the modifier currently joining the total, or -1. */
    public float modifierProgress(long now) {
        if (phase(now) != Phase.MODIFIERS) return -1;
        long t = now - revealStart() - NATURAL_MS;
        return (t % MODIFIER_MS) / (float) MODIFIER_MS;
    }

    /** The natural value the check uses (the kept die with advantage/disadvantage), once the dice have landed. */
    public int naturalShown(long now) {
        Phase p = phase(now);
        return result == null || p == Phase.ROLLING ? -1 : result.usedRoll();
    }

    /** The running total: the natural value plus the modifiers joined so far; the server's total at the verdict. */
    public int totalShown(long now) {
        Phase p = phase(now);
        if (result == null || p == Phase.ROLLING) return Integer.MIN_VALUE;
        if (p == Phase.OUTCOME || p == Phase.FINAL) return result.total();
        int total = result.usedRoll();
        List<DiceBonus> b = bonuses();
        for (int i = 0; i < modifiersApplied(now); i++) total += b.get(i).value();
        return total;
    }

    public boolean outcomeShown(long now) {
        Phase p = phase(now);
        return p == Phase.OUTCOME || p == Phase.FINAL;
    }

    public boolean canContinue(long now) {
        return phase(now) == Phase.FINAL;
    }

    /** With two dice: the index of the kept die (the first on a tie), else -1. */
    public int keptDie() {
        if (result == null || dice.length < 2) return -1;
        return result.usedRoll() == result.roll1() ? 0 : 1;
    }

    /** The cues whose moments passed since the last call (each cue once). */
    public List<Cue> cues(long now) {
        List<Cue> out = new ArrayList<>();
        if (openCue) {
            out.add(Cue.OPEN);
            openCue = false;
        }
        if (rollCue) {
            out.add(Cue.ROLL);
            rollCue = false;
        }
        if (skippedAt < 0) {
            for (DiceTumble d : dice) for (int i = d.bounces(cuesUntil, now); i > 0; i--) out.add(Cue.BOUNCE);
        }
        Phase p = phase(now);
        if (!landCue && result != null && skippedAt < 0 && p != Phase.ROLLING) {
            out.add(Cue.LAND);
            landCue = true;
        }
        int applied = skippedAt >= 0 ? modifierCues : modifiersApplied(now);
        for (; modifierCues < applied; modifierCues++) out.add(Cue.MODIFIER);
        if (!outcomeCue && (p == Phase.OUTCOME || p == Phase.FINAL)) {
            out.add(Cue.OUTCOME);
            outcomeCue = true;
        }
        cuesUntil = now;
        return out;
    }
}
