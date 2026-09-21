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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real, executing lifecycle tests for {@link RollModifierRegistry}'s Part E fix: a modifier's
 * {@code isActive()} is consulted on every resolve call, and an inactive modifier is excluded from
 * the result and evicted from the registry — the general correction for the reported Bless bug
 * (an attack-roll bonus that outlived its status effect until an unrelated disconnect wiped it).
 *
 * <p>These run against the UUID-keyed test hooks ({@code registerForTest}/etc.), not a real
 * {@code ServerPlayer} — {@code RollModifierRegistry}'s production API only ever needs a player's
 * UUID internally (confirmed by this task's refactor: the {@code ServerPlayer}-taking methods are
 * now thin delegates to UUID-keyed ones), and constructing a real {@code ServerPlayer} requires a
 * bootstrapped, unfrozen Minecraft registry — unreachable under plain JUnit (see
 * {@code HealingPotionItemContractTest}'s class Javadoc for the same constraint documented
 * elsewhere in this codebase). This is genuine behavioral proof of the registry's own logic; it is
 * not a sentinel. What it cannot prove is that {@code BlessEffect}'s real anonymous
 * {@code RollModifier} correctly wires {@code isActive()} to {@code sp.hasEffect(ModEffects.BLESS)}
 * at runtime — that wiring is covered by a source-regression sentinel in
 * {@code BlessLifecycleSourceRegressionTest} instead, and ultimately by manual validation.
 */
class RollModifierRegistryLifecycleTest {

    private static final Identifier BLESS_ID = Identifier.fromNamespaceAndPath("totality", "bless");
    private static final Identifier RAGE_ID = Identifier.fromNamespaceAndPath("totality", "rage_test");

    @AfterEach
    void clear() {
        RollModifierRegistry.clearForTest();
    }

    /** A minimal fake modifier with a mutable, test-controlled liveness flag and a roll counter. */
    private static final class FakeModifier implements RollModifierRegistry.RollModifier {
        boolean active = true;
        final AtomicInteger attackRollCount = new AtomicInteger();
        final String label;
        final int value;

        FakeModifier(String label, int value) {
            this.label = label;
            this.value = value;
        }

        @Override
        public RollType modifySave(AbilityScore score, RollType current) { return current; }

        @Override
        public AbilityCheckResolver.RollMode modifyCheck(AbilityScore score, AbilityCheckResolver.RollMode current) {
            return current;
        }

        @Override
        public List<DiceBonus> getAttackBonusList(AbilityScore score) {
            attackRollCount.incrementAndGet();
            return List.of(new DiceBonus(label, value));
        }

        @Override
        public boolean isActive() { return active; }
    }

    @Test
    void modifierContributesWhileActive() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);

        List<DiceBonus> result = RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);

        assertEquals(List.of(new DiceBonus("Bless", 3)), result);
    }

    @Test
    void modifierStopsContributingImmediatelyAfterGoingInactive() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);
        assertFalse(RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR).isEmpty());

        bless.active = false; // simulates the underlying status effect's natural expiration
        List<DiceBonus> afterExpiry = RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);

        assertTrue(afterExpiry.isEmpty(), "expected no contribution once isActive() reports false");
    }

    @Test
    void expiredModifierDiceAreNotRolledAfterExpiration() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);
        RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);
        int rollsWhileActive = bless.attackRollCount.get();
        assertEquals(1, rollsWhileActive);

        bless.active = false;
        RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);
        RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);

        assertEquals(rollsWhileActive, bless.attackRollCount.get(),
                "getAttackBonusList (which rolls dice) must not be called again once inactive");
    }

    @Test
    void inactiveModifierIsEvictedFromTheRegistryNotJustSkipped() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);
        assertEquals(1, RollModifierRegistry.registeredCountForTest(player));

        bless.active = false;
        RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR); // triggers the sweep

        assertEquals(0, RollModifierRegistry.registeredCountForTest(player),
                "an inactive modifier must be evicted, not merely skipped forever");
    }

    @Test
    void worldReloadIsNotRequiredToClearAnExpiredModifier() {
        // The reported bug's workaround was leaving and re-entering the world, which only "fixed"
        // it because disconnect calls clearPlayer (a full wipe). This proves eviction happens via
        // ordinary resolution alone — clearForTest()/clearPlayer is never called in this test.
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);

        bless.active = false;
        List<DiceBonus> result = RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);

        assertTrue(result.isEmpty());
        assertEquals(0, RollModifierRegistry.registeredCountForTest(player));
    }

    @Test
    void explicitRemovalClearsTheModifier() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);

        RollModifierRegistry.removeForTest(player, BLESS_ID);

        assertEquals(0, RollModifierRegistry.registeredCountForTest(player));
        assertTrue(RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR).isEmpty());
    }

    @Test
    void reapplicationRestoresExactlyOneBonusEntry() {
        UUID player = UUID.randomUUID();
        FakeModifier first = new FakeModifier("Bless", 3);
        RollModifierRegistry.registerForTest(player, BLESS_ID, first);
        first.active = false;
        RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR); // evicts "first"

        FakeModifier reapplied = new FakeModifier("Bless", 4);
        RollModifierRegistry.registerForTest(player, BLESS_ID, reapplied);

        assertEquals(1, RollModifierRegistry.registeredCountForTest(player));
        assertEquals(List.of(new DiceBonus("Bless", 4)),
                RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR));
    }

    @Test
    void repeatedRegistrationUnderTheSameSourceIdDoesNotDuplicateTheBonus() {
        // Simulates re-casting/refreshing Bless on an already-Blessed target before it expires:
        // BlessEffect always registers under the same Identifier (BlessSpell.ID), so a second
        // registration must replace, not stack alongside, the first.
        UUID player = UUID.randomUUID();
        RollModifierRegistry.registerForTest(player, BLESS_ID, new FakeModifier("Bless", 3));
        RollModifierRegistry.registerForTest(player, BLESS_ID, new FakeModifier("Bless", 3));
        RollModifierRegistry.registerForTest(player, BLESS_ID, new FakeModifier("Bless", 3));

        assertEquals(1, RollModifierRegistry.registeredCountForTest(player));
        assertEquals(1, RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR).size());
    }

    @Test
    void otherActiveModifiersStillFunctionWhenOneExpires() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        FakeModifier rage = new FakeModifier("Rage", 2);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);
        RollModifierRegistry.registerForTest(player, RAGE_ID, rage);

        bless.active = false;
        List<DiceBonus> result = RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR);

        assertEquals(List.of(new DiceBonus("Rage", 2)), result);
        assertEquals(1, RollModifierRegistry.registeredCountForTest(player), "only the expired modifier should be evicted");
    }

    @Test
    void attackTotalChangesOnlyByTheExpiredContributionsValue() {
        UUID player = UUID.randomUUID();
        FakeModifier bless = new FakeModifier("Bless", 3);
        FakeModifier rage = new FakeModifier("Rage", 2);
        RollModifierRegistry.registerForTest(player, BLESS_ID, bless);
        RollModifierRegistry.registerForTest(player, RAGE_ID, rage);

        int totalBefore = RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR)
                .stream().mapToInt(DiceBonus::value).sum();
        assertEquals(5, totalBefore);

        bless.active = false;
        int totalAfter = RollModifierRegistry.resolveAttackBonusListForTest(player, AbilityScore.STR)
                .stream().mapToInt(DiceBonus::value).sum();

        assertEquals(totalBefore - 3, totalAfter, "total must drop by exactly Bless's contribution");
    }
}
