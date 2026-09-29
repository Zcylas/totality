package zcylas.totality.api.rpg.resources;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source regression sentinels for the 2026-09-22 Food-command/Cake-world-mutation bugfix,
 * hardened in the same-day review-correction pass.
 *
 * <p><b>The bug:</b> {@code FoodSystemVerification}'s Cake self-test invoked the real static
 * {@code CakeBlock#eat(LevelAccessor, BlockPos, BlockState, Player)} via reflection against the
 * REAL, persistent overworld {@code ServerLevel}, at {@code cake.blockPosition()} — a
 * {@code TotalityFakePlayer}'s position, which is never explicitly set and therefore stays at
 * vanilla's own default entity position, exactly {@code BlockPos.ZERO} ({@code (0, 0, 0)}, the
 * WORLD ORIGIN — not necessarily the configured world spawn point; {@code Entity}'s constructor
 * calls {@code this.setPos(0.0, 0.0, 0.0)} unconditionally, confirmed against the real decompiled
 * MC 26.2 source). Vanilla's real {@code eat()} unconditionally calls
 * {@code level.setBlock(pos, state.setValue(BITES, bites + 1), 3)} for a fresh cake state — a
 * genuine, permanent world mutation — which is exactly why a real Cake block appeared at the world
 * origin during Block Breaking playtesting. {@code FoodSystemVerification} runs automatically,
 * unconditionally, once per dev-server start ({@code ServerLifecycleEvents.SERVER_STARTED}, gated
 * only by {@code VerificationReporter.isDevEnvironment()} — a pure build/environment flag,
 * independent of any player action), so eating food or running {@code /totality food set} first
 * never actually caused this bug — the two were only coincidentally close in time.
 *
 * <p><b>Review-correction pass:</b> the original fix's automatic "self-heal" (deleting any Cake
 * found at {@code BlockPos.ZERO} on every dev-server start) was itself unsafe — there is no
 * persistent migration marker, so it was never truly "one-time," and it would have deleted a real,
 * legitimate Cake a player later placed at that exact position. It has been removed entirely; a
 * dev-only diagnostic WARNING may remain, but nothing is ever deleted or replaced automatically.
 * The scratch position used to re-invoke {@code CakeBlock#eat} is also now hardened: it (and its 6
 * face-adjacent neighbors) must independently be verified as AIR before the check runs, with a
 * small bounded fallback search and a clear skip/failure if no safe position exists nearby — never
 * merely "restore whatever BlockState was there before," which cannot fully account for a block
 * entity's own state.
 *
 * <p><b>This file cannot construct a real {@code ServerLevel}/{@code ServerPlayer} under plain
 * JUnit</b> (this repository's established, documented limitation — {@code Item}/entity
 * construction requires a bootstrapped server, confirmed by {@code HealingPotionItemContractTest}'s
 * class Javadoc), so every test here is a source-text regression sentinel: it confirms the fixed
 * code shape is present and the buggy shape is gone, never that the fix behaves correctly at
 * runtime. Runtime confirmation is the live {@code FoodSystemVerification} dev-server check itself
 * — see {@code TOTALITY_FOOD_COMMAND_CAKE_BUG_REVIEW_2026-09-22.zip}'s {@code VALIDATION_RESULTS.md}
 * for the actual live run's results.
 */
class FoodCakeVerificationIsolationRegressionTest {

    private static final Path FOOD_SYSTEM_VERIFICATION =
            Path.of("src/main/java/zcylas/totality/api/rpg/resources/verification/FoodSystemVerification.java");
    private static final Path TOTALITY_COMMANDS =
            Path.of("src/main/java/zcylas/totality/init/TotalityCommands.java");
    private static final Path FOOD_VANILLA_COMPATIBILITY_BRIDGE =
            Path.of("src/main/java/zcylas/totality/api/rpg/resources/food/FoodVanillaCompatibilityBridge.java");
    private static final Path FOOD_MIRROR_SERVER_TICK =
            Path.of("src/main/java/zcylas/totality/networking/food/FoodMirrorServerTick.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void cakeCheckNoLongerUsesTheFakePlayersDefaultZeroPositionAsWorldScratchSpace() throws Exception {
        String source = read(FOOD_SYSTEM_VERIFICATION);
        // "cake.blockPosition()" may still appear in an explanatory comment describing the OLD,
        // now-fixed bug (past tense, for future readers) — what must be gone is the actual
        // reflected invoke call using it as the mutation target.
        assertFalse(source.contains("eatMethod.invoke(null, level, cake.blockPosition(), cakeState, cake)"),
                "the Cake check must never invoke CakeBlock#eat at a TotalityFakePlayer's "
                        + "default (never-set) position — that position is exactly BlockPos.ZERO");
        assertTrue(source.contains("eatMethod.invoke(null, level, cakeScratchPos, cakeState, cake)"),
                "the Cake check must invoke CakeBlock#eat at the validated scratch position instead");
        assertTrue(source.contains("new BlockPos(16, level.getMaxY() - 5, 16)"),
                "the Cake check must use a dedicated, elevated preferred scratch position instead, "
                        + "following the same real-level-but-out-of-the-way convention "
                        + "MiningVerification already uses");
    }

    @Test
    void runSelfTestNeverRemovesOrReplacesBlockAtWorldOrigin() throws Exception {
        // Review-correction pass: the original fix's automatic self-heal (deleting a Cake found at
        // BlockPos.ZERO on every server start) was itself unsafe — no persistent migration marker
        // means it was never truly one-time, and it would delete a real, legitimate Cake a player
        // later placed at that exact position. It must be gone entirely.
        String source = read(FOOD_SYSTEM_VERIFICATION);
        assertFalse(source.contains("level.removeBlock(BlockPos.ZERO"),
                "runSelfTest must never automatically remove a block at BlockPos.ZERO");
        assertFalse(source.contains("level.setBlock(BlockPos.ZERO"),
                "runSelfTest must never automatically replace a block at BlockPos.ZERO");
        // A read-only diagnostic warning is explicitly allowed to remain.
        int runSelfTestStart = source.indexOf("private static void runSelfTest(");
        assertTrue(runSelfTestStart >= 0);
        int migrationStart = source.indexOf("MIGRATION ─", runSelfTestStart);
        String diagnosticRegion = source.substring(runSelfTestStart, migrationStart);
        assertTrue(diagnosticRegion.contains("level.getBlockState(BlockPos.ZERO).is(Blocks.CAKE)")
                        && diagnosticRegion.contains("Totality.LOGGER.warn("),
                "a read-only diagnostic warning for a Cake at BlockPos.ZERO may remain, logging only");
    }

    @Test
    void cakeScratchPositionMustBeValidatedAirBeforeAnyMutation() throws Exception {
        String source = read(FOOD_SYSTEM_VERIFICATION);
        assertTrue(source.contains("private static boolean isSafeAirScratch(ServerLevel level, BlockPos pos)"),
                "a dedicated air/neighbor-safety check must exist for the scratch position");
        int safeAirBody = source.indexOf("private static boolean isSafeAirScratch(");
        assertTrue(source.indexOf("level.getBlockState(pos).isAir()", safeAirBody) > safeAirBody,
                "the scratch position itself must be verified air");
        assertTrue(source.indexOf("Direction.values()", safeAirBody) > safeAirBody,
                "the scratch position's face-adjacent neighbors must also be verified air");
        assertTrue(source.contains("private static BlockPos findSafeAirScratchPosition("),
                "a small bounded fallback search for an alternative safe position must exist");
        assertTrue(source.contains("cakeScratchPos == null"),
                "the Cake check must skip/fail clearly when no safe scratch position was found, "
                        + "rather than overwrite real content");
    }

    @Test
    void cakeCheckRestoresAirInFinallyEvenOnFailure() throws Exception {
        String source = read(FOOD_SYSTEM_VERIFICATION);
        int checkStart = source.indexOf("BlockPos preferredCakeScratchPos");
        assertTrue(checkStart >= 0, "the preferred scratch position must be captured as a local variable");
        String cakeCheckRegion = source.substring(checkStart, source.indexOf("VANILLA PATH (Saturation effect)", checkStart));
        assertTrue(cakeCheckRegion.contains("finally {")
                        && cakeCheckRegion.contains("level.setBlock(cakeScratchPos, Blocks.AIR.defaultBlockState(), 3)"),
                "the scratch position must be restored to AIR in a finally block, so no mutation "
                        + "survives regardless of outcome (including an exception from the reflected "
                        + "CakeBlock#eat call)");
    }

    @Test
    void cakeCheckSelfVerifiesWorldOriginWasNeverTouched() throws Exception {
        // The brief's own explicit requirement: a targeted live check capable of detecting any
        // BlockState change at the test position — here, specifically proving BlockPos.ZERO (the
        // world origin, the previously-buggy position) was never written to.
        String source = read(FOOD_SYSTEM_VERIFICATION);
        assertTrue(source.contains("!level.getBlockState(BlockPos.ZERO).is(Blocks.CAKE)"),
                "the fixed check must explicitly assert the world origin (0,0,0) was never touched, "
                        + "not merely stop touching it silently");
        assertTrue(source.contains("scratchRestored = level.getBlockState(cakeScratchPos).isAir()"),
                "the fixed check must explicitly assert the scratch position was restored to air");
        assertFalse(source.toLowerCase(java.util.Locale.ROOT).contains("\"world spawn\""),
                "BlockPos.ZERO must never be called \"world spawn\" in a user-facing check label — "
                        + "it is the world origin, not necessarily the configured spawn point");
    }

    @Test
    void realWorldScratchTestingIsHonestlyDocumentedAsStillDirtyingAChunk() throws Exception {
        // The brief's own explicit requirement: do not claim zero world-side-effects unless the
        // check runs against an isolated/nonpersistent test level (it does not — this codebase has
        // no such facility, and building one is out of scope for this fix).
        String source = read(FOOD_SYSTEM_VERIFICATION);
        assertTrue(source.contains("dirties one real, persistent chunk"),
                "the Cake check's own documentation must honestly disclose that real-world scratch "
                        + "testing still dirties a chunk, even when fully successful and fully restored");
    }

    @Test
    void adminFoodSetCommandNeverReferencesBlockPosItemStackOrCakeBlock() throws Exception {
        // The command contract: authoritative mutation only — no BlockPos, ItemStack, BlockState,
        // or Cake-specific interaction logic anywhere in its implementation.
        String source = read(TOTALITY_COMMANDS);
        // Anchored on the unique "/totality food get|set" section comment first — "set" alone is
        // not a unique literal in this file (the unrelated "/totality wallet set" command also
        // uses it), so searching for it unanchored would silently match the wrong subcommand.
        int foodSectionStart = source.indexOf("/totality food get|set");
        assertTrue(foodSectionStart >= 0, "expected to find the /totality food get|set section");
        int setStart = source.indexOf(".then(Commands.literal(\"set\")", foodSectionStart);
        assertTrue(setStart >= 0, "expected to find the /totality food set subcommand");
        int setEnd = source.indexOf(".then(Commands.literal(\"debug\")", setStart);
        assertTrue(setEnd > setStart, "expected the food debug subcommand to follow food set, bounding the search region");
        String setBlock = source.substring(setStart, setEnd);
        assertFalse(setBlock.contains("BlockPos"), "/totality food set must never reference BlockPos");
        assertFalse(setBlock.contains("ItemStack"), "/totality food set must never reference ItemStack");
        assertFalse(setBlock.contains("CakeBlock") || setBlock.contains("Blocks.CAKE"),
                "/totality food set must never reference Cake-specific interaction logic");
        assertTrue(setBlock.contains("PlayerResourceService.INSTANCE.set("),
                "/totality food set must go through the ordinary authoritative resource-set path");
        assertTrue(setBlock.contains("CauseTypes.ADMIN_COMMAND"),
                "/totality food set must tag its mutation with the ADMIN_COMMAND cause");
    }

    @Test
    void mirrorResyncIsProvablyOneDirectionalAndCanNeverFeedBack() throws Exception {
        // The reverse (authoritative -> vanilla mirror) update must never be interpreted as a new
        // real food-consumption event, and must never re-enter the authoritative mutation.
        String source = read(FOOD_VANILLA_COMPATIBILITY_BRIDGE);
        int methodStart = source.indexOf("public static void resyncMirrorIfStale(");
        assertTrue(methodStart >= 0);
        String methodBody = source.substring(methodStart, source.indexOf("\n    }\n", methodStart));
        assertFalse(methodBody.contains("PlayerResourceService.INSTANCE.set(")
                        || methodBody.contains("PlayerResourceService.INSTANCE.restore(")
                        || methodBody.contains("PlayerResourceService.INSTANCE.drain("),
                "resyncMirrorIfStale must never call back into the authoritative resource service — "
                        + "it may only query it and write to vanilla's own FoodData field");
        assertTrue(methodBody.contains("player.getFoodData().setFoodLevel(expectedMirror)"),
                "resyncMirrorIfStale must only ever write vanilla's own foodLevel field, never the authoritative value");

        String tickSource = read(FOOD_MIRROR_SERVER_TICK);
        assertTrue(tickSource.contains("server.getPlayerList().getPlayers()"),
                "the per-tick reconciler must only ever iterate real, connected players — never a "
                        + "test/verification fake player, which is never registered in the real PlayerList");
    }

    @Test
    void interceptFoodDataEatNeverInvokesCakeOrBlockLogicItself() throws Exception {
        // The shared bridge function every real vanilla-eat mixin (ordinary food, Cake, Saturation
        // effect) delegates to must stay generic — no Cake/Block-specific branching of its own.
        String source = read(FOOD_VANILLA_COMPATIBILITY_BRIDGE);
        int methodStart = source.indexOf("public static void interceptFoodDataEat(");
        assertTrue(methodStart >= 0);
        String methodBody = source.substring(methodStart, source.indexOf("\n    }\n", methodStart));
        assertFalse(methodBody.contains("BlockPos") || methodBody.contains("CakeBlock") || methodBody.contains("Blocks."),
                "interceptFoodDataEat must stay a generic FoodData-eat translation point, with no "
                        + "Cake/Block-specific logic of its own — Cake's own real-interaction context "
                        + "is supplied entirely by its caller, CakeBlockEatAuthorityMixin");
    }
}
