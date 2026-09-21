package zcylas.totality.api.mob.stats;

import java.util.Locale;

/**
 * Canonical Mob Rank: {@code F < E < D < C < B < A < S < Z < ZERO}. {@link #ZERO} is the external/
 * display identity {@code "0"} ("Family Rank") — an apex narrative classification ABOVE {@link #Z},
 * never the weakest rank. Rank is authored classification only; it is never derived from level,
 * Challenge Rating, or any stat formula. See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §4-5.
 *
 * <p>Strength comparisons must go through {@link #order()} / {@link #isAtMost}/{@link #isAtLeast}/
 * {@link #compareStrength} — never {@code ordinal()}. Adding {@link #F}, {@link #Z}, and {@link
 * #ZERO} to this enum changed every existing constant's Java ordinal; {@link #order()} is an
 * explicit, authored value chosen independently of declaration order, so future reordering or
 * insertion cannot silently change comparison semantics again.
 *
 * <p><b>{@link #getColor()} values for {@link #F}, {@link #Z}, and {@link #ZERO} are PROVISIONAL
 * presentation placeholders (2026-09-17)</b> — the user has not yet canonically designed the visual
 * colors for these three new ranks. Only the semantic rank order above is canonical; do not treat
 * these three specific color literals as final, and do not infer any color-design intent from them.
 * {@code E}/{@code D}/{@code C}/{@code B}/{@code A}/{@code S}'s pre-existing colors are unaffected
 * and unchanged by this pass.
 */
public enum MobRank {
    F(0xFF555555, 0),      // color PROVISIONAL — see class Javadoc
    E(0xFF888888, 1),
    D(0xFF44AA44, 2),
    C(0xFF4488CC, 3),
    B(0xFFAA44CC, 4),
    A(0xFFCCAA00, 5),
    S(0xFFCC4400, 6),
    Z(0xFF9900CC, 7),      // color PROVISIONAL — see class Javadoc
    ZERO(0xFFFFD700, 8);   // color PROVISIONAL — see class Javadoc

    private final int color;
    private final int order;

    MobRank(int color, int order) {
        this.color = color;
        this.order = order;
    }

    public int getColor() { return color; }

    /** Authored strength order — never Java {@code ordinal()}. Higher means stronger. */
    public int order() { return order; }

    /** External/display identifier: {@code "0"} for {@link #ZERO}, the enum name otherwise. */
    public String getId() { return this == ZERO ? "0" : name(); }

    public boolean isAtLeast(MobRank other) { return order >= other.order; }
    public boolean isAtMost(MobRank other) { return order <= other.order; }
    public boolean isStrongerThan(MobRank other) { return order > other.order; }
    public boolean isWeakerThan(MobRank other) { return order < other.order; }
    public int compareStrength(MobRank other) { return Integer.compare(order, other.order); }

    /**
     * Name/id-based parsing. Accepts the legacy E/D/C/B/A/S names exactly as {@code
     * MobStatBlock}'s pre-existing JSON data always has (case-insensitive, matching the old {@code
     * MobRank.valueOf(rank.toUpperCase())} behavior), plus {@code "F"}, {@code "Z"}, and {@code "0"}
     * (mapping to {@link #ZERO}). Falls back to {@link #E} on anything unrecognized — the exact same
     * fallback {@code MobStatBlock.getFixedRank()} always used, preserved here rather than changed.
     */
    public static MobRank fromId(String id) {
        if (id == null) return E;
        String v = id.trim().toUpperCase(Locale.ROOT);
        if (v.equals("0")) return ZERO;
        for (MobRank r : values()) {
            if (r != ZERO && r.name().equals(v)) return r;
        }
        return E;
    }

    /**
     * Stable authored-order decode for the network wire format. Deliberately not {@code
     * values()[order]} — {@link #order()} and Java declaration order coincide today only because
     * this is the first time either was authored; a future reordering of the enum constants must
     * not silently break this decode the way raw ordinal indexing would.
     */
    public static MobRank fromOrder(int order) {
        for (MobRank r : values()) {
            if (r.order == order) return r;
        }
        return E;
    }
}
