package zcylas.totality.api.rpg.resources.client.presentation;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the Phase 3C client-presentation-consumer migration. Every test
 * here is a source-text sentinel, not runtime proof: the migrated consumers ({@code
 * TotalityHudRenderer}, {@code TotalityClient}, {@code ClassTab}, {@code SpellRadialScreen}, {@code
 * OverviewTab}) are client HUD/screen render methods that need a bootstrapped, GL-initialized
 * {@code Minecraft}/{@code Font} to execute — unavailable under plain JUnit, the same constraint
 * documented by {@code TooltipApiFoundationSourceRegressionTest} and
 * {@code CombatRollNotificationSourceRegressionTest} elsewhere in this suite. Runtime confirmation
 * for these is the manual in-client validation recorded in the Phase 3C implementation report.
 * {@link ClientResourcePresentationResolverTest} covers the actual resolution/fallback logic these
 * consumers all route through with real, executing tests.
 */
class Phase3CConsumerMigrationSourceRegressionTest {

    private static final Path HUD_RENDERER =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/TotalityHudRenderer.java");
    private static final Path TOTALITY_CLIENT =
            Path.of("src/main/java/zcylas/totality/TotalityClient.java");
    private static final Path CLASS_TAB =
            Path.of("src/main/java/zcylas/totality/screen/character/tabs/ClassTab.java");
    private static final Path SPELL_RADIAL_SCREEN =
            Path.of("src/main/java/zcylas/totality/screen/ability/SpellRadialScreen.java");
    private static final Path OVERVIEW_TAB =
            Path.of("src/main/java/zcylas/totality/screen/character/tabs/OverviewTab.java");
    private static final Path MOVEMENT_HANDLER =
            Path.of("src/main/java/zcylas/totality/init/TotalityMovementHandler.java");
    private static final Path MOB_HEALTH_BAR_HUD =
            Path.of("src/main/java/zcylas/totality/client/renderer/hud/MobHealthBarHud.java");

    private static final Path CLIENT_MANA_MANAGER =
            Path.of("src/main/java/zcylas/totality/networking/mana/ClientManaManager.java");
    private static final Path CLIENT_STAMINA_MANAGER =
            Path.of("src/main/java/zcylas/totality/networking/stamina/ClientStaminaManager.java");
    private static final Path CLIENT_SPELL_SLOT_MANAGER =
            Path.of("src/main/java/zcylas/totality/api/magic/spell/ClientSpellSlotManager.java");
    private static final Path SYNC_MANA_PAYLOAD =
            Path.of("src/main/java/zcylas/totality/networking/mana/SyncManaPayload.java");
    private static final Path SYNC_STAMINA_PAYLOAD =
            Path.of("src/main/java/zcylas/totality/networking/stamina/SyncStaminaPayload.java");
    private static final Path PLAYER_CHARGES_COMPONENT =
            Path.of("src/main/java/zcylas/totality/api/rpg/classes/PlayerChargesComponent.java");

    private static final Path PARITY_COORDINATOR =
            Path.of("src/main/java/zcylas/totality/client/resource/parity/ClientResourceParityCoordinator.java");
    private static final Path PARITY_TRACKER =
            Path.of("src/main/java/zcylas/totality/api/rpg/resources/client/parity/ClientResourceParityTracker.java");
    private static final Path PARITY_INSPECTION_COMMAND =
            Path.of("src/main/java/zcylas/totality/client/resource/parity/ClientResourceParityInspectionCommand.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── 9/10: HUD Mana + Stamina use the Generic resolver ───────────────────────────────────

    @Test
    void hudRendererUsesResolverForManaAndStamina() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.MANA"),
                "TotalityHudRenderer must resolve Mana through the shared Phase 3C presentation resolver");
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.STAMINA"),
                "TotalityHudRenderer must resolve Stamina through the shared Phase 3C presentation resolver");
    }

    // 13/14: Health and Food remain native-backed — never routed through the new resolver.
    @Test
    void hudRendererDoesNotRouteHealthOrFoodThroughTheGenericPresentationResolver() throws Exception {
        String source = read(HUD_RENDERER);
        assertFalse(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.HEALTH"),
                "Health must stay native-backed (client.player.getHealth()), not migrated in Phase 3C");
        assertFalse(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.FOOD"),
                "Food must stay native-backed, not migrated in Phase 3C");
        assertTrue(source.contains("client.player.getHealth()"), "Health must still read the native player field directly");
        assertTrue(source.contains("client.player.getFoodData().getFoodLevel()"),
                "Food must still read the native FoodData directly");
    }

    @Test
    void hudRendererGeometryReflectsTheContainedCleanupPassNotThePreCleanupConstants() throws Exception {
        // Superseded by the contained HUD cleanup pass (see
        // TOTALITY_SMALL_HUD_CLEANUP_IMPLEMENTATION_REPORT.md): this test previously asserted the
        // pre-cleanup 83x8/3/2 constants as proof the cleanup pass had not started. That is no
        // longer the current state of the repository, so asserting it here would be a stale,
        // misleading sentinel — the active bar geometry has moved to the dedicated HudBarLayout
        // pure helper class (HudBarLayoutTest covers its formulas with real, executing tests).
        // This sentinel instead confirms TotalityHudRenderer's active render path was actually
        // migrated to reference HudBarLayout rather than any hardcoded geometry of its own.
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("HudBarLayout.leftX()"));
        assertTrue(source.contains("HudBarLayout.staminaY(screenH)"));
        assertTrue(source.contains("HudBarLayout.manaY(screenH)"));
        assertTrue(source.contains("HudBarLayout.healthY(screenH)"));
        assertTrue(source.contains("HudBarLayout.rightX(screenW)"));
        assertTrue(source.contains("HudBarLayout.secondaryResourceY(screenH)"));
    }

    // The pre-cleanup 83x8 constants (BG_WIDTH/BG_HEIGHT/BAR_SPACING/BOTTOM_MARGIN) deliberately
    // still exist in TotalityHudRenderer.java, unchanged — but only as dead code feeding the
    // dormant drawBar/drawBarMirrored methods (never called in production; superseded by
    // drawBarSmooth/drawBarMirroredSmooth), per the cleanup task's explicit "do not remove
    // dormant code" instruction. Their continued presence is not evidence the cleanup pass hasn't
    // happened — see the test above for the actual active-geometry proof.
    @Test
    void preCleanupConstantsSurviveOnlyAsDeadCodeFeedingTheDormantDrawMethods() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("private static final int BG_WIDTH      = 83;"));
        assertTrue(source.contains("private static final int BG_HEIGHT     = 8;"));
        int drawBarStart = source.indexOf("private static void drawBar(");
        int drawBarMirroredEnd = source.indexOf("private static void drawSecondaryResources(");
        assertTrue(drawBarStart >= 0 && drawBarMirroredEnd > drawBarStart,
                "expected the dormant drawBar/drawBarMirrored methods to still exist between drawBarMirroredSmooth and drawSecondaryResources");
        String dormantBlock = source.substring(drawBarStart, drawBarMirroredEnd);
        assertTrue(dormantBlock.contains("BG_WIDTH") && dormantBlock.contains("BG_HEIGHT"),
                "the dormant methods must be the sole remaining reference to the pre-cleanup constants");
    }

    // ── 11/12: OverviewTab Mana + Stamina use the Generic resolver; Health stays native ─────

    @Test
    void overviewTabUsesResolverForManaAndStaminaButNotHealth() throws Exception {
        String source = read(OVERVIEW_TAB);
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.MANA"));
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.STAMINA"));
        assertFalse(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.HEALTH"),
                "OverviewTab Health must remain native-backed via RpgDisplayUtils.toDisplayHp");
        assertTrue(source.contains("RpgDisplayUtils.toDisplayHp(player.getHealth())"));
    }

    // ── 16/17/18/20: Rage secondary HUD + ClassTab panel ─────────────────────────────────────

    @Test
    void totalityClientRageSecondaryHudUsesResolverWithLegacyFallback() throws Exception {
        String source = read(TOTALITY_CLIENT);
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE"),
                "the Rage ISecondaryResource must resolve through the shared Phase 3C presentation resolver");
        assertTrue(source.contains("legacyRageCurrent(client)") && source.contains("legacyRageMax(client)"),
                "the Rage resolver call must pass the legacy PlayerChargesComponent readers as its fallback");
        // 20. Non-Barbarian visibility behavior remains unchanged — the exact pre-existing gate.
        assertTrue(source.contains("ClientClassManager.hasClass()")
                        && source.contains("TotalityClasses.BARBARIAN_ID.equals("),
                "shouldShow's Barbarian-only visibility gate must be unchanged");
    }

    @Test
    void classTabRageUsesResolverWithLegacyFallback() throws Exception {
        String source = read(CLASS_TAB);
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolveScalar(PlayerResourceIds.RAGE"),
                "ClassTab's resource panel must resolve Rage through the shared Phase 3C presentation resolver");
        assertTrue(source.contains("ClassTab::legacyRageCurrent") && source.contains("ClassTab::legacyRageMax"),
                "the Rage resolver call must pass the legacy PlayerChargesComponent readers as its fallback");
        // Layout/labels preserved.
        assertTrue(source.contains("\"BARBARIAN RAGE\""));
        assertTrue(source.contains("\"No resource\""));
    }

    // ── 22-30 (production-consumer half): SpellRadialScreen partition wiring ────────────────

    @Test
    void spellRadialScreenUsesPartitionedResolverKeyedBySpellLevel() throws Exception {
        String source = read(SPELL_RADIAL_SCREEN);
        assertTrue(source.contains("ClientResourcePresentationResolver.INSTANCE.resolvePartition(PlayerResourceIds.SPELL_SLOTS, level"),
                "the slot indicator must query the partitioned resolver keyed by the spell's level (1-10)");
        assertTrue(source.contains("ClientSpellSlotManager.getRemaining(level)") && source.contains("ClientSpellSlotManager.getMax(level)"),
                "the legacy ClientSpellSlotManager reads must remain as the fallback suppliers");
        assertTrue(source.contains("spell.isCantrip()"),
                "cantrip short-circuit (infinity symbol, no slot query) must be unchanged");
    }

    @Test
    void spellRadialScreenPresentationPathNeverCallsSpellSlotComponentOrSelectSpellMutation() throws Exception {
        String source = read(SPELL_RADIAL_SCREEN);
        assertFalse(source.contains("SpellSlotComponent."),
                "the presentation path must never call the server-side SpellSlotComponent directly");
        // The cast/spend path is untouched — selection still goes through the pre-existing
        // ClientSelectedSpellManager + SelectSpellPayload wiring, unrelated to slot presentation.
        assertTrue(source.contains("ClientSelectedSpellManager.setSelectedSpell"));
        assertTrue(source.contains("new SelectSpellPayload(id)"));
    }

    // ── 31/32: migrated UI consumers do not access sync internals directly ──────────────────

    @Test
    void migratedConsumersDoNotReferenceClientResourceSyncStateOrSyncManagerDirectly() throws Exception {
        // TotalityClient is excluded from this whole-file loop: it is the mod's top-level client
        // initializer and, independent of the Phase 3C Rage migration in registerRenderers(), it
        // already legitimately drives ClientResourceSyncManager.tick()/clear() as pre-existing
        // Phase 3A/3B-1 lifecycle wiring (JOIN/DISCONNECT/dimension-change/END_CLIENT_TICK). The
        // dedicated totalityClientRageMigrationBlockDoesNotReferenceSyncInternals test below scopes
        // the check to just the migrated Rage block instead.
        for (Path path : new Path[] {HUD_RENDERER, CLASS_TAB, SPELL_RADIAL_SCREEN, OVERVIEW_TAB}) {
            String source = read(path);
            assertFalse(source.contains("ClientResourceSyncState"),
                    path + " must not reference ClientResourceSyncState directly");
            assertFalse(source.contains("ClientResourceSyncManager"),
                    path + " must not reference ClientResourceSyncManager directly");
        }
    }

    @Test
    void totalityClientRageMigrationBlockDoesNotReferenceSyncInternals() throws Exception {
        String source = read(TOTALITY_CLIENT);
        int blockStart = source.indexOf("SecondaryResourceRegistry.register(new ISecondaryResource()");
        assertTrue(blockStart >= 0, "expected to find the Rage ISecondaryResource registration block");
        int blockEnd = source.indexOf("/** Legacy Rage fallback reader");
        assertTrue(blockEnd > blockStart);
        int helpersEnd = source.indexOf("private static int legacyRageMax", blockEnd);
        int helpersMethodEnd = source.indexOf('}', source.indexOf('{', helpersEnd));
        String migratedBlock = source.substring(blockStart, helpersMethodEnd);

        assertFalse(migratedBlock.contains("ClientResourceSyncState"));
        assertFalse(migratedBlock.contains("ClientResourceSyncManager"));
        assertFalse(migratedBlock.contains(".applyFull(") || migratedBlock.contains(".applyDelta("));
    }

    // ── 33: no Resource mutation call in renderer/UI code ────────────────────────────────────

    @Test
    void migratedConsumersContainNoResourceMutationCall() throws Exception {
        for (Path path : new Path[] {HUD_RENDERER, TOTALITY_CLIENT, CLASS_TAB, SPELL_RADIAL_SCREEN, OVERVIEW_TAB}) {
            String source = read(path);
            assertFalse(source.contains(".applyFull(") || source.contains(".applyDelta("),
                    path + " must not apply a Resource sync payload from presentation code");
            assertFalse(source.contains("PlayerResourceService.INSTANCE.spend") || source.contains("PlayerResourceService.INSTANCE.restore"),
                    path + " must not call a Resource-spending/restoring service method");
        }
    }

    @Test
    void presentationResolverItselfExposesNoMutatingMethod() throws Exception {
        String source = read(Path.of(
                "src/main/java/zcylas/totality/api/rpg/resources/client/presentation/ClientResourcePresentationResolver.java"));
        assertFalse(source.contains("void apply") || source.contains("void mutate") || source.contains("void spend")
                        || source.contains("void restore") || source.contains("void set"),
                "ClientResourcePresentationResolver must expose no mutating method");
    }

    // ── 34/35: legacy managers, packets, and parity tooling still exist ─────────────────────

    @Test
    void legacyManagersAndPacketsStillExist() {
        for (Path path : new Path[] {
                CLIENT_MANA_MANAGER, CLIENT_STAMINA_MANAGER, CLIENT_SPELL_SLOT_MANAGER,
                SYNC_MANA_PAYLOAD, SYNC_STAMINA_PAYLOAD, PLAYER_CHARGES_COMPONENT}) {
            assertTrue(Files.exists(path), "expected legacy file to still exist: " + path);
        }
    }

    @Test
    void parityTrackerCoordinatorAndDebugCommandStillExist() {
        for (Path path : new Path[] {PARITY_COORDINATOR, PARITY_TRACKER, PARITY_INSPECTION_COMMAND}) {
            assertTrue(Files.exists(path), "expected parity infrastructure to still exist: " + path);
        }
    }

    // ── 36: TotalityMovementHandler stays outside the presentation resolver ─────────────────

    @Test
    void movementHandlerDoesNotImportOrUseThePresentationResolver() throws Exception {
        String source = read(MOVEMENT_HANDLER);
        assertFalse(source.contains("ClientResourcePresentationResolver"),
                "TotalityMovementHandler's Stamina gates must remain untouched by the Phase 3C presentation resolver");
        assertFalse(source.contains("ClientResourceService"),
                "TotalityMovementHandler must not import the client Resource façade at all");
        assertTrue(source.contains("ClientStaminaManager.getStamina()"),
                "TotalityMovementHandler must keep reading the legacy Stamina manager directly for gameplay gating");
    }

    // ── MobHealthBarHud stays unrelated to the player Resource API ──────────────────────────

    @Test
    void mobHealthBarHudRemainsUnrelatedToThePlayerResourceApi() throws Exception {
        String source = read(MOB_HEALTH_BAR_HUD);
        assertFalse(source.contains("PlayerResourceService"));
        assertFalse(source.contains("ClientResourceService"));
        assertFalse(source.contains("ClientResourcePresentationResolver"));
    }

    // ── 37: no Food 0-100 implementation appears ─────────────────────────────────────────────

    @Test
    void hudRendererFoodBarStillUsesTheNativeZeroToTwentyRatio() throws Exception {
        String source = read(HUD_RENDERER);
        assertTrue(source.contains("hunger / 20.0"),
                "Food's bar-fill ratio must remain the native 0-20 scale — Phase 3C does not begin the Food 0-100 migration");
    }

    // ── 39: no Tooltip API file touched by this pass ─────────────────────────────────────────

    @Test
    void noMigratedConsumerImportsTheTooltipApi() throws Exception {
        // TotalityClient is deliberately excluded: it already imports
        // zcylas.totality.client.tooltip.TooltipScrollController for unrelated, pre-existing scroll
        // wiring, untouched by this pass — see the class-level Javadoc for why the whole-file loop
        // above also excludes it.
        for (Path path : new Path[] {HUD_RENDERER, CLASS_TAB, SPELL_RADIAL_SCREEN, OVERVIEW_TAB,
                Path.of("src/main/java/zcylas/totality/api/rpg/resources/client/presentation/ClientResourcePresentationResolver.java")}) {
            String source = read(path);
            assertFalse(source.contains("zcylas.totality.client.tooltip"),
                    path + " must not import the Tooltip API — Phase 3C never touches Tooltip behavior");
        }
    }
}
