package zcylas.totality.client.renderer.hud;

/**
 * Pure geometry constants and position formulas for Totality's four main HUD bars (Health, Mana,
 * Stamina, Food) and the secondary-resource (Rage) row beneath Food — the "contained HUD cleanup"
 * pass (see {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md} and its correction
 * reports). Approved geometry values are user-authored, not derived; the Y-position formulas below
 * are the shared derivation the task asked for so each row's position is computed from the row
 * below it rather than hardcoded independently. No Minecraft/rendering dependency — safe to unit
 * test without a bootstrapped client.
 *
 * <p><b>Final player-HUD and vanilla-chat compatibility correction:</b> the prior symmetric 3px
 * track inset (a separate outer border + solid charcoal inner-frame panel + a 90x6 track floating
 * inside it) read as too thick a border at live GUI Scale 4. That three-layer scheme is gone —
 * replaced by a genuine one-pixel border ({@link #BORDER_WIDTH}) directly enclosing a single
 * 94x10 usable interior ({@link #INTERIOR_WIDTH}/{@link #INTERIOR_HEIGHT}, offset from the bar's
 * own corner by {@link #INTERIOR_OFFSET_X}/{@link #INTERIOR_OFFSET_Y}, both 1px): {@code 96-1-1=94},
 * {@code 12-1-1=10}. {@code TRACK_INSET_X}/{@code TRACK_INSET_Y}/{@code TRACK_WIDTH}/
 * {@code TRACK_HEIGHT}/{@code INNER_FRAME_WIDTH} no longer exist. Embedded text now centers
 * against the full interior (not just the filled colored width) and accounts for the text's own
 * shadow when centering vertically — see {@link #textY(int, int)}.
 *
 * <p>This pass also adds {@link #CHAT_EXTRA_CLEARANCE} and {@link #chatBottomReservation()}, the
 * single geometry-derived reservation vanilla chat is shifted upward by whenever the Totality HUD
 * is eligible to render (see {@code TotalityChatLayout} in this same package) — one adjustable
 * constant so future spacing changes require editing only one number.
 *
 * <p>This intentionally does not touch the pre-cleanup geometry constants still used by
 * {@link TotalityHudRenderer}'s dormant {@code drawBar}/{@code drawBarMirrored} methods (kept as
 * dead code, per the cleanup task's explicit "do not remove dormant code" instruction) — those
 * keep their own old, now-unused-in-production constants unchanged.
 */
final class HudBarLayout {

    private HudBarLayout() {}

    // ── Approved display geometry (destination draw size — the underlying PNG assets' own native
    // resolution is untouched and no longer even referenced by the active code-drawn bar path;
    // see the HUD audit report and the review-correction reports). ────────────────────────────
    static final int FRAME_WIDTH  = 96;
    static final int FRAME_HEIGHT = 12;

    /** Thickness of the single near-black border ring enclosing the whole {@link #FRAME_WIDTH} x
     *  {@link #FRAME_HEIGHT} frame. */
    static final int BORDER_WIDTH = 1;

    /** Offset of the usable interior from the bar's own top-left corner — {@link #BORDER_WIDTH}
     *  on both axes, so the interior sits flush against the inside edge of the border with no
     *  extra bezel between them. */
    static final int INTERIOR_OFFSET_X = 1;
    static final int INTERIOR_OFFSET_Y = 1;

    /** {@code FRAME_WIDTH - INTERIOR_OFFSET_X * 2} / {@code FRAME_HEIGHT - INTERIOR_OFFSET_Y * 2}
     *  ({@code 96-1-1=94}, {@code 12-1-1=10}). The track, the fill, and the embedded text are all
     *  contained within this single region — no separate charcoal panel layer exists between the
     *  border and the interior. */
    static final int INTERIOR_WIDTH  = 94;
    static final int INTERIOR_HEIGHT = 10;

    /** Distance of the left bars' left edge, and Food's right edge, from the screen edge. */
    static final int EDGE_MARGIN = 6;

    /** Vertical gap between the three stacked left bars, and between Food and the Rage row. */
    static final int BAR_GAP = 2;

    /** Distance of the bottom-most left bar (Stamina) from the screen's bottom edge. */
    static final int BOTTOM_MARGIN = 2;

    // ── Shared Y-position formulas — each row derives from the one below it, per the task's
    // "use shared formulas rather than independently hardcoding each value" instruction. ────────

    static int staminaY(int screenH) {
        return screenH - BOTTOM_MARGIN - FRAME_HEIGHT;
    }

    static int manaY(int screenH) {
        return staminaY(screenH) - BAR_GAP - FRAME_HEIGHT;
    }

    static int healthY(int screenH) {
        return manaY(screenH) - BAR_GAP - FRAME_HEIGHT;
    }

    /** Food is horizontally opposite Health but shares its Y — approved requirement. */
    static int foodY(int screenH) {
        return healthY(screenH);
    }

    /** Rage (and any future secondary resource row) sits directly below Food's row, using Food's
     *  own height/gap — its own pip size/spacing are untouched by this formula. */
    static int secondaryResourceY(int screenH) {
        return foodY(screenH) + FRAME_HEIGHT + BAR_GAP;
    }

    static int leftX() {
        return EDGE_MARGIN;
    }

    static int rightX(int screenW) {
        return screenW - FRAME_WIDTH - EDGE_MARGIN;
    }

    // ── Embedded-text placement ─────────────────────────────────────────────────────────────

    /**
     * Horizontally centers a string of {@code textWidth} rendered pixels against the full
     * {@link #INTERIOR_WIDTH} — text stays stationary as the fill percentage changes, since it is
     * centered against the whole interior rather than the currently-filled width.
     */
    static int textX(int barX, int textWidth) {
        return barX + INTERIOR_OFFSET_X + (INTERIOR_WIDTH - textWidth) / 2;
    }

    /**
     * The purely mathematical centering above places text flush against the interior's top edge
     * for vanilla's default {@code fontLineHeight=9} (zero remaining margin either side, treating
     * the glyph+shadow footprint as the full {@code fontLineHeight + 1}). Live GUI Scale 4 testing
     * found this reads as visibly one pixel too high — {@code fontLineHeight} is a line-spacing
     * constant that includes a 1px inter-line gap row a single line of digit glyphs (Totality's bar
     * text is always digits, {@code /}, spaces, and non-descender abbreviation letters — k/M/B —
     * never g/j/p/q/y) never actually inks, so the mathematical formula's "zero margin" already had
     * one row of real spare room sitting entirely at the bottom. This named, one-pixel correction
     * reclaims that row by shifting the block down, without needing to re-derive exact glyph metrics.
     */
    static final int VALUE_TEXT_Y_OFFSET = 1;

    /**
     * Vertically centers a line of height {@code fontLineHeight} within the {@link #INTERIOR_HEIGHT}
     * interior starting at {@code barY}, accounting for the text's own rendered shadow. Vanilla's
     * shadow is drawn offset by (1,1) from the glyph, so the total visual footprint is
     * {@code fontLineHeight + 1} tall (~9px glyph + 1px shadow for vanilla's default font), not
     * just {@code fontLineHeight} — using the glyph height alone would let the shadow spill past
     * the interior's bottom edge. With the default {@code fontLineHeight=9} the purely mathematical
     * center evaluates to exactly {@code barY + INTERIOR_OFFSET_Y}; {@link #VALUE_TEXT_Y_OFFSET} is
     * then applied on top of that, per live visual testing (see its own javadoc for why this is
     * still safely contained).
     */
    static int textY(int barY, int fontLineHeight) {
        int visualHeight = fontLineHeight + 1;
        int baseTextY = barY + INTERIOR_OFFSET_Y + (INTERIOR_HEIGHT - visualHeight) / 2;
        return baseTextY + VALUE_TEXT_Y_OFFSET;
    }

    // ── Vanilla-chat bottom reservation ─────────────────────────────────────────────────────

    /** The one adjustable knob for how much extra clearance (beyond the left resource-bar
     *  stack's own footprint) vanilla chat is shifted up by — changing only this number reshapes
     *  the whole chat system, since every chat mixin reads {@link #chatBottomReservation()}. */
    static final int CHAT_EXTRA_CLEARANCE = 4;

    /** Height of the left-side Health/Mana/Stamina stack: three {@link #FRAME_HEIGHT} bars with
     *  two {@link #BAR_GAP} gaps between them ({@code 3*12 + 2*2 = 40}). */
    static int leftStackHeight() {
        return 3 * FRAME_HEIGHT + 2 * BAR_GAP;
    }

    /**
     * The total amount vanilla chat (rendered messages, the {@code ChatScreen} input box, command
     * suggestions, and the command-usage hint) is shifted upward by whenever the Totality HUD is
     * eligible to render — {@code leftStackHeight() + BOTTOM_MARGIN + CHAT_EXTRA_CLEARANCE},
     * i.e. {@code 40 + 2 + 4 = 46}.
     */
    static int chatBottomReservation() {
        return leftStackHeight() + BOTTOM_MARGIN + CHAT_EXTRA_CLEARANCE;
    }
}
