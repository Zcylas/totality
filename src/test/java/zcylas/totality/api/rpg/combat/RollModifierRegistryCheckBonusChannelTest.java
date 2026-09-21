package zcylas.totality.api.rpg.combat;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import zcylas.totality.api.dice.DiceBonus;
import zcylas.totality.api.dice.RollType;
import zcylas.totality.api.rpg.check.AbilityCheckResolver;
import zcylas.totality.api.rpg.stats.AbilityScore;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real, executing behavioral tests for the new {@code getCheckBonusList}/{@code
 * resolveCheckBonusList} Ability-Check bonus channel (2026-09-16, dialogue Ability Check bonus
 * routing fix) — proves attack, save, and check bonuses are three genuinely independent lists, not
 * three names for the same underlying data. Runs against the UUID-keyed test hooks, matching {@code
 * RollModifierRegistryLifecycleTest}'s own established precedent (a real {@code ServerPlayer} is
 * unreachable under plain JUnit).
 *
 * <p>{@code SaveOnlyModifier} below deliberately mirrors the exact shape of the real {@code
 * BlessEffect}'s registered modifier (overrides {@code getAttackBonusList}/{@code
 * getSaveBonusList} only) — proving that shape correctly contributes nothing to an Ability Check,
 * which is the actual reported bug (Bless's +1d4 leaking into a Banker Persuasion check). {@code
 * BlessEffect} itself not overriding {@code getCheckBonusList} is separately confirmed by a
 * source-regression sentinel in {@code BlessLifecycleSourceRegressionTest}, since exercising the
 * real anonymous modifier requires a live {@code ServerPlayer}.
 */
class RollModifierRegistryCheckBonusChannelTest {

    private static final Identifier SAVE_ONLY_ID = Identifier.fromNamespaceAndPath("totality", "save_only_test");
    private static final Identifier CHECK_ONLY_ID = Identifier.fromNamespaceAndPath("totality", "check_only_test");
    private static final Identifier NO_OVERRIDE_ID = Identifier.fromNamespaceAndPath("totality", "no_override_test");

    @AfterEach
    void clear() {
        RollModifierRegistry.clearForTest();
    }

    /** Mirrors BlessEffect's real registered modifier shape exactly: attack + save only, per D&D
     *  5e Bless semantics (never Ability Checks). */
    private static final class SaveOnlyModifier implements RollModifierRegistry.RollModifier {
        @Override public RollType modifySave(AbilityScore score, RollType current) { return current; }
        @Override public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current) { return current; }
        @Override public List<DiceBonus> getAttackBonusList(AbilityScore score) { return List.of(new DiceBonus("Bless", 4)); }
        @Override public List<DiceBonus> getSaveBonusList(AbilityScore score) { return List.of(new DiceBonus("Bless", 4)); }
    }

    /** A future Guidance-shaped modifier: Ability Check bonus only, never attack or save. */
    private static final class CheckOnlyModifier implements RollModifierRegistry.RollModifier {
        @Override public RollType modifySave(AbilityScore score, RollType current) { return current; }
        @Override public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current) { return current; }
        @Override public List<DiceBonus> getCheckBonusList(AbilityScore score) { return List.of(new DiceBonus("Guidance", 3)); }
    }

    /** Overrides nothing beyond the required abstract methods — proves every bonus list defaults
     *  to empty rather than throwing or fabricating a value. */
    private static final class NoOverrideModifier implements RollModifierRegistry.RollModifier {
        @Override public RollType modifySave(AbilityScore score, RollType current) { return current; }
        @Override public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current) { return current; }
    }

    @Test
    void aSaveOnlyModifierLikeBlessDoesNotAppearOnAnAbilityCheck() {
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, SAVE_ONLY_ID, new SaveOnlyModifier());

        List<DiceBonus> checkBonuses = RollModifierRegistry.resolveCheckBonusListForTest(player, AbilityScore.CHA);

        assertTrue(checkBonuses.isEmpty(),
                "a Bless-shaped (attack+save only) modifier must not appear on an Ability Check");
    }

    @Test
    void aSaveOnlyModifierLikeBlessStillAppearsOnSavingThrowsAndAttackRolls() {
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, SAVE_ONLY_ID, new SaveOnlyModifier());

        assertEquals(List.of(new DiceBonus("Bless", 4)),
                RollModifierRegistry.resolveSaveBonusListForTest(player, AbilityScore.CHA));
        assertEquals(List.of(new DiceBonus("Bless", 4)),
                RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR));
    }

    @Test
    void aCheckOnlyModifierAppearsOnAnAbilityCheck() {
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, CHECK_ONLY_ID, new CheckOnlyModifier());

        List<DiceBonus> checkBonuses = RollModifierRegistry.resolveCheckBonusListForTest(player, AbilityScore.CHA);

        assertEquals(List.of(new DiceBonus("Guidance", 3)), checkBonuses);
    }

    @Test
    void aCheckOnlyModifierDoesNotAppearOnSavingThrowsOrAttackRolls() {
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, CHECK_ONLY_ID, new CheckOnlyModifier());

        assertTrue(RollModifierRegistry.resolveSaveBonusListForTest(player, AbilityScore.CHA).isEmpty());
        assertTrue(RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR).isEmpty());
    }

    @Test
    void aSaveOnlyAndACheckOnlyModifierTogetherEachAppearOnlyOnTheirOwnChannel() {
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, SAVE_ONLY_ID, new SaveOnlyModifier());
        RollModifierRegistry.registerForTest(player, CHECK_ONLY_ID, new CheckOnlyModifier());

        assertEquals(List.of(new DiceBonus("Guidance", 3)),
                RollModifierRegistry.resolveCheckBonusListForTest(player, AbilityScore.CHA),
                "the Ability Check channel must contain only the check-only modifier's bonus");
        assertEquals(List.of(new DiceBonus("Bless", 4)),
                RollModifierRegistry.resolveSaveBonusListForTest(player, AbilityScore.CHA),
                "the Saving Throw channel must contain only the save-only modifier's bonus");
    }

    @Test
    void aModifierWithNoOverridesContributesNothingToAnyBonusChannel() {
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, NO_OVERRIDE_ID, new NoOverrideModifier());

        assertTrue(RollModifierRegistry.resolveCheckBonusListForTest(player, AbilityScore.CHA).isEmpty());
        assertTrue(RollModifierRegistry.resolveSaveBonusListForTest(player, AbilityScore.CHA).isEmpty());
        assertTrue(RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR).isEmpty());
    }

    @Test
    void modifyCheckRollModeResolutionStillWorksAfterAddingTheCheckBonusChannel() {
        // Requirement 6: existing advantage/disadvantage (RollMode) behavior, resolved via
        // modifyCheck, is a completely separate mechanism from the numeric bonus lists above and
        // must be unaffected by adding getCheckBonusList.
        UUID player = UUID.randomUUID();
        RollModifierRegistry.RollModifier grantsAdvantage = new RollModifierRegistry.RollModifier() {
            @Override public RollType modifySave(AbilityScore score, RollType current) { return current; }
            @Override public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current) {
                return AbilityCheckResolver.RollMode.ADVANTAGE;
            }
        };
        RollModifierRegistry.registerForTest(player, CHECK_ONLY_ID, grantsAdvantage);

        AbilityCheckResolver.RollMode resolved = RollModifierRegistry.resolveCheckModeForTest(
                player, AbilityScore.CHA, AbilityCheckResolver.RollMode.NORMAL);

        assertEquals(AbilityCheckResolver.RollMode.ADVANTAGE, resolved);
    }
}
