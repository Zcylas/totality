package zcylas.totality.api.soulgem;

import zcylas.totality.api.mob.stats.MobRank;

/**
 * The authored acceptance policy a registered Soul Gem carries. Acceptance is decided by the
 * REGISTERED GEM, never by an item-ID check inside generic capture logic — see {@link
 * zcylas.totality.api.soulgem.SoulCaptureService}. Category and rank are checked separately so a
 * rejection can be reported precisely (category rejected vs. rank too strong).
 *
 * <p>Each gem tier authors its own rule; there is no universal tier formula (a "Petty = F, Common =
 * D, therefore every tier maps N ranks upward" assumption is explicitly not encoded here — future
 * tiers are free to use a completely different acceptance shape). See
 * {@code TOTALITY_SOUL_GEM_FOUNDATION_IMPLEMENTATION_REPORT_2026-09-17.md} §6, §12.
 */
public interface SoulGemAcceptanceRule {

    boolean acceptsCategory(SoulCategory category);

    boolean acceptsRank(MobRank rank);

    default boolean accepts(SoulCategory category, MobRank rank) {
        return acceptsCategory(category) && acceptsRank(rank);
    }

    /**
     * The one general shape both Petty and Common need for this pass: a single category, with an
     * authored maximum rank ceiling (inclusive), compared via {@link MobRank#isAtMost} — never
     * {@code ordinal()}. This is a convenience factory, not a universal tier formula; a future gem
     * tier is free to implement {@link SoulGemAcceptanceRule} directly with entirely different logic.
     */
    static SoulGemAcceptanceRule categoryUpToRank(SoulCategory category, MobRank maxRank) {
        return new SoulGemAcceptanceRule() {
            @Override public boolean acceptsCategory(SoulCategory c) { return c == category; }
            @Override public boolean acceptsRank(MobRank r) { return r.isAtMost(maxRank); }
        };
    }
}
