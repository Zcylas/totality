package zcylas.totality.effect;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Source-regression sentinels confirming {@code BlessEffect} and {@code RageEffect} wire their
 * registered {@code RollModifier}'s {@code isActive()} to the entity's real current status-effect
 * state — the Part E fix for the reported "Bless still applies after visible expiration" bug.
 *
 * <p>Every test here is a sentinel, not runtime proof: exercising the real anonymous
 * {@code RollModifier} registered by {@code BlessEffect.onEffectAdded(LivingEntity, int)} requires
 * a live {@code ServerPlayer}, which — like every other {@code Entity} subtype touched by this
 * project's test suite — needs a bootstrapped, unfrozen Minecraft item/entity registry, unreachable
 * under plain JUnit (see {@code HealingPotionItemContractTest}'s class Javadoc for the same
 * constraint documented elsewhere in this codebase). The actual eviction/liveness *behavior* these
 * sentinels' wiring depends on is covered by real, executing tests in
 * {@code RollModifierRegistryLifecycleTest} instead. Runtime confirmation that Bless itself stops
 * applying after expiration is the manual validation recorded in the correction report.
 */
class BlessLifecycleSourceRegressionTest {

    private static final Path BLESS_EFFECT = Path.of("src/main/java/zcylas/totality/effect/BlessEffect.java");
    private static final Path RAGE_EFFECT = Path.of("src/main/java/zcylas/totality/effect/RageEffect.java");

    private static String read(Path path) throws Exception {
        assertTrue(Files.exists(path), "expected to find source file at " + path);
        return Files.readString(path);
    }

    @Test
    void blessEffectOverridesIsActiveAndChecksItsOwnEffect() throws Exception {
        String source = read(BLESS_EFFECT);
        assertTrue(source.contains("public boolean isActive()"),
                "expected BlessEffect's registered RollModifier to override isActive()");
        assertTrue(source.contains("sp.hasEffect(zcylas.totality.init.ModEffects.BLESS)"),
                "expected isActive() to check the entity's real current Bless effect state");
    }

    @Test
    void rageEffectOverridesIsActiveAndChecksItsOwnEffect() throws Exception {
        String source = read(RAGE_EFFECT);
        assertTrue(source.contains("public boolean isActive()"),
                "expected RageEffect's registered RollModifier to override isActive()");
        assertTrue(source.contains("sp.hasEffect(zcylas.totality.init.ModEffects.RAGE)"),
                "expected isActive() to check the entity's real current Rage effect state");
    }

    @Test
    void blessEffectRegistrationAndRemovalPathsAreUnchanged() throws Exception {
        // Confirms the fix is additive (a new isActive() liveness check), not a replacement of the
        // existing explicit-removal path — both must keep working together.
        String source = read(BLESS_EFFECT);
        assertTrue(source.contains("RollModifierRegistry.register(sp, BlessSpell.ID"),
                "expected the existing registration call to remain");
        assertTrue(source.contains("RollModifierRegistry.remove(sp, BlessSpell.ID)"),
                "expected the existing explicit-removal call in onEffectRemoved to remain");
    }

    @Test
    void neitherFileGainedAnAlchemyOrCombatTextDependency() throws Exception {
        for (Path path : java.util.List.of(BLESS_EFFECT, RAGE_EFFECT)) {
            String source = read(path);
            for (String importLine : source.lines().map(String::trim).filter(l -> l.startsWith("import ")).toList()) {
                String lower = importLine.toLowerCase(java.util.Locale.ROOT);
                assertFalse(lower.contains("alchemy"), path + " must not import Alchemy: " + importLine);
                assertFalse(lower.contains("combattext"), path + " must not import combat-text classes: " + importLine);
            }
        }
    }
}
