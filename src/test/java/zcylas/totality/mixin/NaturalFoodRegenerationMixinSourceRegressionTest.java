package zcylas.totality.mixin;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels for the vanilla Food/Peaceful automatic Health regeneration
 * correction. These are sentinels, not runtime proof: confirming the two new mixins actually
 * apply and suppress the correct bytecode call sites requires a live, Mixin-transformed
 * Minecraft/Fabric runtime, which this project's plain-JUnit test suite cannot construct (see
 * {@code HealingPotionItemContractTest}'s class Javadoc for the same constraint documented
 * elsewhere in this codebase). Runtime confirmation is the dedicated-server check and manual
 * validation recorded in the implementation report instead. What these tests DO prove: the exact
 * mixin targets/annotations/registration this correction depends on are present, unchanged since
 * authoring, and that no unrelated Health/Food/healing call site was touched.
 */
class NaturalFoodRegenerationMixinSourceRegressionTest {

    private static final Path FOOD_DATA_MIXIN = Path.of(
            "src/main/java/zcylas/totality/mixin/FoodDataNaturalRegenerationMixin.java");
    private static final Path SERVER_PLAYER_MIXIN = Path.of(
            "src/main/java/zcylas/totality/mixin/ServerPlayerPeacefulRegenerationMixin.java");
    private static final Path MIXIN_CONFIG = Path.of("src/main/resources/totality.mixins.json");
    private static final Path SRC_MAIN_JAVA = Path.of("src/main/java/zcylas/totality");

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    // ── FoodDataNaturalRegenerationMixin ──────────────────────────────────────────────────────

    @Test
    void foodDataMixinTargetsFoodDataClass() throws IOException {
        String source = read(FOOD_DATA_MIXIN);
        assertTrue(source.contains("@Mixin(FoodData.class)"));
        assertTrue(source.contains("import net.minecraft.world.food.FoodData;"));
    }

    @Test
    void foodDataMixinModifiesTheTickMethodsNaturalRegenLocal() throws IOException {
        String source = read(FOOD_DATA_MIXIN);
        assertTrue(source.contains("@ModifyVariable(method = \"tick\", at = @At(\"STORE\"), ordinal = 0)"),
                "expected the exact ModifyVariable targeting FoodData#tick's sole boolean local");
        assertTrue(source.contains("private boolean totality$suppressVanillaFoodDrivenHealthRegeneration(boolean naturalRegen)"));
        assertTrue(source.contains("return false;"));
    }

    @Test
    void foodDataMixinDoesNotWriteTheRealGamerule() throws IOException {
        // Code lines only: the class Javadoc legitimately explains the real gamerule/GameRules
        // relationship in prose — that is documentation, not a write.
        String codeOnly = codeLinesOnly(read(FOOD_DATA_MIXIN));
        assertFalse(codeOnly.contains("GameRules"), "must never reference/write the real GameRules object in code");
        assertFalse(codeOnly.contains(".set("), "must never call a setter — only a local-variable value substitution");
    }

    // ── ServerPlayerPeacefulRegenerationMixin ─────────────────────────────────────────────────

    @Test
    void serverPlayerMixinTargetsServerPlayerClass() throws IOException {
        String source = read(SERVER_PLAYER_MIXIN);
        assertTrue(source.contains("@Mixin(ServerPlayer.class)"));
        assertTrue(source.contains("import net.minecraft.server.level.ServerPlayer;"));
    }

    @Test
    void serverPlayerMixinRedirectsOnlyTheHealCallInsideTickRegeneration() throws IOException {
        String source = read(SERVER_PLAYER_MIXIN);
        assertTrue(source.contains("@Redirect(method = \"tickRegeneration\", at = @At(value = \"INVOKE\","));
        assertTrue(source.contains("target = \"Lnet/minecraft/server/level/ServerPlayer;heal(F)V\""),
                "expected the exact INVOKE target descriptor for the Peaceful auto-heal call site");
    }

    @Test
    void serverPlayerMixinRedirectBodyDoesNotCallHeal() throws IOException {
        // The redirect method must be a no-op with respect to healing: it must not itself call
        // player.heal(...) (which would defeat the suppression) or any other healing method.
        // Code lines only: the method's own explanatory comment legitimately mentions the call
        // it is deliberately NOT making.
        String source = read(SERVER_PLAYER_MIXIN);
        String bodyOnly = codeLinesOnly(source.substring(source.indexOf("private void totality$suppressPeacefulAutomaticHeal")));
        assertFalse(bodyOnly.contains("player.heal("), "the redirect body must not call heal() itself");
    }

    @Test
    void serverPlayerMixinDoesNotTargetFoodOrSaturationMethods() throws IOException {
        // Confirms the redirect is scoped to heal(F)V only — Peaceful's Food/saturation
        // restoration statements in the same method are untouched by this mixin.
        String source = read(SERVER_PLAYER_MIXIN);
        assertFalse(source.contains("setSaturation"));
        assertFalse(source.contains("setFoodLevel"));
    }

    // ── Mixin registration ────────────────────────────────────────────────────────────────────

    @Test
    void bothNewMixinsAreRegisteredInTheServerAuthoritativeSection() throws IOException {
        String config = read(MIXIN_CONFIG);
        int mixinsArrayStart = config.indexOf("\"mixins\"");
        int clientArrayStart = config.indexOf("\"client\"");
        assertTrue(mixinsArrayStart >= 0 && clientArrayStart > mixinsArrayStart,
                "expected to find the \"mixins\" array before the \"client\" array");
        String serverAuthoritativeSection = config.substring(mixinsArrayStart, clientArrayStart);
        String clientOnlySection = config.substring(clientArrayStart);

        assertTrue(serverAuthoritativeSection.contains("\"FoodDataNaturalRegenerationMixin\""),
                "FoodDataNaturalRegenerationMixin must be registered in the server-authoritative section");
        assertTrue(serverAuthoritativeSection.contains("\"ServerPlayerPeacefulRegenerationMixin\""),
                "ServerPlayerPeacefulRegenerationMixin must be registered in the server-authoritative section");
        assertFalse(clientOnlySection.contains("\"FoodDataNaturalRegenerationMixin\""),
                "must not be registered in the client-only section");
        assertFalse(clientOnlySection.contains("\"ServerPlayerPeacefulRegenerationMixin\""),
                "must not be registered in the client-only section");
    }

    @Test
    void mixinConfigDefaultRequireRemainsOneSoMappingDriftFailsLoudly() throws IOException {
        String config = read(MIXIN_CONFIG);
        assertTrue(config.contains("\"defaultRequire\": 1"),
                "the project-wide defaultRequire = 1 must remain in place so a missing injection "
                        + "target fails mixin application loudly rather than silently no-op-ing");
    }

    // ── No client-only references ─────────────────────────────────────────────────────────────

    @Test
    void neitherNewMixinReferencesAnyClientOnlyClass() throws IOException {
        for (Path path : List.of(FOOD_DATA_MIXIN, SERVER_PLAYER_MIXIN)) {
            String source = read(path);
            for (String importLine : source.lines().map(String::trim)
                    .filter(l -> l.startsWith("import ")).toList()) {
                String lower = importLine.toLowerCase(Locale.ROOT);
                assertFalse(lower.contains(".client."), path + " must not import a client-only class: " + importLine);
            }
        }
    }

    // ── Scope guard: no unrelated Health/Food/healing call site touched ─────────────────────

    @Test
    void noProductionFileForciblySetsTheNaturalRegenerationGamerule() throws IOException {
        forEachProductionJavaFile(source -> {
            assertFalse(source.contains(".set(GameRules.NATURAL_HEALTH_REGENERATION"),
                    "the real naturalRegeneration gamerule must never be forcibly written");
        });
    }

    /**
     * Renamed from {@code explicitHealingCallSitesRemainPresentAndUntouched} (review-correction
     * pass): the original name claimed the referenced files are "untouched," but the assertions
     * below only check that specific source expressions are textually present in each file — that
     * is a source-presence regression sentinel, not proof of byte-for-byte file identity, and not
     * proof that any of these call sites actually executes or produces a particular healing
     * amount at runtime. (Separately, this task's own {@code git diff} inspection — recorded in
     * the implementation report, not by this test — did directly confirm these four files were
     * not modified by this correction.)
     */
    @Test
    void knownExplicitHealingCallSitesRemainPresent() throws IOException {
        // These four files are the only production call sites of .heal(...)/causeFoodExhaustion
        // outside the two new mixins (confirmed by repo-wide grep during implementation).
        assertTrue(read(Path.of("src/main/java/zcylas/totality/item/potion/dnd/HealingPotionItem.java"))
                .contains("user.heal(amount);"));
        assertTrue(read(Path.of("src/main/java/zcylas/totality/api/combat/condition/ConditionServerTick.java"))
                .contains("entity.heal(0.2f);"));
        String healEffect = read(Path.of("src/main/java/zcylas/totality/item/magic/rune/effect/HealEffect.java"));
        assertTrue(healEffect.contains("player.causeFoodExhaustion(2.5f);"),
                "HealEffect's explicit causeFoodExhaustion(2.5f) call must remain present");
        assertTrue(healEffect.contains("target.heal(amount);"));
        String alchemy = read(Path.of("src/main/java/zcylas/totality/api/rpg/skills/alchemy/AlchemyEffects.java"));
        assertTrue(alchemy.contains("entity.heal(5f);"));
        assertTrue(alchemy.contains("entity.heal(entity.getMaxHealth());"));
    }

    /**
     * Renamed from {@code noProductionFileOutsideTheTwoNewMixinsInterceptsHealOrTickRegeneration}
     * (review-correction pass): the original name claimed to guard against heal interception, but
     * the assertion below only checks for the literal string {@code "tickRegeneration"} — it
     * never inspects {@code heal(} call sites at all. It proves only that no production file
     * outside {@link #SERVER_PLAYER_MIXIN} (the one mixin that legitimately targets that method)
     * references {@code tickRegeneration}. {@link #FOOD_DATA_MIXIN} is also excluded from the
     * scan for symmetry with the two-mixin exclusion list used elsewhere in this file, but its
     * exclusion is vacuous: that file never contains the string {@code "tickRegeneration"} in the
     * first place. This test does not prove that no other mixin redirects {@code heal(...)}, that
     * no other code calls {@code heal(...)}, that no global healing interception exists anywhere,
     * or anything about the Peaceful mixin's actual runtime behavior.
     */
    @Test
    void noProductionFileOutsideTheDedicatedMixinReferencesTickRegeneration() throws IOException {
        // Guards against a future accidental broad heal-cancellation being added elsewhere —
        // exactly the "unacceptable implementation" pattern this task explicitly forbids.
        forEachProductionJavaFile((path, source) -> {
            if (path.equals(FOOD_DATA_MIXIN) || path.equals(SERVER_PLAYER_MIXIN)) return;
            assertFalse(source.contains("tickRegeneration"),
                    path + " must not reference tickRegeneration outside the dedicated mixin");
        });
    }

    @Test
    void resourceAndHealthAdapterFilesAreUntouchedByThisCorrection() throws IOException {
        // HealthResourceAdapter/FoodResourceAdapter/ProductionResourceDefinitions must not
        // reference either new mixin or the suppressed vanilla methods — this correction is
        // purely a mixin-level interception, not a Resource API change.
        for (String rel : List.of(
                "src/main/java/zcylas/totality/api/rpg/resources/external/HealthResourceAdapter.java",
                "src/main/java/zcylas/totality/api/rpg/resources/external/FoodResourceAdapter.java",
                "src/main/java/zcylas/totality/api/rpg/resources/ProductionResourceDefinitions.java")) {
            String source = read(Path.of(rel));
            assertFalse(source.contains("FoodDataNaturalRegenerationMixin"));
            assertFalse(source.contains("ServerPlayerPeacefulRegenerationMixin"));
            assertFalse(source.contains("tickRegeneration"));
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────────

    private interface SourceAssertion {
        void check(String source);
    }

    private interface PathSourceAssertion {
        void check(Path path, String source);
    }

    private static void forEachProductionJavaFile(SourceAssertion assertion) throws IOException {
        forEachProductionJavaFile((path, source) -> assertion.check(source));
    }

    private static void forEachProductionJavaFile(PathSourceAssertion assertion) throws IOException {
        try (Stream<Path> stream = Files.walk(SRC_MAIN_JAVA)) {
            List<Path> javaFiles = stream.filter(p -> p.toString().endsWith(".java")).toList();
            assertFalse(javaFiles.isEmpty(), "expected to find production Java sources to scan");
            for (Path path : javaFiles) {
                assertion.check(path, Files.readString(path));
            }
        }
    }

    /** Strips {@code //}/{@code *}-prefixed comment lines so a scan only sees executable code. */
    private static String codeLinesOnly(String source) {
        StringBuilder sb = new StringBuilder();
        boolean inBlockComment = false;
        for (String line : source.lines().toList()) {
            String trimmed = line.trim();
            if (inBlockComment) {
                if (trimmed.contains("*/")) inBlockComment = false;
                continue;
            }
            if (trimmed.startsWith("/*")) {
                if (!trimmed.contains("*/")) inBlockComment = true;
                continue;
            }
            if (trimmed.startsWith("*") || trimmed.startsWith("//")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
