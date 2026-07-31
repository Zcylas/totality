package zcylas.totality.client.renderer.hud;

import net.minecraft.client.Minecraft;

/**
 * Single source of truth the vanilla-chat compatibility mixins call to find out how much extra
 * bottom clearance to add to vanilla's own chat geometry — part of the final player-HUD and
 * vanilla-chat compatibility correction (see
 * {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md}'s newest addendum).
 *
 * <p>Public (unlike the package-private {@link HudBarLayout}) so the chat mixins — which live in
 * {@code zcylas.totality.mixin.client.chat}, a different package — can call it; it lives in this
 * package specifically so it can read {@link HudBarLayout#chatBottomReservation()} directly
 * without needing to make any of {@code HudBarLayout} itself public.
 *
 * <p>Eligibility mirrors {@link TotalityHudRenderer}'s own gate for the persistent in-world HUD
 * ({@code client.player != null && !client.gui.hud.isHidden()}) — chat is only shifted while the
 * Totality HUD would actually be occupying that space; vanilla's own chat geometry is otherwise
 * left completely alone (title/menu screens, no world loaded, F1 hidden).
 *
 * <p><b>Chat-origin correction (this pass):</b> live testing found the message stack sitting too
 * high, with a large empty gap above the input line, even though every mixin already added the
 * identical {@link #extraBottomReservation()} to its own vanilla literal (verified scale-invariant
 * by construction — see the four mixins' own javadoc). The actual cause was architectural, not
 * arithmetic: {@code ChatComponent}'s message-bottom anchor and {@code ChatScreen}'s input-Y
 * anchor each independently added the reservation to two <em>unrelated</em> vanilla constants
 * ({@code ChatComponent.BOTTOM_MARGIN}=40 vs. {@code ChatScreen}'s own hardcoded input margin=12),
 * which happen to already sit 28px apart in completely unmodified vanilla Minecraft — normally
 * invisible because that gap sits directly above the (hidden-while-typing) hotbar, but glaringly
 * empty once the whole block floats 46px higher with nothing nearby to justify it. The fix: the
 * message-bottom anchor no longer derives from {@code BOTTOM_MARGIN} at all — it now derives
 * directly from the SAME {@link #CHAT_INPUT_BOTTOM_MARGIN} the input uses, offset by
 * {@link #MESSAGE_TO_INPUT_GAP} (vanilla's own {@code ChatComponent.MESSAGE_BOTTOM_TO_MESSAGE_TOP}
 * constant, 8px — not an invented value), so messages stack immediately above input using an
 * actual vanilla spacing constant instead of the coincidental, much larger 28px gap two unrelated
 * constants used to produce.
 */
public final class TotalityChatLayout {

    private TotalityChatLayout() {}

    /**
     * The number of extra pixels vanilla chat should be shifted upward by right now — either
     * {@link HudBarLayout#chatBottomReservation()} (46) while the Totality HUD is eligible to
     * render, or {@code 0} otherwise (preserving vanilla's untouched behavior). Applied exactly
     * once per chat element, inside each element's own vanilla bottom-margin literal (before any
     * chat-scale division), which keeps the resulting screen position scale-invariant — see each
     * mixin's own javadoc for the per-element algebra.
     */
    public static int extraBottomReservation() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.gui.hud.isHidden()) return 0;
        return HudBarLayout.chatBottomReservation();
    }

    /** Mirrors {@code ChatScreen}'s own vanilla input-box Y margin ({@code height - 12}) — the
     *  single shared "bottom anchor" every other chat element's own margin is now expressed
     *  relative to, instead of each re-deriving its own independent vanilla constant. */
    public static final int CHAT_INPUT_BOTTOM_MARGIN = 12;

    /** Vanilla's own {@code ChatComponent.MESSAGE_BOTTOM_TO_MESSAGE_TOP} constant (8) — reused
     *  here as the gap between the bottom of the message stack and the top of the input line, so
     *  messages sit immediately above input using a real vanilla-authored spacing value rather
     *  than the incidental 28px gap {@code BOTTOM_MARGIN}(40) vs. the input's own margin(12)
     *  produced when computed independently. */
    public static final int MESSAGE_TO_INPUT_GAP = 8;

    /**
     * The final margin (vanilla base + reservation) {@code ChatScreen}'s input box and
     * {@code CommandSuggestions}' suggestion popup are both positioned relative to
     * ({@code height - inputBottomMargin()}) — the single shared bottom origin.
     */
    public static int inputBottomMargin() {
        return CHAT_INPUT_BOTTOM_MARGIN + extraBottomReservation();
    }

    /**
     * The final margin {@code ChatComponent}'s message-bottom anchor is positioned relative to
     * ({@code (guiHeight - messageBottomMargin()) / chatScale}) — derived directly from
     * {@link #inputBottomMargin()} plus {@link #MESSAGE_TO_INPUT_GAP}, guaranteeing messages
     * always sit exactly {@code MESSAGE_TO_INPUT_GAP} pixels above input regardless of the
     * reservation's current value (single shared origin, not two independently-perturbed
     * constants).
     */
    public static int messageBottomMargin() {
        return inputBottomMargin() + MESSAGE_TO_INPUT_GAP;
    }

    /** Vanilla's own {@code CommandSuggestions.extractUsage} margin (27) plus reservation — the
     *  usage-hint box's own vanilla spacing above input (27-12=15px) was not reported broken by
     *  live testing, so only the shared reservation is applied here, unlike
     *  {@link #messageBottomMargin()}. */
    public static final int USAGE_HINT_VANILLA_MARGIN = 27;

    public static int usageHintBottomMargin() {
        return USAGE_HINT_VANILLA_MARGIN + extraBottomReservation();
    }

    /**
     * ChatScreen visual-correction pass: {@code ChatScreen.extractRenderState} draws a translucent
     * background rectangle behind the (borderless — {@code EditBox.setBordered(false)}) input box,
     * at {@code fill(2, height-14, width-2, height-2, backgroundColor)} — decompiled and confirmed
     * against MC 26.2's {@code minecraft-merged-deobf-26.2.jar}
     * ({@code net/minecraft/client/gui/screens/ChatScreen.class}). That rectangle sits exactly 2px
     * above the EditBox's own bounding box in unmodified vanilla ({@code 14 = 12(EditBox's own
     * margin) + 2}, {@code 2 = 0 + 2}) — this is the input's entire visual backing, not a separate
     * decorative element, which is why it must translate in lockstep with the box rather than stay
     * at the screen's true bottom edge.
     *
     * <p>Both margins below are derived directly from {@link #inputBottomMargin()} — the exact
     * same shared origin the {@code EditBox} itself uses — plus the fixed 2px vanilla offset,
     * rather than independently adding {@link #extraBottomReservation()} to the raw {@code 14}/
     * {@code 2} literals a second time. This guarantees the background can never drift from the
     * box it sits behind, by construction, regardless of any future change to the reservation.
     */
    private static final int INPUT_BACKGROUND_ABOVE_EDIT_BOX = 2;
    private static final int VANILLA_EDIT_BOX_HEIGHT = 12;

    public static int inputBackgroundTopMargin() {
        return inputBottomMargin() + INPUT_BACKGROUND_ABOVE_EDIT_BOX;
    }

    public static int inputBackgroundBottomMargin() {
        return inputBottomMargin() - VANILLA_EDIT_BOX_HEIGHT + INPUT_BACKGROUND_ABOVE_EDIT_BOX;
    }
}
