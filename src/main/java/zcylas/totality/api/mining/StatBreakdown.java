package zcylas.totality.api.mining;

import java.util.List;

/**
 * A stat's final value plus the named contributions that produced it, in application order — for
 * the Shift-tooltip provenance view (§10 of the V2 balance pass). Built from real contributions,
 * never guessed backward from the final number.
 */
public record StatBreakdown(float value, List<Contribution> contributions) {

    /** BASE starts the value (displayed plain, e.g. "75", never "+75"); ADD/MULTIPLY/MIN modify
     *  what came before it. Every breakdown's first contribution is BASE. */
    public enum Op { BASE, ADD, MULTIPLY, MIN }

    public record Contribution(String label, Op op, float amount) {}
}
