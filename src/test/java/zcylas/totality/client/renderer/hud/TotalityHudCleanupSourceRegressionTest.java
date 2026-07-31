package zcylas.totality.client.renderer.hud;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the contained HUD cleanup pass (main-bar geometry/embedded text
 * in {@code TotalityHudRenderer}, and the temporary direct-look Mob Display HP visibility change in
 * {@code MobHealthBarHud}) — see {@code TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md}.
 *
 * <p><b>Evidentiary limits:</b> every test in this file is a source-text regression sentinel, not
 * runtime proof. Both classes under test are client HUD render methods requiring a bootstrapped,
 * GL-initialized {@code Minecraft}/{@code Font} to execute — unavailable under plain JUnit, the
 * same constraint documented by {@code Phase3CConsumerMigrationSourceRegressionTest} and every
 * other {@code *SourceRegressionTest} in this suite. {@link HudBarLayoutTest} and
 * {@link HudValueFormatterTest} cover the actual geometry/formatting math with real, executing
 * tests; runtime confirmation of the rendered result is the manual in-client validation recorded
 * in the implementation report.
 */
class TotalityHudCleanupSourceRegressionTest {

    private static final Path HUD_RENDERER =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java");
    private static final Path MOB_HEALTH_BAR_HUD =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java");

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

    // ── TotalityHudRenderer: main-bar geometry/embedded-text sentinels ──────────────────────

    // 9. Food remains mirrored right-to-left. 10. Health/Mana/Stamina remain left-to-right.
    @Test
    void healthManaStaminaUseTheLeftAnchoredDrawWhileFoodUsesTheMirroredDraw() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("drawBarSmooth(graphics, client, leftX, hpY,"),
                "Health must still use the left-anchored (left-to-right fill) draw helper");
        assertTrue(source.contains("drawBarSmooth(graphics, client, leftX, staminaY,"),
                "Stamina must still use the left-anchored draw helper");
        assertTrue(source.contains("drawBarSmooth(graphics, client, leftX, manaY,"),
                "Mana must still use the left-anchored draw helper");
        assertTrue(source.contains("drawBarMirroredSmooth(graphics, client, rightX, hpY,"),
                "Food must still use the mirrored (right-to-left fill) draw helper");
    }

    // 11. Rage's own pip size/spacing are unchanged by the Food-geometry-following Y formula.
    @Test
    void secondaryResourcePipSizeAndGapAreUnchanged() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("int pipSz  = 10;"));
        assertTrue(source.contains("int pipGap = 2;"));
        assertTrue(source.contains("HudBarLayout.secondaryResourceY(screenH)"),
                "the secondary-resource row's Y must now derive from HudBarLayout's Food-following formula");
    }

    // 12. No external value placement remains to the right of the left bars (in the active
    // drawBarSmooth method specifically — the dormant drawBar still has the old pattern and must
    // not be mistaken for the active path).
    @Test
    void activeLeftBarDrawMethodNoLongerPlacesTextExternalToTheFrame() throws Exception {
        String source = read(HUD_RENDERER);
        String drawBarSmoothBody = methodBody(source,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        assertFalse(drawBarSmoothBody.contains("x + BG_WIDTH + 4"),
                "the active drawBarSmooth must no longer use the old external-text formula");
        assertTrue(drawBarSmoothBody.contains("HudBarLayout.textX("),
                "the active drawBarSmooth must place text via the embedded-text formula");
    }

    // 13. No external value placement remains to the left of Food (active drawBarMirroredSmooth).
    @Test
    void activeFoodDrawMethodNoLongerPlacesTextExternalToTheFrame() throws Exception {
        String source = read(HUD_RENDERER);
        String drawBarMirroredSmoothBody = methodBody(source,
                "private static void drawBarMirroredSmooth(", "private static void drawBar(");
        assertFalse(drawBarMirroredSmoothBody.contains("x - textW - 4"),
                "the active drawBarMirroredSmooth must no longer use the old external-text formula");
        assertTrue(drawBarMirroredSmoothBody.contains("HudBarLayout.textX("),
                "the active drawBarMirroredSmooth must place text via the embedded-text formula");
    }

    // 1/2. Frame is 96x12; interior is 94x10. No dynamic frame growth occurs — the approved
    // geometry is fixed named constants, not computed from text length.
    @Test
    void frameAndInteriorDimensionsAreFixedConstantsNotComputedFromTextLength() throws Exception {
        String layoutSource = read(Path.of("src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java"));
        assertTrue(layoutSource.contains("static final int FRAME_WIDTH  = 96;"));
        assertTrue(layoutSource.contains("static final int FRAME_HEIGHT = 12;"));
        assertTrue(layoutSource.contains("static final int INTERIOR_WIDTH  = 94;"));
        assertTrue(layoutSource.contains("static final int INTERIOR_HEIGHT = 10;"));

        String hudSource = read(HUD_RENDERER);
        String drawBarSmoothBody = methodBody(hudSource,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        assertFalse(drawBarSmoothBody.contains(".width(text) *"),
                "the frame must never be resized based on the measured text width");
    }

    // 3/4. The interior is offset by a genuine 1px border on both axes (96-1-1=94, 12-1-1=10) —
    // the old symmetric 3px track inset (with its separate charcoal inner-frame panel) is gone.
    @Test
    void interiorOffsetIsOnePixelBorderOnBothAxes() throws Exception {
        String layoutSource = read(Path.of("src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java"));
        assertTrue(layoutSource.contains("static final int BORDER_WIDTH = 1;"));
        assertTrue(layoutSource.contains("static final int INTERIOR_OFFSET_X = 1;"));
        assertTrue(layoutSource.contains("static final int INTERIOR_OFFSET_Y = 1;"));
        assertFalse(layoutSource.contains("static final int TRACK_INSET_X"),
                "the old 3px symmetric track inset must be fully removed");
        assertFalse(layoutSource.contains("static final int TRACK_INSET_Y"),
                "the old 3px symmetric track inset must be fully removed");
        assertFalse(layoutSource.contains("static final int INNER_FRAME_WIDTH"),
                "the old separate charcoal inner-frame ring width must be fully removed");
    }

    // 26. No font scaling occurs.
    @Test
    void activeBarDrawMethodsDoNotScaleTheFont() throws Exception {
        String source = read(HUD_RENDERER);
        String drawBarSmoothBody = methodBody(source,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        String drawBarMirroredSmoothBody = methodBody(source,
                "private static void drawBarMirroredSmooth(", "private static void drawBar(");
        assertFalse(drawBarSmoothBody.contains(".scale("));
        assertFalse(drawBarMirroredSmoothBody.contains(".scale("));
    }

    // Embedded text uses HudValueFormatter (long-safe) rather than the old int-based buildText in
    // the active draw path — the dormant buildText/formatValue survive untouched for the dormant
    // drawBar/drawBarMirrored methods only.
    @Test
    void activeBarDrawMethodsUseTheLongSafeValueFormatterNotTheOldIntBasedBuildText() throws Exception {
        String source = read(HUD_RENDERER);
        String drawBarSmoothBody = methodBody(source,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        String drawBarMirroredSmoothBody = methodBody(source,
                "private static void drawBarMirroredSmooth(", "private static void drawBar(");
        assertTrue(drawBarSmoothBody.contains("HudValueFormatter.display("));
        assertTrue(drawBarMirroredSmoothBody.contains("HudValueFormatter.display("));
        assertFalse(drawBarSmoothBody.contains("buildText("));
        assertFalse(drawBarMirroredSmoothBody.contains("buildText("));
    }

    // Resource presentation values are not narrowed to int merely for HUD formatting.
    @Test
    void resolverValuesStayLongThroughTheActiveDrawPath() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("long stamina    = staminaView.current();"));
        assertTrue(source.contains("long maxStamina = staminaView.maximum();"));
        assertTrue(source.contains("long mana    = manaView.current();"));
        assertTrue(source.contains("long maxMana = manaView.maximum();"));
        // Two independent assertions rather than one combined assertFalse(A && B): a combined
        // check only fails if BOTH regress, silently missing a regression in just one of the two
        // (review-correction pass finding).
        assertFalse(source.contains("(int) staminaView"),
                "stamina must no longer be cast to int immediately after resolution");
        assertFalse(source.contains("(int) manaView"),
                "mana must no longer be cast to int immediately after resolution");
        String drawBarSmoothSignature = "long displayCurrent, long displayMax";
        assertTrue(source.contains(drawBarSmoothSignature),
                "the active draw helpers must take long current/max, not int");
    }

    // ── Review-correction pass: code-drawn bars, no sprite stretching ───────────────────────

    // The enlarged bars must no longer blitSprite the original (now-too-small) frame/fill sprites
    // — they are drawn in code instead.
    @Test
    void activeBarDrawMethodsNoLongerBlitSpriteTheFrameOrFill() throws Exception {
        String source = read(HUD_RENDERER);
        String drawBarSmoothBody = methodBody(source,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        String drawBarMirroredSmoothBody = methodBody(source,
                "private static void drawBarMirroredSmooth(", "private static void drawBar(");
        assertFalse(drawBarSmoothBody.contains("blitSprite"),
                "the active drawBarSmooth must no longer blitSprite the frame/fill");
        assertFalse(drawBarMirroredSmoothBody.contains("blitSprite"),
                "the active drawBarMirroredSmooth must no longer blitSprite the frame/fill");
    }

    // The active path draws a layered border/interior via GuiHelper.fillFrame + graphics.fill(...)
    // and the resource-colored fill via graphics.fill(...) — confirms a real replacement exists,
    // not just a sprite-call removal. Final player-HUD and vanilla-chat compatibility correction:
    // drawBarFrame is down to two layers (a genuine 1px border + one shared interior track, no
    // separate charcoal panel); drawBarFill still takes explicit highlight/shadow colors instead
    // of deriving them via ColorUtils.blend at render time.
    @Test
    void activeBarDrawMethodsUseCodeDrawnFrameAndFillHelpers() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static void drawBarFrame(GuiGraphicsExtractor graphics, int x, int y) {"));
        assertTrue(source.contains("GuiHelper.fillFrame(graphics, x, y, HudBarLayout.FRAME_WIDTH, HudBarLayout.FRAME_HEIGHT,"));
        assertTrue(source.contains("HudBarLayout.BORDER_WIDTH, OUTER_BORDER_COLOR);"));
        assertTrue(source.contains("private static void drawBarFill(GuiGraphicsExtractor graphics, int fillX, int fillY, int fillW, int fillH,"));
        assertTrue(source.contains("int fillColor, int highlightColor, int shadowColor) {"));
        // No runtime blend-toward-white highlight computation remains — colors are explicit constants.
        assertFalse(source.contains("ColorUtils.blend(fillColor"),
                "highlight/shadow must be explicit named constants, not a runtime blend-toward-white/black");

        String drawBarSmoothBody = methodBody(source,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        String drawBarMirroredSmoothBody = methodBody(source,
                "private static void drawBarMirroredSmooth(", "private static void drawBar(");
        assertTrue(drawBarSmoothBody.contains("drawBarFrame(graphics, x, y);"));
        assertTrue(drawBarSmoothBody.contains("drawBarFill(graphics,"));
        assertTrue(drawBarMirroredSmoothBody.contains("drawBarFrame(graphics, x, y);"));
        assertTrue(drawBarMirroredSmoothBody.contains("drawBarFill(graphics,"));
    }

    // No new texture files are referenced by the active path — only named color constants, now
    // sampled directly from the original pre-cleanup sprite assets (final palette-correction pass)
    // rather than hand-picked/approximated, plus a shared neutral border/frame/track treatment.
    @Test
    void activeBarDrawMethodsUseNamedColorConstantsNotSpriteIdentifiers() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static final int OUTER_BORDER_COLOR = 0xFF030303;"));
        assertTrue(source.contains("private static final int TRACK_COLOR        = 0xFF2D2C2C;"));
        // The old separate charcoal inner-frame panel color is fully removed (final player-HUD
        // and vanilla-chat compatibility correction collapsed border+panel+track into border+track).
        assertFalse(source.contains("private static final int INNER_FRAME_COLOR"),
                "the old separate charcoal inner-frame panel color must be fully removed");
        assertTrue(source.contains("private static final int HEALTH_FILL_COLOR      = 0xFF720A0B;"));
        assertTrue(source.contains("private static final int MANA_FILL_COLOR      = 0xFF02234E;"));
        assertTrue(source.contains("private static final int STAMINA_FILL_COLOR      = 0xFF133203;"));
        assertTrue(source.contains("private static final int FOOD_FILL_COLOR      = 0xFF482601;"));
        assertTrue(source.contains("private static final int HEALTH_HIGHLIGHT_COLOR"));
        assertTrue(source.contains("private static final int HEALTH_SHADOW_COLOR"));
        assertTrue(source.contains("private static final int MANA_HIGHLIGHT_COLOR"));
        assertTrue(source.contains("private static final int MANA_SHADOW_COLOR"));
        assertTrue(source.contains("private static final int STAMINA_HIGHLIGHT_COLOR"));
        assertTrue(source.contains("private static final int STAMINA_SHADOW_COLOR"));
        assertTrue(source.contains("private static final int FOOD_HIGHLIGHT_COLOR"));
        assertTrue(source.contains("private static final int FOOD_SHADOW_COLOR"));
        // Confirms the named color constants are actually passed (3 colors each) at the
        // Health/Food call sites (paired with
        // healthManaStaminaUseTheLeftAnchoredDrawWhileFoodUsesTheMirroredDraw, which already
        // confirms those are the correct drawBarSmooth/drawBarMirroredSmooth call sites).
        assertTrue(source.contains("HEALTH_FILL_COLOR, HEALTH_HIGHLIGHT_COLOR, HEALTH_SHADOW_COLOR,"));
        assertTrue(source.contains("FOOD_FILL_COLOR, FOOD_HIGHLIGHT_COLOR, FOOD_SHADOW_COLOR,"));
    }

    // Old bright/flat fill colors (from the first code-drawn attempt) are gone.
    @Test
    void oldBrightFlatFillColorsAreNoLongerPresent() throws Exception {
        String source = read(HUD_RENDERER);
        assertFalse(source.contains("0xFFCC3333"), "the old bright HEALTH_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFF4466CC"), "the old bright MANA_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFF44AA44"), "the old bright STAMINA_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFFCC8833"), "the old bright FOOD_FILL_COLOR must be gone");
    }

    // Final palette-correction pass: the SECOND-generation palette (darker than the very first
    // attempt, but still hand-picked/approximated rather than sampled from the original sprites)
    // is also gone, replaced by the sampled constants asserted above.
    @Test
    void oldApproximatedDarkerPaletteIsNoLongerPresent() throws Exception {
        String source = read(HUD_RENDERER);
        assertFalse(source.contains("0xFF0A0A0A"), "the old approximated OUTER_BORDER_COLOR must be gone");
        assertFalse(source.contains("0xFF161616"), "the old approximated TRACK_COLOR must be gone");
        assertFalse(source.contains("0xFF7A2424"), "the old approximated HEALTH_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFF9C3A3A"), "the old approximated HEALTH_HIGHLIGHT_COLOR must be gone");
        assertFalse(source.contains("0xFF4A1414"), "the old approximated HEALTH_SHADOW_COLOR must be gone");
        assertFalse(source.contains("0xFF28468A"), "the old approximated MANA_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFF3C5CA8"), "the old approximated MANA_HIGHLIGHT_COLOR must be gone");
        assertFalse(source.contains("0xFF162A55"), "the old approximated MANA_SHADOW_COLOR must be gone");
        assertFalse(source.contains("0xFF2E6E2E"), "the old approximated STAMINA_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFF44904A"), "the old approximated STAMINA_HIGHLIGHT_COLOR must be gone");
        assertFalse(source.contains("0xFF193A19"), "the old approximated STAMINA_SHADOW_COLOR must be gone");
        assertFalse(source.contains("0xFF8A5A28"), "the old approximated FOOD_FILL_COLOR must be gone");
        assertFalse(source.contains("0xFFA8753C"), "the old approximated FOOD_HIGHLIGHT_COLOR must be gone");
        assertFalse(source.contains("0xFF553015"), "the old approximated FOOD_SHADOW_COLOR must be gone");
    }

    // The sampled palette's provenance (asset paths + sampling method) must be documented in the
    // source, not just present as bare hex constants — confirms this was a deliberate sampling
    // pass, not another round of hand-picking.
    @Test
    void sampledPaletteDocumentsItsAssetPathsAndSamplingMethod() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("bar_background.png"));
        assertTrue(source.contains("health_filled.png"));
        assertTrue(source.contains("mana_filled.png"));
        assertTrue(source.contains("stamina_filled.png"));
        assertTrue(source.contains("hunger_filled.png"));
        assertTrue(source.contains("sampled"),
                "the color-constant block must document that these values were sampled, not hand-picked");
        assertFalse(source.contains("Bitmap") || source.contains("BufferedImage") || source.contains("ImageIO"),
                "sampling must not happen at runtime — no image-reading API may appear in production code");
    }

    // Food's fill still grows from the track's right edge leftward (mirrored direction), just via
    // a plain-rectangle X anchor instead of a flipped sprite U coordinate.
    @Test
    void foodFillStillGrowsRightToLeftUnderTheCodeDrawnApproach() throws Exception {
        String source = read(HUD_RENDERER);
        String drawBarMirroredSmoothBody = methodBody(source,
                "private static void drawBarMirroredSmooth(", "private static void drawBar(");
        assertTrue(drawBarMirroredSmoothBody.contains(
                "int fillStartX = x + HudBarLayout.INTERIOR_OFFSET_X + (HudBarLayout.INTERIOR_WIDTH - filledW);"),
                "Food's fill rectangle must still start further right as less of it is filled (right-to-left growth)");
    }

    // 5. The old sprite-derived fill offset of 10 is no longer used anywhere by the active
    // code-drawn bar path — HudBarLayout no longer even declares it.
    @Test
    void oldFillOffsetConstantNoLongerExists() throws Exception {
        // Checks for the actual declaration pattern, not a bare substring — this class's own
        // javadoc mentions "FILL_WIDTH"/etc. by name (explaining they were removed), which a bare
        // .contains() would false-positive against.
        String layoutSource = read(Path.of("src/main/java/zcylas/totality/client/renderer/hud/HudBarLayout.java"));
        assertFalse(layoutSource.contains("static final int FILL_OFFSET_X"), "the old asymmetric sprite-derived fill offset must be fully removed");
        assertFalse(layoutSource.contains("static final int FILL_WIDTH"), "the old sprite-derived fill width must be fully removed");
        assertFalse(layoutSource.contains("static final int FILL_HEIGHT"), "the old sprite-derived fill height must be fully removed");
        String hudSource = read(HUD_RENDERER);
        String drawBarSmoothBody = methodBody(hudSource,
                "private static void drawBarSmooth(", "private static void drawBarMirroredSmooth(");
        assertFalse(drawBarSmoothBody.contains("HudBarLayout.FILL_OFFSET_X"));
    }

    // ── Final-visual-correction pass: AC relocated to a hotbar-relative X, not the left
    // player-bar stack ───────────────────────────────────────────────────────────────────────

    @Test
    void acNoLongerSitsAboveTheHealthBar() throws Exception {
        String source = read(HUD_RENDERER);
        assertFalse(source.contains("hpY - client.font.lineHeight - 2"),
                "AC must no longer be positioned relative to hpY (the old above-Health-stack spot that collided with chat)");
    }

    // 10. AC uses a hotbar-relative X formula. 11. AC is no longer anchored to the player-bar
    // left edge (leftX).
    @Test
    void acUsesAHotbarRelativeXFormulaInsteadOfThePlayerBarLeftEdge() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static final int VANILLA_HOTBAR_HALF_WIDTH = 91;"));
        assertTrue(source.contains("private static final int AC_HOTBAR_LEFT_INSET      = 2;"));
        assertTrue(source.contains("int hotbarLeft = screenW / 2 - VANILLA_HOTBAR_HALF_WIDTH;"),
                "AC's X must derive from the vanilla hotbar's own approximate left edge");
        assertTrue(source.contains("int acX = hotbarLeft + AC_HOTBAR_LEFT_INSET;"));
        assertTrue(source.contains("graphics.text(client.font, \"AC \" + ac, acX, acY, 0xFF00CCFF, true);"),
                "AC's value/color/shadow must be unchanged — only its X/Y source changed");
        // 11: leftX (the player-bar column's own anchor) must not appear anywhere near the AC draw.
        int acBlockStart = source.indexOf("int ac = calculateClientAC(client);");
        int acBlockEnd = source.indexOf("// ── RIGHT SIDE", acBlockStart);
        assertTrue(acBlockStart >= 0 && acBlockEnd > acBlockStart);
        String acBlock = source.substring(acBlockStart, acBlockEnd);
        assertFalse(acBlock.contains("leftX"), "AC must no longer reference leftX (the player-bar stack's own anchor)");
    }

    @Test
    void acStillUsesTheVanillaHotbarHeightReferencePointForItsYPosition() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static final int VANILLA_HOTBAR_HEIGHT     = 22;"));
        assertTrue(source.contains(
                "int acY = screenH - VANILLA_HOTBAR_HEIGHT - client.font.lineHeight - 2 - AC_ABOVE_XP_CLEARANCE;"),
                "AC must be positioned low on screen, just above the vanilla hotbar's own height, minus the extra clearance");
    }

    // Final player-HUD and vanilla-chat compatibility correction: AC still collided with chat at
    // its prior Y, so it moves up by a new named clearance constant.
    @Test
    void acUsesTheNewAboveXpClearanceConstant() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static final int AC_ABOVE_XP_CLEARANCE = 6;"));
    }

    // ── Review-correction pass: misleading crosshair alive-check comment fixed (wording only,
    // no behavior change) ────────────────────────────────────────────────────────────────────

    @Test
    void crosshairAliveCheckCommentNoLongerFalselyClaimsAnExplicitCheckExistsOnThatPath() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertFalse(source.contains("not a Player, within range, alive), so \"we are rendering at all\""),
                "the old wording falsely implied the crosshair path itself explicitly checked isAlive()");
        assertTrue(source.contains("explicit isAlive() check is only"),
                "the corrected comment must acknowledge isAlive() is only explicitly checked on the combatTarget branch");
        // Behavior is unchanged: still no isAlive() call anywhere in the crosshairTarget assignment itself.
        String registerBody = methodBody(source, "public static void register() {", "public static void tick()");
        String crosshairAssignment = registerBody.substring(
                registerBody.indexOf("crosshairTarget = "), registerBody.indexOf("LivingEntity target = getDisplayTarget"));
        assertFalse(crosshairAssignment.contains("isAlive()"),
                "this is a comment-only correction — the crosshairTarget path must still not call isAlive() itself");
    }

    // ── Final-visual-correction pass: Phase 3C / native-authority / no-mutation boundaries ──

    // 12. Phase 3C Mana/Stamina readers remain unchanged by this purely-visual pass.
    @Test
    void phase3CReadersRemainUnchanged() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.STAMINA,"));
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.MANA,"));
        assertTrue(source.contains("() -> ClientStaminaManager.getStamina(), () -> ClientStaminaManager.getMaxStamina());"));
        assertTrue(source.contains("() -> ClientManaManager.getMana(), () -> ClientManaManager.getMaxMana());"));
    }

    // 13. Native Health and Food remain unchanged: Health's fill still reads the native
    // getHealth()/getMaxHealth() ratio; Food's fill still reads the native 0-20 hunger ratio.
    @Test
    void nativeHealthAndFoodRemainUnchanged() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("float hp    = client.player.getHealth();"));
        assertTrue(source.contains("float maxHp = client.player.getMaxHealth();"));
        assertTrue(source.contains("double hpPct      = maxHp > 0 ? hp / maxHp : 0;"));
        assertTrue(source.contains("int hunger  = client.player.getFoodData().getFoodLevel();"));
        assertTrue(source.contains("double hungerPct  = hunger / 20.0;"),
                "Food's fill ratio must still be the native 0-20 scale — no Food 0-100 work has started");
    }

    // 14. No Resource mutation or packet sending appears anywhere in the render path.
    @Test
    void noResourceMutationOrPacketSendingAppearsInTheRenderPath() throws Exception {
        String source = read(HUD_RENDERER);
        assertFalse(source.contains(".applyFull(") || source.contains(".applyDelta("),
                "must not apply a Resource sync payload from presentation code");
        assertFalse(source.contains("ClientPlayNetworking.send("),
                "must not send a packet from presentation/rendering code");
        assertFalse(source.contains("PlayerResourceService.INSTANCE.spend") || source.contains("PlayerResourceService.INSTANCE.restore"),
                "must not call a Resource-spending/restoring service method");
    }

    // ── MobHealthBarHud: temporary direct-look HP visibility sentinels ──────────────────────

    // 37. Valid direct crosshair target can show HP without requiring combat.
    @Test
    void showHealthBarIsUnconditionalRegardlessOfCombatState() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("boolean showHealthBar = true;"),
                "HP-bar visibility must no longer be gated behind inCombat/perception");
    }

    // 38/39/40/41: neutral direct-look target does not newly expose combat-only details.
    @Test
    void combatOnlyGatesRemainExactlyAsBefore() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        // 38: name color stays gated (neutral direct-look keeps the neutral COLOR_UNKNOWN name).
        assertTrue(source.contains("boolean showNameColor = inCombat || perception >= 2;"));
        assertTrue(source.contains("int nameColor    = showNameColor ? getThreatColor(target, mobData) : COLOR_UNKNOWN;"));
        // 39: rank stays combat-only.
        assertTrue(source.contains("boolean showRank      = inCombat;"));
        // 41: AC stays combat-only.
        assertTrue(source.contains("boolean showAc        = inCombat;"));
    }

    // 40: rarity prefix behavior (already unconditional pre-cleanup, confirmed unchanged) is not
    // newly tied to showHealthBar.
    @Test
    void rarityPrefixLogicInBuildNameIsUnchanged() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("if (rarity != SpawnRarity.COMMON && rarity != SpawnRarity.UNCOMMON)"));
        assertFalse(source.contains("showHealthBar") && source.contains("rarity != SpawnRarity.COMMON &&") &&
                        methodBody(source, "private static String buildName(", "private static int getThreatColor(")
                                .contains("showHealthBar"),
                "buildName's rarity-prefix logic must not reference showHealthBar");
    }

    // 42: no new Level text was introduced.
    @Test
    void noLevelTextWasAddedToTheRenderMethod() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        String renderBody = methodBody(source, "private static void render(", "// ── Panel drawing");
        assertFalse(renderBody.contains("\"Level"), "render() must not draw a Level text string");
    }

    // 43. Existing combat target still shows combat information (inCombat unchanged).
    @Test
    void inCombatComputationIsUnchanged() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("boolean inCombat = isInCombat(target);"));
        assertTrue(source.contains("private static boolean isInCombat(LivingEntity target) {"));
    }

    // 44. Combat retention remains 80 ticks.
    @Test
    void combatDisplayTicksRemains80() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("private static final int COMBAT_DISPLAY_TICKS = 80; // 4 seconds"));
    }

    // 45. Players remain excluded from the normal crosshair Mob Display path.
    @Test
    void playersRemainExcludedFromTheCrosshairTargetPath() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("&& !(le instanceof Player)"));
    }

    // 46. Mob health remains native LivingEntity current/max.
    @Test
    void mobHealthRemainsNativeLivingEntityCurrentMax() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("target.getHealth() / target.getMaxHealth()"));
        assertTrue(source.contains("RpgDisplayUtils.toDisplayHp(target.getHealth())"));
        assertTrue(source.contains("RpgDisplayUtils.toDisplayHp(target.getMaxHealth())"));
    }

    // 47. No Mob Resource API is introduced.
    @Test
    void noMobResourceApiWasIntroduced() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertFalse(source.contains("ClientResourcePresentationResolver"));
        assertFalse(source.contains("ClientResourceService"));
        assertFalse(source.contains("PlayerResourceIds"));
    }

    // 48. No MobStats cache lifecycle behavior is changed.
    @Test
    void mobStatsCacheIsOnlyRead() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("MobStatsClientCache.get(target.getId())"));
        assertFalse(source.contains("MobStatsClientCache.update("));
        assertFalse(source.contains("MobStatsClientCache.clear("));
    }

    // 49. No Perception implementation is introduced — still the same stub.
    @Test
    void perceptionMasteryLevelRemainsStubbedToZero() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("private static int getPerceptionMasteryLevel() {\n        return 0; // TODO: read from ClientMasteryManager\n    }"));
    }

    // 50. No threat-tier redesign appears — still the same MobRank-based rank/color logic.
    @Test
    void noThreatTierRedesignWasIntroduced() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertTrue(source.contains("MobRank.values()["));
        for (String futureTierWord : new String[] {"Uncommon", "Legendary", "Mythical", "Ancient"}) {
            assertFalse(source.contains(futureTierWord),
                    "the future authored threat-tier redesign must not appear yet: found \"" + futureTierWord + "\"");
        }
    }
}
