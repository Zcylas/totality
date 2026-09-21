package zcylas.totality.client.renderer.hud;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the vanilla-chat bottom-reservation half of the final player-HUD
 * and vanilla-chat compatibility correction — see
 * {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md}'s newest addendum.
 *
 * <p><b>Evidentiary limits:</b> every test in this file is a source-text regression sentinel, not
 * runtime proof. The four chat mixins and {@link TotalityChatLayout} all require a bootstrapped
 * {@code Minecraft}/mixin-applied classloading environment to execute for real — unavailable under
 * plain JUnit, the same constraint documented by every other {@code *SourceRegressionTest} in this
 * suite. {@link HudBarLayoutTest} covers {@code chatBottomReservation()}'s actual arithmetic
 * (46 = 40+2+4) with a real, executing test; runtime confirmation that vanilla chat visually moves
 * — and that its input box/suggestions/scrollbar/hover all move together — is the manual in-client
 * validation recorded in the implementation report. The exact vanilla bytecode offsets these
 * mixins target were confirmed once, out-of-band, by decompiling/{@code javap}-ing MC 26.2's real
 * {@code minecraft-merged-deobf-26.2.jar} (not assumed from an older version) — these sentinels
 * only guard against this source regressing afterward, they do not re-derive that confirmation.
 */
class ChatCompatibilityCorrectionSourceRegressionTest {

    private static final Path CHAT_LAYOUT =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/TotalityChatLayout.java");
    private static final Path MIXIN_CONFIG =
            Path.of("src/main/resources/totality.mixins.json");
    private static final Path CHAT_COMPONENT_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/chat/ChatComponentBottomMarginMixin.java");
    private static final Path CHAT_SCREEN_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/chat/ChatScreenInputPositionMixin.java");
    private static final Path CHAT_SCREEN_BACKGROUND_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/chat/ChatScreenInputBackgroundMixin.java");
    private static final Path SUGGESTIONS_LIST_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/chat/CommandSuggestionsListPositionMixin.java");
    private static final Path SUGGESTIONS_USAGE_MIXIN =
            Path.of("src/main/java/zcylas/totality/mixin/client/chat/CommandSuggestionsUsagePositionMixin.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    private static String methodBody(String source, String methodSignatureStart, String nextMethodOrMarker) {
        int start = source.indexOf(methodSignatureStart);
        assertTrue(start >= 0, "expected to find method starting with: " + methodSignatureStart);
        int end = source.indexOf(nextMethodOrMarker, start + methodSignatureStart.length());
        assertTrue(end > start, "expected to find the next marker after the method: " + nextMethodOrMarker);
        return source.substring(start, end);
    }

    // ── HudBarLayout.chatBottomReservation() single source of truth ─────────────────────────

    @Test
    void hudBarLayoutDeclaresTheSingleAdjustableChatClearanceConstant() throws Exception {
        String source = read(Path.of("src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java"));
        assertTrue(source.contains("static final int CHAT_EXTRA_CLEARANCE = 4;"));
        assertTrue(source.contains("static int chatBottomReservation()"));
    }

    // ── TotalityChatLayout: eligibility gate + single reservation source ────────────────────

    @Test
    void totalityChatLayoutIsPublicSoMixinsInADifferentPackageCanCallIt() throws Exception {
        String source = read(CHAT_LAYOUT);
        assertTrue(source.contains("public final class TotalityChatLayout"));
        assertTrue(source.contains("public static int extraBottomReservation()"));
    }

    // Eligibility mirrors TotalityHudRenderer's own gate — player loaded and HUD not F1-hidden.
    @Test
    void totalityChatLayoutMirrorsTheHudRendererEligibilityGate() throws Exception {
        String source = read(CHAT_LAYOUT);
        assertTrue(source.contains("client.player == null || client.gui.hud.isHidden()"),
                "chat reservation must only apply while the Totality HUD itself would be eligible to render");
        assertTrue(source.contains("return 0;"),
                "ineligible states (no player, F1-hidden) must fall back to zero extra reservation, i.e. untouched vanilla behavior");
    }

    @Test
    void totalityChatLayoutReadsHudBarLayoutsSingleReservationMethod() throws Exception {
        String source = read(CHAT_LAYOUT);
        assertTrue(source.contains("HudBarLayout.chatBottomReservation()"));
    }

    // ── Chat-origin correction: single shared bottom origin (final correction pass) ─────────

    // 22/32/33. One shared origin, CHAT_EXTRA_CLEARANCE exists exactly once (in HudBarLayout,
    // already covered above), and the final reservation is derived, not duplicated as a literal.
    @Test
    void totalityChatLayoutDeclaresTheSharedInputOriginAndMessageGapConstants() throws Exception {
        String source = read(CHAT_LAYOUT);
        assertTrue(source.contains("public static final int CHAT_INPUT_BOTTOM_MARGIN = 12;"));
        assertTrue(source.contains("public static final int MESSAGE_TO_INPUT_GAP = 8;"));
        assertTrue(source.contains("public static int inputBottomMargin()"));
        assertTrue(source.contains("public static int messageBottomMargin()"));
        assertTrue(source.contains("public static int usageHintBottomMargin()"));
    }

    // 26/27. Messages and command suggestions both derive from the exact same input-origin
    // method — messageBottomMargin() calls inputBottomMargin() rather than independently
    // re-deriving its own base+reservation, guaranteeing they can never drift apart.
    @Test
    void messageBottomMarginDerivesFromTheSameInputOriginRatherThanItsOwnIndependentBase() throws Exception {
        String source = read(CHAT_LAYOUT);
        String messageBottomMarginBody = methodBody(source, "public static int messageBottomMargin()", "public static final int USAGE_HINT_VANILLA_MARGIN");
        assertTrue(messageBottomMarginBody.contains("inputBottomMargin()"),
                "messages must derive their bottom margin from the same input-origin method the input box itself uses");
        assertTrue(messageBottomMarginBody.contains("MESSAGE_TO_INPUT_GAP"),
                "the gap above input must be the named vanilla-sourced constant, not a second inline literal");
    }

    // 23. Reservation is not applied twice to message history — messageBottomMargin()'s own body
    // must not call extraBottomReservation() a second, independent time; it must only reuse
    // inputBottomMargin() (which already applies the reservation once).
    @Test
    void messageBottomMarginDoesNotCallExtraBottomReservationDirectly() throws Exception {
        String source = read(CHAT_LAYOUT);
        String messageBottomMarginBody = methodBody(source,
                "public static int messageBottomMargin() {", "public static final int USAGE_HINT_VANILLA_MARGIN");
        assertFalse(messageBottomMarginBody.contains("extraBottomReservation()"),
                "messageBottomMargin() must not call extraBottomReservation() itself — it must reuse inputBottomMargin(), which already applies it exactly once");
        assertTrue(messageBottomMarginBody.contains("return inputBottomMargin() + MESSAGE_TO_INPUT_GAP;"));
    }

    // inputBottomMargin() and usageHintBottomMargin() are the only two real call sites of
    // extraBottomReservation() — each applies it exactly once, to its own vanilla base.
    @Test
    void inputAndUsageHintEachApplyTheReservationExactlyOnce() throws Exception {
        String source = read(CHAT_LAYOUT);
        String inputBottomMarginBody = methodBody(source,
                "public static int inputBottomMargin() {", "public static int messageBottomMargin()");
        assertTrue(inputBottomMarginBody.contains("return CHAT_INPUT_BOTTOM_MARGIN + extraBottomReservation();"));

        String usageHintBody = methodBody(source,
                "public static int usageHintBottomMargin() {", "}\n}");
        assertTrue(usageHintBody.contains("return USAGE_HINT_VANILLA_MARGIN + extraBottomReservation();"));
    }

    // ── Mixin config registration ────────────────────────────────────────────────────────────

    @Test
    void allFiveChatMixinsAreRegisteredInTheClientMixinConfig() throws Exception {
        String source = read(MIXIN_CONFIG);
        assertTrue(source.contains("\"client.chat.ChatComponentBottomMarginMixin\""));
        assertTrue(source.contains("\"client.chat.ChatScreenInputBackgroundMixin\""));
        assertTrue(source.contains("\"client.chat.ChatScreenInputPositionMixin\""));
        assertTrue(source.contains("\"client.chat.CommandSuggestionsListPositionMixin\""));
        assertTrue(source.contains("\"client.chat.CommandSuggestionsUsagePositionMixin\""));
    }

    // The chat mixins are all client-only (rendering/input classes only exist on the client) —
    // must be registered under "client", never the always-loaded common "mixins" array, or a
    // dedicated server would try to classload them.
    @Test
    void chatMixinsAreRegisteredUnderClientOnlySection() throws Exception {
        String source = read(MIXIN_CONFIG);
        int clientArrayStart = source.indexOf("\"client\": [");
        int clientArrayEnd = source.indexOf("],", clientArrayStart);
        assertTrue(clientArrayStart >= 0 && clientArrayEnd > clientArrayStart);
        String clientArray = source.substring(clientArrayStart, clientArrayEnd);
        assertTrue(clientArray.contains("client.chat.ChatComponentBottomMarginMixin"));
        assertTrue(clientArray.contains("client.chat.ChatScreenInputBackgroundMixin"));
        assertTrue(clientArray.contains("client.chat.ChatScreenInputPositionMixin"));
        assertTrue(clientArray.contains("client.chat.CommandSuggestionsListPositionMixin"));
        assertTrue(clientArray.contains("client.chat.CommandSuggestionsUsagePositionMixin"));

        int commonArrayStart = source.indexOf("\"mixins\": [");
        int commonArrayEnd = source.indexOf("],", commonArrayStart);
        String commonArray = source.substring(commonArrayStart, commonArrayEnd);
        assertFalse(commonArray.contains("chat."), "chat mixins must not appear in the always-loaded common section");
    }

    // ── ChatComponentBottomMarginMixin: single shared origin for render + hover/click ───────

    @Test
    void chatComponentMixinTargetsThePrivateFourArgExtractRenderStateOverload() throws Exception {
        String source = read(CHAT_COMPONENT_MIXIN);
        assertTrue(source.contains("@Mixin(ChatComponent.class)"));
        assertTrue(source.contains(
                "method = \"extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V\""),
                "must target the private 4-arg overload, not the public 7-arg wrapper — this is the one method shared by rendering and captureClickableText's hover/click hit-testing");
        assertTrue(source.contains("@Constant(intValue = 40)"));
        // Chat-origin correction: the message-bottom margin no longer adds the reservation to
        // vanilla's own BOTTOM_MARGIN(40) — it is fully replaced by the shared-origin method.
        assertTrue(source.contains("return TotalityChatLayout.messageBottomMargin();"));
    }

    // ── ChatScreenInputPositionMixin: ordinal-qualified so the EditBox's own height is untouched ──

    @Test
    void chatScreenMixinTargetsOnlyTheFirstOccurrenceOfTheYPositionConstant() throws Exception {
        String source = read(CHAT_SCREEN_MIXIN);
        assertTrue(source.contains("@Mixin(ChatScreen.class)"));
        assertTrue(source.contains("method = \"init\""));
        assertTrue(source.contains("@Constant(intValue = 12, ordinal = 0)"),
                "must use ordinal 0 to target only the y-position constant, not the EditBox's own height (the second literal 12 in init())");
        assertTrue(source.contains("return TotalityChatLayout.inputBottomMargin();"),
                "input is the shared origin itself — it reads inputBottomMargin() directly");
    }

    // ── CommandSuggestions: list popup + usage hint, both anchorToBottom-branch constants ───

    @Test
    void suggestionsListMixinTargetsShowSuggestions() throws Exception {
        String source = read(SUGGESTIONS_LIST_MIXIN);
        assertTrue(source.contains("@Mixin(CommandSuggestions.class)"));
        assertTrue(source.contains("method = \"showSuggestions\""));
        assertTrue(source.contains("@Constant(intValue = 12)"));
        assertTrue(source.contains("return TotalityChatLayout.inputBottomMargin();"),
                "the suggestion popup must attach to the exact same shared origin the input box itself uses");
    }

    @Test
    void suggestionsUsageMixinTargetsExtractUsage() throws Exception {
        String source = read(SUGGESTIONS_USAGE_MIXIN);
        assertTrue(source.contains("@Mixin(CommandSuggestions.class)"));
        assertTrue(source.contains("method = \"extractUsage\""));
        assertTrue(source.contains("@Constant(intValue = 27)"));
        assertTrue(source.contains("return TotalityChatLayout.usageHintBottomMargin();"));
    }

    // ── Scope guard: no vanilla chat redesign — width/opacity/history/wrapping untouched ────

    private static final Path[] ALL_FIVE_CHAT_MIXINS = {
            CHAT_COMPONENT_MIXIN, CHAT_SCREEN_MIXIN, CHAT_SCREEN_BACKGROUND_MIXIN, SUGGESTIONS_LIST_MIXIN, SUGGESTIONS_USAGE_MIXIN
    };

    @Test
    void noChatMixinTouchesWidthOpacityHistoryOrWrappingBehavior() throws Exception {
        for (Path mixin : ALL_FIVE_CHAT_MIXINS) {
            String source = read(mixin);
            assertFalse(source.contains("chatWidth"), mixin + " must not touch chat width");
            assertFalse(source.contains("getBackgroundColor") || source.contains("Opacity"),
                    mixin + " must not touch chat/background opacity or recompute the background color");
            assertFalse(source.contains("MAX_CHAT_HISTORY"), mixin + " must not touch chat history size");
            assertFalse(source.contains("wrap") && !source.contains("javadoc"), mixin + " must not touch message wrapping");
        }
    }

    // Only a vertical (Y-anchor) shift is applied — no mixin modifies an X-coordinate constant
    // or otherwise repositions chat horizontally.
    @Test
    void allFiveMixinsOnlyShiftVerticalPosition() throws Exception {
        for (Path mixin : ALL_FIVE_CHAT_MIXINS) {
            String source = read(mixin);
            assertFalse(source.contains("ModifyArg"), mixin + " uses ModifyConstant on a single bottom-anchor literal, not an argument-list rewrite");
        }
    }

    // ── ChatScreenInputBackgroundMixin: input-background rectangle correction (final pass) ──

    // 1. The ChatScreen input background uses the shared shifted input origin.
    @Test
    void chatScreenBackgroundMixinTargetsExtractRenderStateWithBothYLiterals() throws Exception {
        String source = read(CHAT_SCREEN_BACKGROUND_MIXIN);
        assertTrue(source.contains("@Mixin(ChatScreen.class)"));
        assertTrue(source.contains(
                "method = \"extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V\""),
                "must target ChatScreen's own extractRenderState override, not Screen's inherited one");
        assertTrue(source.contains("@Constant(intValue = 14)"),
                "the background rectangle's top-margin literal (14) must be targeted");
        assertTrue(source.contains("@Constant(intValue = 2, ordinal = 2)"),
                "the background rectangle's bottom-margin literal (the THIRD occurrence of int 2 — the first two are the fill's horizontal X margins, which must stay untouched)");
        assertTrue(source.contains("return TotalityChatLayout.inputBackgroundTopMargin();"));
        assertTrue(source.contains("return TotalityChatLayout.inputBackgroundBottomMargin();"));
    }

    // 2. The original unshifted bottom-screen fill path is no longer active — confirmed by the
    // mixin containing no second, independent fill/background draw call of its own (ModifyConstant
    // redirects the existing single vanilla fill call in place; it cannot leave a second, still-
    // unshifted copy of that call active, since there is only ever one graphics.fill(...) call site
    // inside vanilla's extractRenderState for this rectangle).
    @Test
    void noSecondIndependentBackgroundFillIsIntroduced() throws Exception {
        String source = read(CHAT_SCREEN_BACKGROUND_MIXIN);
        String topHandlerBody = methodBody(source,
                "private int totality$raiseInputBackgroundTop(int original) {", "@ModifyConstant");
        String bottomHandlerBody = methodBody(source,
                "private int totality$raiseInputBackgroundBottom(int original) {", "}\n}");
        assertFalse(topHandlerBody.contains(".fill("),
                "the top-margin handler must only return a replacement constant, never draw its own fill");
        assertFalse(bottomHandlerBody.contains(".fill("),
                "the bottom-margin handler must only return a replacement constant, never draw its own fill");
        assertFalse(source.contains("@Inject"), "must not inject a second render call — @ModifyConstant alone is sufficient and cannot create a duplicate draw");
    }

    // 3. The background rectangle and EditBox use matching top/bottom geometry — both margins are
    // derived from inputBottomMargin(), the exact same origin ChatScreenInputPositionMixin already
    // returns for the EditBox itself, so they cannot drift apart.
    @Test
    void backgroundMarginsDeriveFromTheSameInputOriginAsTheEditBox() throws Exception {
        String chatLayoutSource = read(CHAT_LAYOUT);
        String topBody = methodBody(chatLayoutSource,
                "public static int inputBackgroundTopMargin() {", "public static int inputBackgroundBottomMargin()");
        assertTrue(topBody.contains("inputBottomMargin()"),
                "the background's top margin must derive from the same shared origin as the EditBox");
        String bottomBody = methodBody(chatLayoutSource,
                "public static int inputBackgroundBottomMargin() {", "}\n}");
        assertTrue(bottomBody.contains("inputBottomMargin()"),
                "the background's bottom margin must derive from the same shared origin as the EditBox");
    }

    // 4. The chat reservation is applied exactly once — neither background margin method calls
    // extraBottomReservation() directly; both can only reach it via inputBottomMargin().
    @Test
    void backgroundMarginsDoNotCallExtraBottomReservationDirectly() throws Exception {
        String chatLayoutSource = read(CHAT_LAYOUT);
        String topBody = methodBody(chatLayoutSource,
                "public static int inputBackgroundTopMargin() {", "public static int inputBackgroundBottomMargin()");
        String bottomBody = methodBody(chatLayoutSource,
                "public static int inputBackgroundBottomMargin() {", "}\n}");
        assertFalse(topBody.contains("extraBottomReservation()"),
                "inputBackgroundTopMargin() must not call extraBottomReservation() itself — only inputBottomMargin() may");
        assertFalse(bottomBody.contains("extraBottomReservation()"),
                "inputBackgroundBottomMargin() must not call extraBottomReservation() itself — only inputBottomMargin() may");
    }

    // 5. Message/history positioning is unchanged by this pass — ChatComponentBottomMarginMixin's
    // target/constant/return value are identical to before this correction.
    @Test
    void messagePositioningMixinIsUnaffectedByTheBackgroundCorrection() throws Exception {
        String source = read(CHAT_COMPONENT_MIXIN);
        assertTrue(source.contains("@Constant(intValue = 40)"));
        assertTrue(source.contains("return TotalityChatLayout.messageBottomMargin();"));
    }

    // 6. Command-suggestion positioning is unchanged by this pass.
    @Test
    void commandSuggestionMixinsAreUnaffectedByTheBackgroundCorrection() throws Exception {
        String listSource = read(SUGGESTIONS_LIST_MIXIN);
        assertTrue(listSource.contains("return TotalityChatLayout.inputBottomMargin();"));
        String usageSource = read(SUGGESTIONS_USAGE_MIXIN);
        assertTrue(usageSource.contains("return TotalityChatLayout.usageHintBottomMargin();"));
    }

    // 7. Hover/click coordinate handling is unchanged — the background mixin only targets the
    // purely-visual extractRenderState method, never mouseClicked/mouseScrolled/hit-testing.
    @Test
    void backgroundMixinNeverTouchesClickOrHoverHandling() throws Exception {
        String source = read(CHAT_SCREEN_BACKGROUND_MIXIN);
        assertFalse(source.contains("mouseClicked"));
        assertFalse(source.contains("mouseScrolled"));
        assertFalse(source.contains("keyPressed"));
        assertFalse(source.contains("handleComponentClicked"));
    }

    // 9. HUD geometry and Resource behavior are untouched — this mixin/helper addition never
    // references HudBarLayout or TotalityHudRenderer, only TotalityChatLayout.
    @Test
    void backgroundMixinNeverReferencesHudGeometryOrResourceClasses() throws Exception {
        String source = read(CHAT_SCREEN_BACKGROUND_MIXIN);
        assertFalse(source.contains("HudBarLayout"));
        assertFalse(source.contains("TotalityHudRenderer"));
        assertFalse(source.contains("PlayerResourceService"));
        assertFalse(source.contains("ClientResourcePresentationResolver"));
    }
}
